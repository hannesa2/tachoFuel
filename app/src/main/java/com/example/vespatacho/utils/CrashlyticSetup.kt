package com.example.vespatacho.utils

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.provider.Settings
import com.example.vespatacho.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics
import info.hannes.crashlytic.CrashlyticsTree
import timber.log.Timber

object CrashlyticSetup {
    @SuppressLint("HardwareIds")
    fun init(contentResolver: ContentResolver) {
        Timber.plant(CrashlyticsTree(Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)))
        FirebaseCrashlytics.getInstance().setCustomKey("VERSION_NAME", BuildConfig.VERSION_NAME)
        FirebaseCrashlytics.getInstance().setCustomKey("Build Date", BuildConfig.BUILD_DATE)
        FirebaseCrashlytics.getInstance().setCustomKey("SHA1", BuildConfig.SHA1)
    }
}