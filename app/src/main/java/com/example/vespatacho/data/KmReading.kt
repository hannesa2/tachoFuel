package com.example.vespatacho.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "km_readings")
data class KmReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Odometer value in km as read from the tacho. */
    val km: Int,
    /** Unix epoch millis when the photo was taken. */
    val timestamp: Long = System.currentTimeMillis(),
    /** Raw OCR text returned by ML Kit for debugging. */
    val rawOcrText: String? = null,
    val vehicleId: Long = 1,
)
