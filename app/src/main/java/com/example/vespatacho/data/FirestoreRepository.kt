package com.example.vespatacho.data

import com.example.vespatacho.BuildConfig
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
 * In DEBUG builds a fixed UID is used so debug and release installs share
 * the same Firestore data regardless of anonymous auth session.
 * In RELEASE the UID comes from anonymous Firebase Auth (device-stable).
 */
class FirestoreRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    /** Shared fixed UID so debug and release installs always access the same Firestore data. */
    private val debugUid = "q6a03UpdpuVatxhrYgOe7ehNjuW2"

    /** Returns the UID to use for Firestore paths. */
    private suspend fun uid(): String = debugUid

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
    put("rawOcrTextKm", rawOcrTextKm)
    put("rawOcrTextFuel", rawOcrTextFuel)
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
            rawOcrTextKm = getString("rawOcrTextKm"),
        rawOcrTextFuel = getString("rawOcrTextFuel"),
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
