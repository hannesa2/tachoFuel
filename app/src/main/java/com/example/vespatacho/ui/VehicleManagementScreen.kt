package com.example.vespatacho.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vespatacho.data.TankSizeEntry
import com.example.vespatacho.data.Vehicle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleManagementScreen(viewModel: VehicleManagementViewModel = viewModel()) {
    val vehicles by viewModel.vehicles.collectAsState()
    var addDialogOpen by remember { mutableStateOf(false) }
    var editVehicle by remember { mutableStateOf<Vehicle?>(null) }
    var historyVehicle by remember { mutableStateOf<Vehicle?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Fahrzeuge verwalten") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { addDialogOpen = true }) {
                Icon(Icons.Default.Add, contentDescription = "Fahrzeug hinzufügen")
            }
        },
    ) { padding ->
        if (vehicles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Noch keine Fahrzeuge vorhanden.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(vehicles, key = { it.id }) { vehicle ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = vehicle.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Tank: ${"%.1f".format(vehicle.tankLiters)} l",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { historyVehicle = vehicle }) {
                                Icon(Icons.Default.History, contentDescription = "Tankgrößen-Verlauf")
                            }
                            IconButton(onClick = { editVehicle = vehicle }) {
                                Icon(Icons.Default.Edit, contentDescription = "Fahrzeug bearbeiten")
                            }
                            IconButton(onClick = { viewModel.deleteVehicle(vehicle) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Fahrzeug löschen")
                            }
                        }
                    }
                }
            }
        }
    }

    if (addDialogOpen) {
        VehicleDialog(
            title = "Fahrzeug hinzufügen",
            initialName = "",
            initialTankLiters = 5.5,
            onDismiss = { addDialogOpen = false },
            onConfirm = { name, tank ->
                viewModel.addVehicle(name, tank)
                addDialogOpen = false
            },
        )
    }

    editVehicle?.let { vehicle ->
        VehicleDialog(
            title = "Fahrzeug bearbeiten",
            initialName = vehicle.name,
            initialTankLiters = vehicle.tankLiters,
            onDismiss = { editVehicle = null },
            onConfirm = { name, tank ->
                viewModel.updateVehicle(vehicle.copy(name = name, tankLiters = tank))
                editVehicle = null
            },
        )
    }

    historyVehicle?.let { vehicle ->
        TankSizeHistoryDialog(
            vehicle = vehicle,
            viewModel = viewModel,
            onDismiss = { historyVehicle = null },
        )
    }
}

@Composable
private fun VehicleDialog(
    title: String,
    initialName: String,
    initialTankLiters: Double,
    onDismiss: () -> Unit,
    onConfirm: (String, Double) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var tankText by remember(initialTankLiters) { mutableStateOf("%.1f".format(initialTankLiters)) }
    val tankValid = tankText.replace(',', '.').toDoubleOrNull()?.let { it > 0.0 } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") },
                )
                OutlinedTextField(
                    value = tankText,
                    onValueChange = { tankText = it },
                    singleLine = true,
                    label = { Text("Tankgröße (Liter)") },
                    isError = !tankValid,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val tank = tankText.replace(',', '.').toDoubleOrNull() ?: initialTankLiters
                    onConfirm(name.trim(), tank)
                },
                enabled = name.trim().isNotEmpty() && tankValid,
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

/**
 * Lists this vehicle's tank-capacity periods (standard size plus any dated
 * overrides) and lets the user add/edit/delete entries with optional
 * from/to dates.
 */
@Composable
private fun TankSizeHistoryDialog(
    vehicle: Vehicle,
    viewModel: VehicleManagementViewModel,
    onDismiss: () -> Unit,
) {
    val entries by viewModel.tankSizeEntriesForVehicle(vehicle.id).collectAsState(initial = emptyList())
    var editEntry by remember { mutableStateOf<TankSizeEntry?>(null) }
    var addDialogOpen by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tankgrößen-Verlauf — ${vehicle.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Standardgröße: ${"%.1f".format(vehicle.tankLiters)} l. " +
                        "Optional kannst du Zeiträume mit abweichender Tankgröße hinterlegen.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (entries.isEmpty()) {
                    Text("Keine abweichenden Zeiträume hinterlegt.", style = MaterialTheme.typography.bodySmall)
                } else {
                    entries.forEach { entry ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${"%.1f".format(entry.tankLiters)} l")
                                    Text(
                                        "von ${entry.validFrom?.let { fmt.format(Date(it)) } ?: "immer"} " +
                                            "bis ${entry.validTo?.let { fmt.format(Date(it)) } ?: "immer"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { editEntry = entry }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Zeitraum bearbeiten")
                                }
                                IconButton(onClick = { viewModel.deleteTankSizeEntry(entry) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Zeitraum löschen")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { addDialogOpen = true }) { Text("Zeitraum hinzufügen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        },
    )

    if (addDialogOpen) {
        TankSizeEntryDialog(
            title = "Zeitraum hinzufügen",
            initialTankLiters = vehicle.tankLiters,
            initialValidFrom = null,
            initialValidTo = null,
            onDismiss = { addDialogOpen = false },
            onConfirm = { liters, from, to ->
                viewModel.addTankSizeEntry(
                    TankSizeEntry(vehicleId = vehicle.id, tankLiters = liters, validFrom = from, validTo = to),
                )
                addDialogOpen = false
            },
        )
    }

    editEntry?.let { entry ->
        TankSizeEntryDialog(
            title = "Zeitraum bearbeiten",
            initialTankLiters = entry.tankLiters,
            initialValidFrom = entry.validFrom,
            initialValidTo = entry.validTo,
            onDismiss = { editEntry = null },
            onConfirm = { liters, from, to ->
                viewModel.updateTankSizeEntry(entry.copy(tankLiters = liters, validFrom = from, validTo = to))
                editEntry = null
            },
        )
    }
}

/**
 * Add/edit dialog for a single tank-size period. [onConfirm] receives the
 * liter value and the optional from/to epoch-millis bounds (UTC midnight).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TankSizeEntryDialog(
    title: String,
    initialTankLiters: Double,
    initialValidFrom: Long?,
    initialValidTo: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Double, Long?, Long?) -> Unit,
) {
    var tankText by remember { mutableStateOf("%.1f".format(initialTankLiters)) }
    var validFrom by remember { mutableStateOf(initialValidFrom) }
    var validTo by remember { mutableStateOf(initialValidTo) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    val tankValid = tankText.replace(',', '.').toDoubleOrNull()?.let { it > 0.0 } == true
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    if (showFromPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = validFrom)
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    validFrom = state.selectedDateMillis
                    showFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) { Text("Abbrechen") }
            },
        ) { DatePicker(state = state) }
    }

    if (showToPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = validTo)
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    validTo = state.selectedDateMillis
                    showToPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) { Text("Abbrechen") }
            },
        ) { DatePicker(state = state) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = tankText,
                    onValueChange = { tankText = it },
                    singleLine = true,
                    label = { Text("Tankgröße (Liter)") },
                    isError = !tankValid,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                )
                OutlinedTextField(
                    value = validFrom?.let { fmt.format(Date(it)) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text("Von (optional)") },
                    placeholder = { Text("immer") },
                    trailingIcon = {
                        androidx.compose.foundation.layout.Row {
                            if (validFrom != null) {
                                IconButton(onClick = { validFrom = null }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Startdatum entfernen")
                                }
                            }
                            IconButton(onClick = { showFromPicker = true }) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Startdatum wählen")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = validTo?.let { fmt.format(Date(it)) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text("Bis (optional)") },
                    placeholder = { Text("immer") },
                    trailingIcon = {
                        androidx.compose.foundation.layout.Row {
                            if (validTo != null) {
                                IconButton(onClick = { validTo = null }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Enddatum entfernen")
                                }
                            }
                            IconButton(onClick = { showToPicker = true }) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Enddatum wählen")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (validFrom != null && validTo != null && validFrom!! > validTo!!) {
                    Text(
                        "Das Startdatum liegt nach dem Enddatum.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val tank = tankText.replace(',', '.').toDoubleOrNull() ?: initialTankLiters
                    onConfirm(tank, validFrom, validTo)
                },
                enabled = tankValid && !(validFrom != null && validTo != null && validFrom!! > validTo!!),
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

