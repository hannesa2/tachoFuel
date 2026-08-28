package com.example.vespatacho.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.vespatacho.data.AppDatabase
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

    private val dao = AppDatabase.getInstance(app).gasReadingDao()
    private val readingId: Long = savedStateHandle.get<Long>("readingId") ?: 0L

    private val _reading = MutableStateFlow<GasReading?>(null)
    val reading: StateFlow<GasReading?> = _reading.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _reading.value = dao.getById(readingId)
        }
    }

    fun save(km: Int?, price: Double?, liter: Double?, timestamp: Long, onDone: () -> Unit) {
        val current = _reading.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            dao.update(current.copy(km = km, price = price, liter = liter, timestamp = timestamp))
            onDone()
        }
    }
}

typealias EditKmReadingViewModel = EditGasReadingViewModel
