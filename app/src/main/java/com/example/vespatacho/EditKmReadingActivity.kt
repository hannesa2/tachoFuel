package com.example.vespatacho

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.vespatacho.ui.EditKmReadingScreen

class EditKmReadingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EditKmReadingScreen(
                onSaved = { finish() },
                onCancel = { finish() },
            )
        }
    }
}
