package com.example.vespatacho.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KmReadingDao {
    @Insert
    suspend fun insert(reading: KmReading): Long

    @Query("SELECT * FROM km_readings ORDER BY timestamp DESC")
    fun getAll(): Flow<List<KmReading>>

    @Query("SELECT * FROM km_readings ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): KmReading?

    @Delete
    suspend fun delete(reading: KmReading)
}
