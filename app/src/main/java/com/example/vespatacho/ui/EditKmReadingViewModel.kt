package com.example.vespatacho.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.vespatacho.data.DetectionSampleRepository
import com.example.vespatacho.data.GasReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditGasReadingViewModel(
    app: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(app) {

    private val repo = (app as com.example.vespatacho.VespaTachoApp).repository
    private val sampleDao =
        (app as com.example.vespatacho.VespaTachoApp).database.detectionSampleDao()
    private val readingId: Long = savedStateHandle.get<Long>("readingId") ?: 0L

    private val _reading = MutableStateFlow<GasReading?>(null)
    val reading: StateFlow<GasReading?> = _reading.asStateFlow()

    /** Decoded JPEG from the nearest ODOMETER detection sample, if any. */
    private val _odometerImage = MutableStateFlow<Bitmap?>(null)
    val odometerImage: StateFlow<Bitmap?> = _odometerImage.asStateFlow()

    /** Decoded JPEG from the nearest FUEL detection sample, if any. */
    private val _fuelImage = MutableStateFlow<Bitmap?>(null)
    val fuelImage: StateFlow<Bitmap?> = _fuelImage.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val r = repo.getById(readingId)
            _reading.value = r
            if (r != null) {
                sampleDao.findNearest(r.vehicleId, DetectionSampleRepository.TYPE_ODOMETER, r.timestamp)
                    ?.imageJpeg
                    ?.let { _odometerImage.value = BitmapFactory.decodeByteArray(it, 0, it.size) }
                sampleDao.findNearest(r.vehicleId, DetectionSampleRepository.TYPE_FUEL, r.timestamp)
                    ?.imageJpeg
                    ?.let { _fuelImage.value = BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
        }
    }

    fun save(km: Int?, price: Double?, liter: Double?, timestamp: Long, onDone: () -> Unit) {
        val current = _reading.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repo.update(current.copy(km = km, price = price, liter = liter, timestamp = timestamp))
            onDone()
        }
    }
}

typealias EditKmReadingViewModel = EditGasReadingViewModel
