package com.example.vespatacho.ui

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import com.example.vespatacho.EditKmReadingActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricMoped
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vespatacho.BuildConfig
import com.example.vespatacho.TachoActivity
import com.example.vespatacho.TankanzeigeActivity
import com.example.vespatacho.VehicleManagementActivity
import com.example.vespatacho.data.GasReading
import com.example.vespatacho.data.Vehicle
import info.hannes.github.AppUpdateHelper
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
    val pagerState = rememberPagerState(initialPage = selectedVehicleIndex) { vehicles.size }

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
                            Icon(Icons.Default.ElectricMoped, contentDescription = "Fahrzeuge verwalten")
                        }
                        var menuExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menü")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Nach Update suchen") },
                                leadingIcon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    val activity = context as? androidx.appcompat.app.AppCompatActivity
                                    if (activity != null) {
                                        AppUpdateHelper.checkWithDialog(
                                            activity,
                                            BuildConfig.GIT_REPOSITORY,
                                        )
                                    }
                                },
                            )
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
                            onDelete = viewModel::deleteReading,
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
    kmReadingsFlow: Flow<List<GasReading>>,
    onDelete: (GasReading) -> Unit,
    onTacho: () -> Unit,
    onTankanzeige: () -> Unit,
) {
    val readings by kmReadingsFlow.collectAsState(initial = emptyList())
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val listState = rememberLazyListState()

    // Scroll to top whenever a new record is added (list grows)
    LaunchedEffect(readings.size) {
        if (readings.isNotEmpty()) listState.animateScrollToItem(0)
    }

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
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(readings, key = { _, reading -> reading.id }) { index, reading ->
                    HomeReadingCard(
                        reading = reading,
                        prevKm = readings.getOrNull(index + 1)?.km,
                        dateStr = fmt.format(Date(reading.timestamp)),
                        estimatedRange = if (index == 0) estimatedRangeKm(readings, vehicle.tankLiters) else null,
                        tankLiters = vehicle.tankLiters,
                        onDelete = { onDelete(reading) },
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

        FuelConsumptionChart(
            readings = readings,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeReadingCard(
    reading: GasReading,
    prevKm: Int?,
    dateStr: String,
    estimatedRange: Int?,
    tankLiters: Double = 5.5,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val title = buildString {
        append(reading.km?.let { "$it km" } ?: "— km")
        if (reading.price != null && reading.liter != null) {
            append(" • €${"%.2f".format(reading.price)} / ${"%.2f".format(reading.liter)} l")
        }
    }

    if (showOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showOptionsDialog = false },
            title = { Text(title) },
            text = { Text("Was möchtest du tun?") },
            confirmButton = {
                TextButton(onClick = {
                    showOptionsDialog = false
                    context.startActivity(
                        Intent(context, EditKmReadingActivity::class.java)
                            .putExtra("readingId", reading.id),
                    )
                }) { Text("Bearbeiten") }
            },
            dismissButton = {
                TextButton(onClick = { showOptionsDialog = false; showDeleteDialog = true }) {
                    Text("Löschen")
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eintrag löschen?") },
            text = { Text("$title vom $dateStr wirklich löschen?") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Abbrechen") }
            },
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    context.startActivity(
                        Intent(context, EditKmReadingActivity::class.java)
                            .putExtra("readingId", reading.id),
                    )
                },
                onLongClick = { showOptionsDialog = true },
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("${reading.km?.fmtKm() ?: "—"} km", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (reading.price != null && reading.liter != null) {
                Text("€${"%.2f".format(reading.price)} • ${"%.2f".format(reading.liter)} l")
            }
            Text(dateStr, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val currentKm = reading.km
            if (currentKm != null && prevKm != null) {
                val delta = currentKm - prevKm
                Text(
                    "${if (delta > 0) "+" else ""}$delta km seit vorherigem Eintrag",
                    color = if (delta >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
            } else {
                Text("Erster Eintrag", fontSize = 13.sp)
            }
            if (estimatedRange != null) {
                val emptyAtKm = reading.km?.let { it + estimatedRange }
                Text(
                    "~$estimatedRange km Reichweite (voller Tank, ${"%.1f".format(tankLiters)} l)",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 13.sp,
                )
                if (emptyAtKm != null) {
                    Text(
                        "${emptyAtKm.fmtKm()} km",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * Estimates the range in km on a full [tankLiters]-litre tank, based on
 * average consumption derived from all refuelling entries.
 * Returns null if there are fewer than two data points.
 */
private fun estimatedRangeKm(readings: List<GasReading>, tankLiters: Double): Int? {
    val consumptions = readings.zipWithNext().mapNotNull { (curr, prev) ->
        val km = curr.km?.let { c -> prev.km?.let { p -> (c - p).toDouble() } } ?: return@mapNotNull null
        val liters = curr.liter ?: return@mapNotNull null
        if (km <= 0.0 || liters <= 0.0) return@mapNotNull null
        liters / km * 100.0 // L/100 km
    }
    if (consumptions.isEmpty()) return null
    val avgL100km = consumptions.average()
    return (tankLiters / avgL100km * 100.0).toInt()
}

/** Formats an Int km value with German thousands dot, e.g. 28281 → "28.281". */
private fun Int.fmtKm(): String = String.format("%,d", this).replace(',', '.')
