package com.example.vespatacho.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectionSampleDao {
    @Insert
    suspend fun insert(sample: DetectionSample): Long

    @Update
    suspend fun update(sample: DetectionSample)

    @Query("SELECT * FROM detection_samples ORDER BY timestamp DESC")
    fun getAll(): Flow<List<DetectionSample>>

    @Query("SELECT * FROM detection_samples WHERE storageUrl IS NULL ORDER BY timestamp ASC")
    suspend fun getPendingUpload(): List<DetectionSample>

    @Query("SELECT COUNT(*) FROM detection_samples")
    suspend fun count(): Long
}
