package com.example.vespatacho.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.vespatacho.data.AppDatabase
import com.example.vespatacho.data.KmReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditKmReadingViewModel(
    app: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).kmReadingDao()
    private val readingId: Long = savedStateHandle.get<Long>("readingId") ?: 0L

    private val _reading = MutableStateFlow<KmReading?>(null)
    val reading: StateFlow<KmReading?> = _reading.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _reading.value = dao.getById(readingId)
        }
    }

    fun save(km: Int, timestamp: Long, onDone: () -> Unit) {
        val current = _reading.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            dao.update(current.copy(km = km, timestamp = timestamp))
            onDone()
        }
    }
}
