package com.example.vespatacho

import com.example.vespatacho.data.AppDatabase
import com.example.vespatacho.data.FirestoreRepository
import com.example.vespatacho.data.GasReadingRepository
import info.hannes.logcat.LoggingApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VespaTachoApp : LoggingApplication() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { AppDatabase.getInstance(this) }

    val repository by lazy {
        GasReadingRepository(
            gasDao = database.gasReadingDao(),
            vehicleDao = database.vehicleDao(),
            firestore = FirestoreRepository(),
            scope = appScope,
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Pull any records from Firestore that are missing locally.
        appScope.launch { repository.syncFromCloud() }
    }
}
