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
 * All installs access the shared data under [SHARED_UID].
 * Anonymous Firebase Auth is used so requests are authenticated and satisfy
 * Firebase security rules requiring `request.auth != null`.
 */
class FirestoreRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    companion object {
        /** Shared fixed UID so all installs always access the same Firestore data. */
        const val SHARED_UID = "Lv4rXOuuk4XtvmHzv7Ub6DBpXx03"
    }

    /**
     * Ensures an authenticated session exists before making any Firestore calls,
     * so security rules requiring `request.auth != null` succeed.
     */
    suspend fun ensureAuth(): String? {
        val current = auth.currentUser
        if (current != null) return current.uid
        return runCatching {
            auth.signInAnonymously().await()
            Timber.d("FirestoreRepository: anonymous auth signed in as ${auth.currentUser?.uid}")
            auth.currentUser?.uid
        }.onFailure {
            Timber.w(it, "FirestoreRepository: anonymous auth sign-in failed")
        }.getOrNull()
    }

    /** Returns the UID to use for Firestore paths. */
    private suspend fun uid(): String {
        ensureAuth()
        return SHARED_UID
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

internal fun Vehicle.toFirestoreMap() = mapOf<String, Any>("id" to id, "name" to name, "tankLiters" to tankLiters)

private fun com.google.firebase.firestore.DocumentSnapshot.toVehicle(): Vehicle? {
    return try {
        Vehicle(
            id = getLong("id") ?: return null,
            name = getString("name") ?: return null,
            tankLiters = getDouble("tankLiters") ?: 5.5,
        )
    } catch (_: Exception) { null }
}
