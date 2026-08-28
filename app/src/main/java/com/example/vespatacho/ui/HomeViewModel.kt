package com.example.vespatacho.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vespatacho.data.AppDatabase
import com.example.vespatacho.data.KmReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("home_prefs", Context.MODE_PRIVATE)
    private val kmDao = AppDatabase.getInstance(app).kmReadingDao()
    private val vehicleDao = AppDatabase.getInstance(app).vehicleDao()

    val vehicles = vehicleDao.getAll().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    private val _selectedVehicleIndex = MutableStateFlow(prefs.getInt("last_vehicle_index", 0))
    val selectedVehicleIndex: StateFlow<Int> = _selectedVehicleIndex.asStateFlow()

    fun selectVehicle(index: Int) {
        _selectedVehicleIndex.value = index
        prefs.edit().putInt("last_vehicle_index", index).apply()
    }

    fun kmReadingsForVehicle(vehicleId: Long): Flow<List<KmReading>> =
        kmDao.getAllByVehicle(vehicleId)

    fun deleteKmReading(reading: KmReading) {
        viewModelScope.launch(Dispatchers.IO) { kmDao.delete(reading) }
    }
}
