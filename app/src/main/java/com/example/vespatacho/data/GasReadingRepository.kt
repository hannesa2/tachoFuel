package com.example.vespatacho.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Single source of truth for GasReading and Vehicle data.
 *
 * Every mutating operation writes to Room first (so the UI updates instantly),
 * then syncs to Firestore in the background on IO.
 */
class GasReadingRepository(
    private val gasDao: GasReadingDao,
    private val vehicleDao: VehicleDao,
    private val firestore: FirestoreRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {

    // ── GasReading ──────────────────────────────────────────────────────────

    fun getAllByVehicle(vehicleId: Long): Flow<List<GasReading>> =
        gasDao.getAllByVehicle(vehicleId)

    suspend fun getLatestByVehicle(vehicleId: Long): GasReading? =
        gasDao.getLatestByVehicle(vehicleId)

    suspend fun getById(id: Long): GasReading? =
        gasDao.getById(id)

    suspend fun insert(reading: GasReading): Long {
        val id = gasDao.insert(reading)
        scope.launch { runCatching { firestore.upsertReading(reading.copy(id = id)) } }
        return id
    }

    suspend fun update(reading: GasReading) {
        gasDao.update(reading)
        scope.launch { runCatching { firestore.upsertReading(reading) } }
    }

    suspend fun delete(reading: GasReading) {
        gasDao.delete(reading)
        scope.launch { runCatching { firestore.deleteReading(reading) } }
    }

    // ── Vehicle ─────────────────────────────────────────────────────────────

    fun getAllVehicles(): Flow<List<Vehicle>> =
        vehicleDao.getAllVehicles()

    suspend fun insertVehicle(vehicle: Vehicle): Long {
        val id = vehicleDao.insert(vehicle)
        scope.launch { runCatching { firestore.upsertVehicle(vehicle.copy(id = id)) } }
        return id
    }

    suspend fun updateVehicle(vehicle: Vehicle) {
        vehicleDao.update(vehicle)
        scope.launch { runCatching { firestore.upsertVehicle(vehicle) } }
    }

    suspend fun deleteVehicle(vehicle: Vehicle) {
        vehicleDao.delete(vehicle)
        scope.launch { runCatching { firestore.deleteVehicle(vehicle) } }
    }

    // ── Cloud → Local sync ───────────────────────────────────────────────────

    /**
     * Pulls records from Firestore that are missing locally and inserts them
     * into Room. Call once on app start.
     */
    suspend fun syncFromCloud() {
        runCatching {
            // Sync vehicles first (readings reference vehicleId)
            val localVehicleIds = vehicleDao.getAllVehiclesOnce().map { it.id }.toSet()
            firestore.fetchVehiclesMissingLocally(localVehicleIds).forEach { vehicleDao.insert(it) }

            // Sync readings
            val localReadingIds = gasDao.getAllIds().toSet()
            firestore.fetchReadingsMissingLocally(localReadingIds).forEach { gasDao.insert(it) }
        }
    }
}
