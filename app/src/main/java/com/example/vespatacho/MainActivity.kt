package com.example.vespatacho

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.vespatacho.ui.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeScreen(
                onTacho = { startActivity(Intent(this, TachoActivity::class.java)) },
                onTankanzeige = { startActivity(Intent(this, TankanzeigeActivity::class.java)) },
            )
        }
    }
}
