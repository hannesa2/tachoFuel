package com.example.vespatacho.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vespatacho.data.AppDatabase
import com.example.vespatacho.data.Vehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VehicleManagementViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.getInstance(app).vehicleDao()
    val vehicles = dao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addVehicle(name: String) {
        viewModelScope.launch(Dispatchers.IO) { dao.insert(Vehicle(name = name)) }
    }

    fun updateVehicle(vehicle: Vehicle) {
        viewModelScope.launch(Dispatchers.IO) { dao.update(vehicle) }
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch(Dispatchers.IO) { dao.delete(vehicle) }
    }
}
