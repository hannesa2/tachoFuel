package com.example.vespatacho.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Tank capacity in litres — used to estimate range on a full tank. */
    val tankLiters: Double = 5.5,
)
