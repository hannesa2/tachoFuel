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

    /** Find the sample directly linked to a specific gas reading. */
    @Query("SELECT * FROM detection_samples WHERE readingId = :readingId AND type = :type LIMIT 1")
    suspend fun findByReading(readingId: Long, type: String): DetectionSample?

    @Query("SELECT * FROM detection_samples WHERE readingId = :readingId")
    suspend fun getAllForReading(readingId: Long): List<DetectionSample>

    @Query("DELETE FROM detection_samples WHERE readingId = :readingId")
    suspend fun deleteAllForReading(readingId: Long)

    @Query("UPDATE detection_samples SET readingId = :readingId WHERE id = :sampleId")
    suspend fun linkToReading(sampleId: Long, readingId: Long)

    /** Find the sample closest in time to [timestamp] for the given vehicle and detection type. */
    @Query(
        "SELECT * FROM detection_samples WHERE vehicleId = :vehicleId AND type = :type " +
        "ORDER BY ABS(timestamp - :timestamp) ASC LIMIT 1",
    )
    suspend fun findNearest(vehicleId: Long, type: String, timestamp: Long): DetectionSample?

    @Query("SELECT COUNT(*) FROM detection_samples WHERE timestamp = :timestamp AND type = :type AND vehicleId = :vehicleId")
    suspend fun existsByKey(timestamp: Long, type: String, vehicleId: Long): Int

    @Query("SELECT COUNT(*) FROM detection_samples")
    suspend fun count(): Long
}
