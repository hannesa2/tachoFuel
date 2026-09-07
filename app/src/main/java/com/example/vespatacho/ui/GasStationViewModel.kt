package com.example.vespatacho.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.vespatacho.camera.FuelDetector
import com.example.vespatacho.data.DetectionSampleRepository
import com.example.vespatacho.data.GasReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executor

class GasStationViewModel(app: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(app) {

    private val repo = (app as com.example.vespatacho.VespaTachoApp).repository
    private val sampleRepo = (app as com.example.vespatacho.VespaTachoApp).detectionSampleRepository
    private val vehicleId: Long = savedStateHandle.get<Long>("vehicleId") ?: 1L

    val readings = repo.getAllByVehicle(vehicleId).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    sealed interface CaptureState {
        data object Idle : CaptureState
        data object Processing : CaptureState
        data class Ready(val detectedPrice: String, val detectedLiter: String, val rawOcrTextFuel: String) : CaptureState
        data class Error(val message: String) : CaptureState
    }

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    private val _pendingFuelSampleId = MutableStateFlow<Long?>(null)

    // ── Continuous live-preview detection (used by GasStationCameraXActivity) ──────────────
    data class LiveDetection(val price: String, val liter: String, val rawOcrTextFuel: String)

    private val _liveDetection = MutableStateFlow(LiveDetection("", "", ""))
    val liveDetection: StateFlow<LiveDetection> = _liveDetection.asStateFlow()

    private var lastLiveBitmap: Bitmap? = null
    private var liveAnalysisInFlight = false

    /**
     * Immediately updates just the price in [liveDetection], leaving liter/rawOcrTextFuel
     * untouched. Called from [com.example.vespatacho.mlkit.textdetector.TextRecProcessor] as
     * soon as a live OCR line ending in "e"/"E"/"€" (the price panel label) is recognised,
     * independent of and faster than the throttled [analyseLivePreviewFrame].
     */
    fun updateDetectedPrice(price: String) {
        if (price.isBlank()) return
        Timber.d("updateDetectedPrice: $price")
        _liveDetection.value = _liveDetection.value.copy(price = price)
    }

    /**
     * Immediately updates just the liter value in [liveDetection], leaving price/rawOcrTextFuel
     * untouched. Called from [com.example.vespatacho.mlkit.textdetector.TextRecProcessor] as
     * soon as the live OCR stream finds the digits + "Liter" label, independent of and faster
     * than the throttled [analyseLivePreviewFrame].
     */
    fun updateDetectedLiter(liter: String) {
        if (liter.isBlank()) return
        Timber.d("updateDetectedLiter: $liter")
        _liveDetection.value = _liveDetection.value.copy(liter = liter)
    }

    /**
     * Runs [FuelDetector] on a bitmap grabbed from a live CameraX analysis frame and publishes
     * the result via [liveDetection] so the UI can show auto-filled, continuously-updating
     * price/liter fields without requiring an explicit capture button press.
     *
     * Unlike [analyseLiveFrame]/[captureAndAnalyse], this does *not* persist a
     * [com.example.vespatacho.data.DetectionSample] on every call (that would spam the DB and
     * Firebase Storage many times per second); the last analysed bitmap is only saved as a
     * sample once the user confirms with [saveLiveReading].
     */
    fun analyseLivePreviewFrame(bitmap: Bitmap) {
        if (liveAnalysisInFlight) return
        liveAnalysisInFlight = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = FuelDetector.detect(bitmap)
                lastLiveBitmap = bitmap
                // Merge rather than replace: a frame with no plausible match returns "" for
                // that field, which must not erase a value already confirmed by a previous
                // frame or by the live OCR autofill (updateDetectedPrice/updateDetectedLiter).
                val previous = _liveDetection.value
                _liveDetection.value = LiveDetection(
                    price = result.price.ifBlank { previous.price },
                    liter = result.liter.ifBlank { previous.liter },
                    rawOcrTextFuel = result.rawOcrTextFuel.ifBlank { previous.rawOcrTextFuel },
                )
            } catch (e: Exception) {
                Timber.w(e, "Live frame analysis failed")
            } finally {
                liveAnalysisInFlight = false
            }
        }
    }

    /** Persists the user-confirmed price/liter from the live-preview flow. */
    fun saveLiveReading(price: Double, liter: Double, rawOcrTextFuel: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = lastLiveBitmap
            val sampleId = bitmap?.let {
                sampleRepo.saveSample(
                    bitmap = it,
                    type = DetectionSampleRepository.TYPE_FUEL,
                    rawOcrText = rawOcrTextFuel,
                    detectedPrice = price.toString(),
                    detectedLiter = liter.toString(),
                    vehicleId = vehicleId,
                )
            }
            val readingId = persistReading(price, liter, rawOcrTextFuel)
            sampleId?.let { sampleRepo.linkSampleToReading(it, readingId) }
        }
    }

    fun captureAndAnalyse(imageCapture: ImageCapture, executor: Executor) {
        _captureState.value = CaptureState.Processing

        val photoFile = File.createTempFile("fuel_", ".jpg", getApplication<Application>().cacheDir)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    viewModelScope.launch(Dispatchers.IO) {
                        processPhoto(photoFile)
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    _captureState.value = CaptureState.Error("Camera error: ${exc.message}")
                }
            },
        )
    }

    private suspend fun processPhoto(photoFile: File) {
        try {
            val bitmap = withContext(Dispatchers.IO) { loadRotatedBitmap(photoFile) }
            analyseBitmap(bitmap)
        } finally {
            photoFile.delete()
        }
    }

    private suspend fun analyseBitmap(bitmap: Bitmap) {
        try {
            val result = FuelDetector.detect(bitmap)
            val sampleId = sampleRepo.saveSample(
                bitmap = bitmap,
                type = DetectionSampleRepository.TYPE_FUEL,
                rawOcrText = result.rawOcrTextFuel,
                detectedPrice = result.price.ifEmpty { null },
                detectedLiter = result.liter.ifEmpty { null },
                vehicleId = vehicleId,
            )
            _pendingFuelSampleId.value = sampleId
            _captureState.value = CaptureState.Ready(result.price, result.liter, result.rawOcrTextFuel)
        } catch (e: Exception) {
            _captureState.value = CaptureState.Error(e.message ?: "Failed to analyse photo.")
        }
    }

    fun saveReading(price: Double, liter: Double, rawOcrTextFuel: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val readingId = persistReading(price, liter, rawOcrTextFuel)
            _pendingFuelSampleId.value?.let { sampleRepo.linkSampleToReading(it, readingId) }
            _pendingFuelSampleId.value = null
            _captureState.value = CaptureState.Idle
        }
    }

    private suspend fun persistReading(price: Double, liter: Double, rawOcrTextFuel: String): Long {
        val latest = repo.getLatestByVehicle(vehicleId)
        return if (latest != null && latest.price == null && latest.liter == null) {
            repo.update(latest.copy(price = price, liter = liter, rawOcrTextFuel = rawOcrTextFuel))
            latest.id
        } else {
            repo.insert(GasReading(vehicleId = vehicleId, price = price, liter = liter, rawOcrTextFuel = rawOcrTextFuel))
        }
    }

    fun deleteReading(reading: GasReading) {
        viewModelScope.launch(Dispatchers.IO) { repo.delete(reading) }
    }

    fun resetCapture() {
        _captureState.value = CaptureState.Idle
    }

    private fun loadRotatedBitmap(file: File): Bitmap {
        val exif = ExifInterface(file.absolutePath)
        val rotation = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val raw = BitmapFactory.decodeFile(file.absolutePath)
        return if (rotation != 0f) {
            val matrix = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        } else raw
    }
}
