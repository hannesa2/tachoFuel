package com.example.vespatacho

import com.example.vespatacho.data.AppDatabase
import info.hannes.logcat.LoggingApplication

class VespaTachoApp : LoggingApplication() {
    // Eagerly initialize the DB so the first query has no cold-start delay.
    val database by lazy { AppDatabase.getInstance(this) }
}
