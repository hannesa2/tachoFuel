package com.example.vespatacho.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vespatacho.data.KmReading
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTacho: () -> Unit,
    onTankanzeige: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val kmReadings by viewModel.kmReadings.collectAsState()
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("TachoFuel") })
            },
            bottomBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onTacho,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Tacho")
                    }
                    Button(
                        onClick = onTankanzeige,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Tankanzeige")
                    }
                }
            },
        ) { padding ->
            if (kmReadings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Noch keine Kilometerstände gespeichert.", textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(kmReadings, key = { _, reading -> reading.id }) { index, reading ->
                        HomeReadingCard(
                            reading = reading,
                            prevKm = kmReadings.getOrNull(index + 1)?.km,
                            dateStr = fmt.format(Date(reading.timestamp)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeReadingCard(reading: KmReading, prevKm: Int?, dateStr: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("${reading.km} km", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(dateStr, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                prevKm?.let { previous ->
                    val delta = reading.km - previous
                    "${if (delta > 0) "+" else ""}$delta km seit vorherigem Eintrag"
                } ?: "Erster Eintrag",
                color = if (prevKm == null || reading.km - prevKm >= 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                fontSize = 13.sp,
            )
        }
    }
}
