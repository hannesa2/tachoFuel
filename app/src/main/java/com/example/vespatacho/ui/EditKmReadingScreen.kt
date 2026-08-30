package com.example.vespatacho.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditKmReadingScreen(
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    viewModel: EditGasReadingViewModel = viewModel(),
) {
    val reading by viewModel.reading.collectAsState()
    val odometerImage by viewModel.odometerImage.collectAsState()
    val fuelImage by viewModel.fuelImage.collectAsState()

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Eintrag bearbeiten") }) },
        ) { padding ->
            if (reading == null) {
                CircularProgressIndicator(modifier = Modifier.padding(padding))
            } else {
                var kmText by remember(reading) { mutableStateOf(reading!!.km?.toString() ?: "") }
                var priceText by remember(reading) { mutableStateOf(reading!!.price?.toString() ?: "") }
                var literText by remember(reading) { mutableStateOf(reading!!.liter?.toString() ?: "") }
                var timestamp by remember(reading) { mutableLongStateOf(reading!!.timestamp) }

                val cal = remember(timestamp) {
                    Calendar.getInstance().apply { timeInMillis = timestamp }
                }
                val formattedDateTime = remember(timestamp) {
                    "%02d.%02d.%04d %02d:%02d".format(
                        cal.get(Calendar.DAY_OF_MONTH),
                        cal.get(Calendar.MONTH) + 1,
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                    )
                }

                var showDatePicker by remember { mutableStateOf(false) }
                var showTimePicker by remember { mutableStateOf(false) }

                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = run {
                        val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                        utcCal.timeInMillis = timestamp
                        utcCal.set(Calendar.HOUR_OF_DAY, 0)
                        utcCal.set(Calendar.MINUTE, 0)
                        utcCal.set(Calendar.SECOND, 0)
                        utcCal.set(Calendar.MILLISECOND, 0)
                        utcCal.timeInMillis
                    },
                )
                val timePickerState = rememberTimePickerState(
                    initialHour = cal.get(Calendar.HOUR_OF_DAY),
                    initialMinute = cal.get(Calendar.MINUTE),
                    is24Hour = true,
                )

                if (showDatePicker) {
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                showDatePicker = false
                                showTimePicker = true
                            }) { Text("Weiter") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") }
                        },
                    ) { DatePicker(state = datePickerState) }
                }

                if (showTimePicker) {
                    AlertDialog(
                        onDismissRequest = { showTimePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                showTimePicker = false
                                val selectedDayMillis = datePickerState.selectedDateMillis ?: timestamp
                                val newCal = Calendar.getInstance().apply {
                                    val utcCal = Calendar.getInstance(
                                        java.util.TimeZone.getTimeZone("UTC"),
                                    ).apply { timeInMillis = selectedDayMillis }
                                    set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                                    set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                                    set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                                    set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                    set(Calendar.MINUTE, timePickerState.minute)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                timestamp = newCal.timeInMillis
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showTimePicker = false }) { Text("Abbrechen") }
                        },
                        text = { TimePicker(state = timePickerState) },
                    )
                }

                fun String.toFlexibleDoubleOrNull(): Double? = replace(',', '.').toDoubleOrNull()

                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // ── Editable fields ──────────────────────────────────────
                    OutlinedTextField(
                        value = kmText,
                        onValueChange = { kmText = it.filter { c -> c.isDigit() } },
                        label = { Text("km") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("Preis (€)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = literText,
                        onValueChange = { literText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("Liter") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = formattedDateTime,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Datum & Uhrzeit") },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Datum ändern")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                viewModel.save(
                                    km = kmText.toIntOrNull(),
                                    price = priceText.toFlexibleDoubleOrNull(),
                                    liter = literText.toFlexibleDoubleOrNull(),
                                    timestamp = timestamp,
                                    onDone = onSaved,
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Speichern") }
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                        ) { Text("Abbrechen") }
                    }

                    // ── OCR scan images (readonly) ───────────────────────────
                    HorizontalDivider()
                    if (odometerImage != null || fuelImage != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            odometerImage?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Tacho-Scan",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(max = 200.dp),
                                )
                            }
                            fuelImage?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Tankanzeige-Scan",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(max = 200.dp),
                                )
                            }
                        }
                    } else {
                        Text(
                            "Keine Scan-Bilder vorhanden",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }

                    // ── Raw OCR text (readonly) ──────────────────────────────
                    reading?.rawOcrTextKm?.takeIf { it.isNotBlank() }?.let { ocrText ->
                        OutlinedTextField(
                            value = ocrText,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("OCR Tacho") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    reading?.rawOcrTextFuel?.takeIf { it.isNotBlank() }?.let { ocrText ->
                        OutlinedTextField(
                            value = ocrText,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("OCR Tankanzeige") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
