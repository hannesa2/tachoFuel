package com.example.vespatacho.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One captured image + detection result, stored for future ML training.
 *
 * Images are stored as mid-resolution JPEG (longest side ≤ 800px, 70% quality)
 * to keep the database size manageable while retaining enough detail for training.
 */
@Entity(tableName = "detection_samples")
data class DetectionSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "ODOMETER" or "FUEL" */
    val type: String,
    /** Mid-resolution JPEG bytes */
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val imageJpeg: ByteArray,
    /** Full raw OCR text from ML Kit */
    val rawOcrText: String,
    /** Detected km value (odometer samples) */
    val detectedKm: Int? = null,
    /** Detected price in € (fuel samples) */
    val detectedPrice: String? = null,
    /** Detected liters (fuel samples) */
    val detectedLiter: String? = null,
    /** Firebase Storage download URL — filled after upload */
    val storageUrl: String? = null,
    val vehicleId: Long = 1,
    val timestamp: Long = System.currentTimeMillis(),
) {
    // ByteArray requires manual equals/hashCode to avoid referential comparison
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DetectionSample) return false
        return id == other.id && type == other.type && timestamp == other.timestamp
    }

    override fun hashCode(): Int = 31 * id.hashCode() + type.hashCode()
}
