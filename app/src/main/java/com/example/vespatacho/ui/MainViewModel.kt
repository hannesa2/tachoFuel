package com.example.vespatacho.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vespatacho.camera.OdometerDetector
import com.example.vespatacho.data.AppDatabase
import com.example.vespatacho.data.KmReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executor

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).kmReadingDao()

    val readings = dao.getAll().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    sealed interface CaptureState {
        data object Idle : CaptureState
        data object Processing : CaptureState
        data class Detected(val km: Int, val rawText: String) : CaptureState
        data class Error(val message: String) : CaptureState
    }

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    fun captureAndAnalyse(imageCapture: ImageCapture, executor: Executor) {
        _captureState.value = CaptureState.Processing

        val photoFile = File.createTempFile("tacho_", ".jpg", getApplication<Application>().cacheDir)
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
        val bitmap = withContext(Dispatchers.IO) { loadRotatedBitmap(photoFile) }
        val lastKm = dao.getLatest()?.km
        val result = OdometerDetector.detect(bitmap, lastKm)
        _captureState.value = if (result?.km != null) {
            CaptureState.Detected(result.km, result.rawOcrText)
        } else {
            CaptureState.Error("No odometer reading found.\nOCR text: ${result?.rawOcrText}")
        }
        photoFile.delete()
    }

    fun saveReading(km: Int, rawOcrText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insert(KmReading(km = km, rawOcrText = rawOcrText))
            _captureState.value = CaptureState.Idle
        }
    }

    fun deleteReading(reading: KmReading) {
        viewModelScope.launch(Dispatchers.IO) { dao.delete(reading) }
    }

    fun resetCapture() {
        _captureState.value = CaptureState.Idle
    }

    /** Loads a JPEG and corrects its rotation according to EXIF data. */
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
