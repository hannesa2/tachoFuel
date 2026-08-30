package com.example.vespatacho.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Handles all Firestore read/write operations.
 *
 * Data lives under: users/{uid}/gasReadings/{vehicleId}_{readingId}
 *                   users/{uid}/vehicles/{vehicleId}
 *
 * On first install (empty Firestore tree) the data from [SEED_UID] is copied
 * into the new user's collection so they start with the existing records.
 * After that the user's own UID is used exclusively.
 */
class FirestoreRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    companion object {
        /** UID whose Firestore data is copied into a new empty installation. */
        const val SEED_UID = "q6a03UpdpuVatxhrYgOe7ehNjuW2"
    }

    /** Returns the current anonymous UID, signing in if needed. */
    private suspend fun uid(): String {
        val current = auth.currentUser
        if (current != null) return current.uid
        auth.signInAnonymously().await()
        return auth.currentUser!!.uid
    }

    private suspend fun readingsCollection() =
        db.collection("users").document(uid()).collection("gasReadings")

    private suspend fun vehiclesCollection() =
        db.collection("users").document(uid()).collection("vehicles")

    // ── GasReading ──────────────────────────────────────────────────────────

    suspend fun upsertReading(reading: GasReading) {
        val docId = "${reading.vehicleId}_${reading.id}"
        readingsCollection().document(docId).set(reading.toFirestoreMap(), SetOptions.merge()).await()
    }

    suspend fun deleteReading(reading: GasReading) {
        val docId = "${reading.vehicleId}_${reading.id}"
        readingsCollection().document(docId).delete().await()
    }

    /** Returns all cloud readings not present locally (by id). */
    suspend fun fetchReadingsMissingLocally(localIds: Set<Long>): List<GasReading> {
        val snapshot = readingsCollection().get().await()
        return snapshot.documents.mapNotNull { doc ->
            doc.toGasReading()?.takeIf { it.id !in localIds }
        }
    }

    // ── Vehicle ─────────────────────────────────────────────────────────────

    suspend fun upsertVehicle(vehicle: Vehicle) {
        vehiclesCollection().document("${vehicle.id}").set(vehicle.toFirestoreMap(), SetOptions.merge()).await()
    }

    suspend fun deleteVehicle(vehicle: Vehicle) {
        vehiclesCollection().document("${vehicle.id}").delete().await()
    }

    suspend fun fetchVehiclesMissingLocally(localIds: Set<Long>): List<Vehicle> {
        val snapshot = vehiclesCollection().get().await()
        return snapshot.documents.mapNotNull { doc ->
            doc.toVehicle()?.takeIf { it.id !in localIds }
        }
    }

    // ── Seed: copy initial data for new empty installations ─────────────────

    /**
     * If the current user has no data in Firestore, copy all readings and
     * vehicles from [SEED_UID] into their collection.
     * This is a one-time migration for fresh installs.
     */
    suspend fun seedFromDefaultIfEmpty() {
        runCatching {
            val uid = uid()
            if (uid == SEED_UID) return  // already the seed user, nothing to copy

            val existingReadings = readingsCollection().get().await()
            if (!existingReadings.isEmpty) {
                Timber.i("Seed: user already has data, skipping")
                return
            }

            Timber.i("Seed: new empty user — copying data from seed UID")
            val seedReadings = db.collection("users").document(SEED_UID)
                .collection("gasReadings").get().await()
            val seedVehicles = db.collection("users").document(SEED_UID)
                .collection("vehicles").get().await()

            val batch = db.batch()
            val userRef = db.collection("users").document(uid)
            // Re-serialize through typed mappers instead of passing doc.data raw —
            // this guarantees only Firestore-safe types (Long not Int, etc.) reach the SDK.
            seedReadings.documents.forEach { doc ->
                val reading = doc.toGasReading() ?: return@forEach
                batch.set(userRef.collection("gasReadings").document(doc.id), reading.toFirestoreMap())
            }
            seedVehicles.documents.forEach { doc ->
                val vehicle = doc.toVehicle() ?: return@forEach
                batch.set(userRef.collection("vehicles").document(doc.id), vehicle.toFirestoreMap())
            }
            batch.commit().await()
            Timber.i("Seed: copied ${seedReadings.size()} readings + ${seedVehicles.size()} vehicles")
        }.onFailure {
            Timber.w(it, "Seed copy failed — continuing without seed data")
        }
    }
}

// ── Mapping helpers ──────────────────────────────────────────────────────────

// Firestore supports: String, Long, Double, Boolean, Map, List, null, Timestamp, Blob.
// Int/Integer is NOT supported — always cast Int to Long before writing.

internal fun GasReading.toFirestoreMap(): Map<String, Any?> = buildMap {
    put("id", id)                           // Long ✓
    put("vehicleId", vehicleId)             // Long ✓
    put("km", km?.toLong())                 // Int → Long
    put("price", price)                     // Double? ✓
    put("liter", liter)                     // Double? ✓
    put("rawOcrTextKm", rawOcrTextKm)       // String? ✓
    put("rawOcrTextFuel", rawOcrTextFuel)   // String? ✓
    put("timestamp", timestamp)             // Long ✓
}

private fun com.google.firebase.firestore.DocumentSnapshot.toGasReading(): GasReading? {
    return try {
        GasReading(
            id = getLong("id") ?: return null,
            vehicleId = getLong("vehicleId") ?: 1L,
            km = getLong("km")?.toInt(),
            price = getDouble("price"),
            liter = getDouble("liter"),
            rawOcrTextKm = getString("rawOcrTextKm"),
            rawOcrTextFuel = getString("rawOcrTextFuel"),
            timestamp = getLong("timestamp") ?: System.currentTimeMillis(),
        )
    } catch (_: Exception) { null }
}

internal fun Vehicle.toFirestoreMap() = mapOf<String, Any>("id" to id, "name" to name)

private fun com.google.firebase.firestore.DocumentSnapshot.toVehicle(): Vehicle? {
    return try {
        Vehicle(
            id = getLong("id") ?: return null,
            name = getString("name") ?: return null,
        )
    } catch (_: Exception) { null }
}
