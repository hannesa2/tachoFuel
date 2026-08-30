package com.example.vespatacho

import com.example.vespatacho.data.AppDatabase
import com.example.vespatacho.data.DetectionSampleRepository
import com.example.vespatacho.data.FirestoreRepository
import com.example.vespatacho.data.GasReadingRepository
import com.example.vespatacho.utils.CrashlyticSetup
import info.appdev.charting.utils.Utils
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

    val detectionSampleRepository by lazy {
        DetectionSampleRepository(dao = database.detectionSampleDao())
    }

    override fun onCreate() {
        super.onCreate()
        Utils.init(this)
        appScope.launch {
            repository.syncFromCloud()
            detectionSampleRepository.retryPendingUploads()
        }
        CrashlyticSetup.init(contentResolver)
    }
}
