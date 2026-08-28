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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import com.example.vespatacho.data.Vehicle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleManagementScreen(viewModel: VehicleManagementViewModel = viewModel()) {
    val vehicles by viewModel.vehicles.collectAsState()
    var addDialogOpen by remember { mutableStateOf(false) }
    var editVehicle by remember { mutableStateOf<Vehicle?>(null) }

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
                            Text(
                                text = vehicle.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                            )
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
        VehicleNameDialog(
            title = "Fahrzeug hinzufügen",
            initialName = "",
            onDismiss = { addDialogOpen = false },
            onConfirm = { name ->
                viewModel.addVehicle(name)
                addDialogOpen = false
            },
        )
    }

    editVehicle?.let { vehicle ->
        VehicleNameDialog(
            title = "Fahrzeug bearbeiten",
            initialName = vehicle.name,
            onDismiss = { editVehicle = null },
            onConfirm = { name ->
                viewModel.updateVehicle(vehicle.copy(name = name))
                editVehicle = null
            },
        )
    }
}

@Composable
private fun VehicleNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.trim().isNotEmpty(),
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        },
    )
}
