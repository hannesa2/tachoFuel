package com.example.vespatacho.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelReadingDao {
    @Insert
    suspend fun insert(reading: FuelReading): Long

    @Query("SELECT * FROM fuel_readings ORDER BY timestamp DESC")
    fun getAll(): Flow<List<FuelReading>>

    @Delete
    suspend fun delete(reading: FuelReading)
}
