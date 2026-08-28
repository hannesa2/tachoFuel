package com.example.vespatacho.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface KmReadingDao {
    @Insert
    suspend fun insert(reading: KmReading): Long

    @Query("SELECT * FROM km_readings ORDER BY timestamp DESC")
    fun getAll(): Flow<List<KmReading>>

    @Query("SELECT * FROM km_readings WHERE vehicleId = :vehicleId ORDER BY timestamp DESC")
    fun getAllByVehicle(vehicleId: Long): Flow<List<KmReading>>

    @Query("SELECT * FROM km_readings ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): KmReading?

    @Query("SELECT * FROM km_readings WHERE vehicleId = :vehicleId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestByVehicle(vehicleId: Long): KmReading?

    @Update
    suspend fun update(reading: KmReading)

    @Query("SELECT * FROM km_readings WHERE id = :id")
    suspend fun getById(id: Long): KmReading?

    @Delete
    suspend fun delete(reading: KmReading)
}
