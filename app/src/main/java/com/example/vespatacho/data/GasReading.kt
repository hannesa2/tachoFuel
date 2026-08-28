package com.example.vespatacho.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gas_readings")
data class GasReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long = 1,
    /** Odometer reading in km — null if not yet captured for this visit. */
    val km: Int? = null,
    /** Total fuel price paid — null if not yet captured. */
    val price: Double? = null,
    /** Litres filled — null if not yet captured. */
    val liter: Double? = null,
    /** Raw OCR text from odometer scan. */
    val rawOcrText: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
