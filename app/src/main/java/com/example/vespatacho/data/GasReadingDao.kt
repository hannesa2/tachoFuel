package com.example.vespatacho.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GasReadingDao {
    @Insert
    suspend fun insert(reading: GasReading): Long

    @Update
    suspend fun update(reading: GasReading)

    @Delete
    suspend fun delete(reading: GasReading)

    @Query("SELECT * FROM gas_readings WHERE vehicleId = :vehicleId ORDER BY timestamp DESC")
    fun getAllByVehicle(vehicleId: Long): Flow<List<GasReading>>

    @Query("SELECT * FROM gas_readings WHERE vehicleId = :vehicleId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestByVehicle(vehicleId: Long): GasReading?

    @Query("SELECT * FROM gas_readings WHERE id = :id")
    suspend fun getById(id: Long): GasReading?

    @Query("SELECT id FROM gas_readings")
    suspend fun getAllIds(): List<Long>
}
