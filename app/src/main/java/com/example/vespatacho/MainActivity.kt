package com.example.vespatacho

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.vespatacho.ui.HomeScreen
import info.hannes.github.AppUpdateHelper

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HomeScreen() }

        AppUpdateHelper.checkForNewVersion(this, BuildConfig.GIT_REPOSITORY)
    }
}
