package com.example.vespatacho.data

import android.graphics.Bitmap
import com.example.vespatacho.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.ByteArrayOutputStream

/**
 * Saves captured detection images to Room (local) and Firebase (cloud)
 * for future ML training data collection.
 *
 * Images are resized to max 800px on the longest side and compressed
 * as JPEG at 70% quality (~50-100KB per image).
 *
 * Firebase Storage path: detectionSamples/{uid}/{type}/{timestamp}_{localId}.jpg
 * Firestore path:        users/{uid}/detectionSamples/{localId}
 */
class DetectionSampleRepository(
    private val dao: DetectionSampleDao,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    companion object {
        const val TYPE_ODOMETER = "ODOMETER"
        const val TYPE_FUEL = "FUEL"
        private const val MAX_IMAGE_PX = 800
        private const val JPEG_QUALITY = 70
    }

    /**
     * Compresses [bitmap] to mid-resolution, saves to Room, then uploads to Firebase in background.
     * Returns the local Room id immediately — cloud upload happens asynchronously.
     */
    suspend fun saveSample(
        bitmap: Bitmap,
        type: String,
        rawOcrText: String,
        detectedKm: Int? = null,
        detectedPrice: String? = null,
        detectedLiter: String? = null,
        vehicleId: Long = 1,
    ): Long {
        val jpeg = compressToMidRes(bitmap)
        val sample = DetectionSample(
            type = type,
            imageJpeg = jpeg,
            rawOcrText = rawOcrText,
            detectedKm = detectedKm,
            detectedPrice = detectedPrice,
            detectedLiter = detectedLiter,
            vehicleId = vehicleId,
            timestamp = System.currentTimeMillis(),
        )
        val id = dao.insert(sample)
        Timber.d("DetectionSample saved locally: id=$id type=$type size=${jpeg.size / 1024}KB")

        // Fire-and-forget cloud upload
        uploadToFirebase(dao.getPendingUpload().firstOrNull { it.id == id } ?: sample.copy(id = id))

        return id
    }

    /** Retries uploading any samples that failed previously (call on app start). */
    suspend fun retryPendingUploads() {
        val pending = dao.getPendingUpload()
        Timber.d("Retrying ${pending.size} pending detection sample uploads")
        pending.forEach { uploadToFirebase(it) }
    }

    private suspend fun uploadToFirebase(sample: DetectionSample) {
        runCatching {
            val uid = ensureUid() ?: return
            val path = "detectionSamples/$uid/${sample.type}/${sample.timestamp}_${sample.id}.jpg"

            // Upload image to Firebase Storage
            val ref = storage.reference.child(path)
            ref.putBytes(sample.imageJpeg).await()
            val downloadUrl = ref.downloadUrl.await().toString()

            // Save metadata to Firestore
            firestore.collection("users").document(uid)
                .collection("detectionSamples").document("${sample.id}")
                .set(sample.toMetadataMap(downloadUrl))
                .await()

            // Update Room with the Storage URL
            dao.update(sample.copy(storageUrl = downloadUrl))
            Timber.d("DetectionSample uploaded: id=${sample.id} url=$downloadUrl")
        }.onFailure {
            Timber.w(it, "DetectionSample upload failed for id=${sample.id}, will retry next launch")
        }
    }

    private suspend fun ensureUid(): String = "q6a03UpdpuVatxhrYgOe7ehNjuW2"

    /** Scale bitmap down so the longest side ≤ MAX_IMAGE_PX, then JPEG-compress. */
    private fun compressToMidRes(bitmap: Bitmap): ByteArray {
        val scale = MAX_IMAGE_PX.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true,
            )
        } else bitmap

        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }
}

private fun DetectionSample.toMetadataMap(downloadUrl: String) = mapOf(
    "id" to id,
    "type" to type,
    "rawOcrText" to rawOcrText,
    "detectedKm" to detectedKm,
    "detectedPrice" to detectedPrice,
    "detectedLiter" to detectedLiter,
    "storageUrl" to downloadUrl,
    "vehicleId" to vehicleId,
    "timestamp" to timestamp,
)
