package com.example.vespatacho.ui

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vespatacho.TachoActivity
import com.example.vespatacho.TankanzeigeActivity
import com.example.vespatacho.VehicleManagementActivity
import com.example.vespatacho.data.KmReading
import com.example.vespatacho.data.Vehicle
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val vehicles by viewModel.vehicles.collectAsState()
    val selectedVehicleIndex by viewModel.selectedVehicleIndex.collectAsState()

    val safeIndex = if (vehicles.isEmpty()) 0 else selectedVehicleIndex.coerceAtMost(vehicles.lastIndex)
    val pagerState = rememberPagerState(initialPage = safeIndex) { vehicles.size }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != safeIndex) {
            viewModel.selectVehicle(pagerState.currentPage)
        }
    }
    LaunchedEffect(safeIndex) {
        if (pagerState.currentPage != safeIndex) {
            pagerState.animateScrollToPage(safeIndex)
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("🛵 TachoFuel") },
                    actions = {
                        IconButton(onClick = {
                            context.startActivity(Intent(context, VehicleManagementActivity::class.java))
                        }) {
                            Icon(Icons.Default.DirectionsCar, contentDescription = "Fahrzeuge verwalten")
                        }
                    },
                )
            },
        ) { padding ->
            if (vehicles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Kein Fahrzeug vorhanden.\nBitte zuerst ein Fahrzeug anlegen.",
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    TabRow(selectedTabIndex = safeIndex) {
                        vehicles.forEachIndexed { index, vehicle ->
                            Tab(
                                selected = index == safeIndex,
                                onClick = { viewModel.selectVehicle(index) },
                                text = { Text(vehicle.name) },
                            )
                        }
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f),
                    ) { page ->
                        val vehicle = vehicles.getOrNull(page) ?: return@HorizontalPager
                        VehicleTab(
                            vehicle = vehicle,
                            kmReadingsFlow = viewModel.kmReadingsForVehicle(vehicle.id),
                            onTacho = {
                                context.startActivity(
                                    Intent(context, TachoActivity::class.java)
                                        .putExtra("vehicleId", vehicle.id),
                                )
                            },
                            onTankanzeige = {
                                context.startActivity(
                                    Intent(context, TankanzeigeActivity::class.java)
                                        .putExtra("vehicleId", vehicle.id),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleTab(
    vehicle: Vehicle,
    kmReadingsFlow: Flow<List<KmReading>>,
    onTacho: () -> Unit,
    onTankanzeige: () -> Unit,
) {
    val readings by kmReadingsFlow.collectAsState(initial = emptyList())
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (readings.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Noch keine Einträge.", textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(readings, key = { _, reading -> reading.id }) { index, reading ->
                    HomeReadingCard(
                        reading = reading,
                        prevKm = readings.getOrNull(index + 1)?.km,
                        dateStr = fmt.format(Date(reading.timestamp)),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onTacho, modifier = Modifier.weight(1f)) { Text("Tacho") }
            Button(onClick = onTankanzeige, modifier = Modifier.weight(1f)) { Text("Tankanzeige") }
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
            prevKm?.let {
                val delta = reading.km - it
                Text(
                    "${if (delta > 0) "+" else ""}$delta km seit vorherigem Eintrag",
                    color = if (delta >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
            } ?: Text("Erster Eintrag", fontSize = 13.sp)
        }
    }
}
