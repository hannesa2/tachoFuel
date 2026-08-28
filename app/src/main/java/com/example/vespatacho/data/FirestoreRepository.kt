package com.example.vespatacho.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Handles all Firestore read/write operations.
 *
 * Data lives under: users/{uid}/gasReadings/{vehicleId}_{readingId}
 *                   users/{uid}/vehicles/{vehicleId}
 *
 * Anonymous auth is used so data is tied to the device installation.
 * The UID is stable unless the user clears app data.
 */
class FirestoreRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    /** Returns the current user UID, signing in anonymously if needed. */
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
        readingsCollection().document(docId).set(reading.toMap(), SetOptions.merge()).await()
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
        vehiclesCollection().document("${vehicle.id}").set(vehicle.toMap(), SetOptions.merge()).await()
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

private fun GasReading.toMap() = buildMap<String, Any?> {
    put("id", id)
    put("vehicleId", vehicleId)
    put("km", km)
    put("price", price)
    put("liter", liter)
    put("rawOcrText", rawOcrText)
    put("timestamp", timestamp)
}

private fun com.google.firebase.firestore.DocumentSnapshot.toGasReading(): GasReading? {
    return try {
        GasReading(
            id = getLong("id") ?: return null,
            vehicleId = getLong("vehicleId") ?: 1L,
            km = getLong("km")?.toInt(),
            price = getDouble("price"),
            liter = getDouble("liter"),
            rawOcrText = getString("rawOcrText"),
            timestamp = getLong("timestamp") ?: System.currentTimeMillis(),
        )
    } catch (_: Exception) { null }
}

private fun Vehicle.toMap() = mapOf("id" to id, "name" to name)

private fun com.google.firebase.firestore.DocumentSnapshot.toVehicle(): Vehicle? {
    return try {
        Vehicle(
            id = getLong("id") ?: return null,
            name = getString("name") ?: return null,
        )
    } catch (_: Exception) { null }
}
