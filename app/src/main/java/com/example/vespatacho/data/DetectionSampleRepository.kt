package com.example.vespatacho.data

import android.graphics.Bitmap
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.net.URL
import androidx.core.graphics.scale

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

    /** Links a previously saved sample to a specific gas reading. */
    suspend fun linkSampleToReading(sampleId: Long, readingId: Long) {
        dao.linkToReading(sampleId, readingId)
    }

    /**
     * Deletes all detection samples associated with [readingId] from
     * Firebase Storage, Firestore, and the local Room DB.
     */
    suspend fun deleteForReading(readingId: Long) {
        val samples = dao.getAllForReading(readingId)
        if (samples.isEmpty()) return
        val firestoreUid = FirestoreRepository.SHARED_UID
        for (sample in samples) {
            runCatching {
                sample.storageUrl?.let { url ->
                    storage.getReferenceFromUrl(url).delete().await()
                    Timber.d("Storage deleted: ${sample.id}")
                }
            }.onFailure { Timber.w(it, "Storage delete failed for sample ${sample.id}") }

            runCatching {
                firestore.collection("users").document(firestoreUid)
                    .collection("detectionSamples").document("${sample.id}")
                    .delete().await()
                Timber.d("Firestore detectionSample deleted: ${sample.id}")
            }.onFailure { Timber.w(it, "Firestore delete failed for sample ${sample.id}") }
        }
        dao.deleteAllForReading(readingId)
        Timber.d("Deleted ${samples.size} detection samples for readingId=$readingId")
    }

    /** Retries uploading any samples that failed previously (call on app start). */
    suspend fun retryPendingUploads() {
        val totalInDb = dao.count()
        val pending = dao.getPendingUpload()
        Timber.d("retryPendingUploads(): total=${totalInDb} pending=${pending.size}")
        pending.forEach { uploadToFirebase(it) }
    }

    /**
     * Downloads detection samples from Firestore/Storage that are missing from local Room DB.
     * Uses timestamp+type+vehicleId as the stable key to avoid duplicates.
     * Call on app start after auth is ready.
     */
    suspend fun syncFromCloud() {
        runCatching {
            val uid = ensureUid()
            val docs = firestore.collection("users").document(uid)
                .collection("detectionSamples").get().await()
            Timber.d("syncFromCloud: found ${docs.size()} samples in Firestore for uid=$uid")

            var downloaded = 0
            for (doc in docs.documents) {
                val timestamp = doc.getLong("timestamp") ?: continue
                val type = doc.getString("type") ?: continue
                val vehicleId = doc.getLong("vehicleId") ?: 1L
                val storageUrl = doc.getString("storageUrl") ?: continue

                if (dao.existsByKey(timestamp, type, vehicleId) > 0) continue

                val jpeg = runCatching {
                    URL(storageUrl).openStream().use { it.readBytes() }
                }.getOrElse {
                    Timber.w(it, "syncFromCloud: failed to download $storageUrl")
                    return@runCatching
                }

                dao.insert(
                    DetectionSample(
                        type = type,
                        imageJpeg = jpeg,
                        rawOcrText = doc.getString("rawOcrText") ?: "",
                        detectedKm = doc.getLong("detectedKm")?.toInt(),
                        detectedPrice = doc.getString("detectedPrice"),
                        detectedLiter = doc.getString("detectedLiter"),
                        storageUrl = storageUrl,
                        vehicleId = vehicleId,
                        timestamp = timestamp,
                    )
                )
                downloaded++
            }
            Timber.d("syncFromCloud: downloaded $downloaded new samples")
        }.onFailure {
            Timber.w(it, "syncFromCloud failed — continuing without cloud samples")
        }
    }

    private suspend fun uploadToFirebase(sample: DetectionSample) {
        runCatching {
            val authUid = ensureUid()
            val firestoreUid = FirestoreRepository.SHARED_UID
            val path = "detectionSamples/$authUid/${sample.type}/${sample.timestamp}_${sample.id}.jpg"
            Timber.d("DetectionSample uploading: id=${sample.id} authUid=$authUid path=$path size=${sample.imageJpeg.size / 1024}KB")

            // Upload image to Firebase Storage under the real auth UID
            val ref = storage.reference.child(path)
            ref.putBytes(sample.imageJpeg).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Timber.d("DetectionSample Storage OK: $downloadUrl")

            // Save metadata to Firestore under the shared UID
            firestore.collection("users").document(firestoreUid)
                .collection("detectionSamples").document("${sample.id}")
                .set(sample.toMetadataMap(downloadUrl))
                .await()
            Timber.d("DetectionSample Firestore OK: id=${sample.id}")

            // Update Room with the Storage URL
            dao.update(sample.copy(storageUrl = downloadUrl))
            Timber.d("DetectionSample uploaded successfully: id=${sample.id}")
        }.onFailure {
            Timber.e(it, "DetectionSample upload FAILED for id=${sample.id}: ${it.message}")
        }
    }

    private suspend fun ensureUid(): String {
        val current = auth.currentUser
        if (current != null) return current.uid
        auth.signInAnonymously().await()
        return auth.currentUser!!.uid
    }

    /** Scale bitmap down so the longest side ≤ MAX_IMAGE_PX, then JPEG-compress. */
    private fun compressToMidRes(bitmap: Bitmap): ByteArray {
        val scale = MAX_IMAGE_PX.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            bitmap.scale((bitmap.width * scale).toInt(), (bitmap.height * scale).toInt())
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
    "detectedKm" to detectedKm?.toLong(),   // Int → Long (Firestore doesn't support Int)
    "detectedPrice" to detectedPrice,
    "detectedLiter" to detectedLiter,
    "storageUrl" to downloadUrl,
    "vehicleId" to vehicleId,
    "timestamp" to timestamp,
)
