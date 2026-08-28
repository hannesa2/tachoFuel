package com.example.vespatacho.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fuel_readings")
data class FuelReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val price: Double,
    val liter: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val vehicleId: Long = 1,
)
