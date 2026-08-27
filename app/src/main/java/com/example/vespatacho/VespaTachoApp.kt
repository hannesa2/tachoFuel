package com.example.vespatacho

import android.app.Application
import com.example.vespatacho.data.AppDatabase

class VespaTachoApp : Application() {
    // Eagerly initialise the DB so the first query has no cold-start delay.
    val database by lazy { AppDatabase.getInstance(this) }
}
