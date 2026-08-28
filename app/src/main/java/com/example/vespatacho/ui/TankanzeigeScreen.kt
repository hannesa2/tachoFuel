package com.example.vespatacho.ui

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vespatacho.data.GasReading
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

@Composable
fun TankanzeigeApp(onSaved: () -> Unit = {}, viewModel: TankanzeigeViewModel = viewModel()) {
    var showHistory by remember { mutableStateOf(false) }
    val captureState by viewModel.captureState.collectAsState()
    val readings by viewModel.readings.collectAsState()

    MaterialTheme {
        if (showHistory) {
            FuelHistoryScreen(
                readings = readings,
                onDelete = viewModel::deleteReading,
                onBack = { showHistory = false },
            )
        } else {
            CameraFuelScreen(
                captureState = captureState,
                onCapture = viewModel::captureAndAnalyse,
                onSave = { price, liter, rawText -> viewModel.saveReading(price, liter, rawText); onSaved() },
                onDiscard = viewModel::resetCapture,
                onShowHistory = { showHistory = true },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraFuelScreen(
    captureState: TankanzeigeViewModel.CaptureState,
    onCapture: (ImageCapture, java.util.concurrent.Executor) -> Unit,
    onSave: (Double, Double, String) -> Unit,
    onDiscard: () -> Unit,
    onShowHistory: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCameraPermission = it
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val executor = remember { Executors.newSingleThreadExecutor() }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner, hasCameraPermission) {
        var cameraProvider: ProcessCameraProvider? = null
        if (hasCameraPermission) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                cameraProvider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                cameraProvider!!.unbindAll()
                cameraProvider!!.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose { cameraProvider?.unbindAll() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⛽ Tankanzeige") },
                actions = {
                    IconButton(onClick = onShowHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Camera permission required", textAlign = TextAlign.Center)
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (val state = captureState) {
                    is TankanzeigeViewModel.CaptureState.Idle -> {
                        FloatingActionButton(onClick = { onCapture(imageCapture, executor) }) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Take photo")
                        }
                    }
                    is TankanzeigeViewModel.CaptureState.Processing -> {
                        CircularProgressIndicator(color = Color.White)
                        Text("Detecting fuel price…", color = Color.White)
                    }
                    is TankanzeigeViewModel.CaptureState.Ready -> {
                        FuelInputOverlay(
                            detectedPrice = state.detectedPrice,
                            detectedLiter = state.detectedLiter,
                            rawOcrTextFuel = state.rawOcrTextFuel,
                            onSave = onSave,
                            onDiscard = onDiscard,
                        )
                    }
                    is TankanzeigeViewModel.CaptureState.Error -> {
                        Text(state.message, color = Color.Red, textAlign = TextAlign.Center)
                        TextButton(onClick = onDiscard) { Text("Retry", color = Color.White) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FuelInputOverlay(
    detectedPrice: String,
    detectedLiter: String,
    rawOcrTextFuel: String,
    onSave: (Double, Double, String) -> Unit,
    onDiscard: () -> Unit,
) {
    var price by remember(detectedPrice) { mutableStateOf(detectedPrice) }
    var liter by remember(detectedLiter) { mutableStateOf(detectedLiter) }

    fun String.toDoubleOrNullFlexible(): Double? = replace(',', '.').toDoubleOrNull()

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Erkannte Tankdaten:", color = Color.White, fontSize = 14.sp)
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.7f),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Preis (€)", color = Color.White) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray,
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.width(220.dp),
                )
                OutlinedTextField(
                    value = liter,
                    onValueChange = { liter = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Liter", color = Color.White) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray,
                    ),
                    modifier = Modifier.width(220.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    val parsedPrice = price.toDoubleOrNullFlexible()
                    val parsedLiter = liter.toDoubleOrNullFlexible()
                    if (parsedPrice != null && parsedLiter != null) onSave(parsedPrice, parsedLiter, rawOcrTextFuel)
                },
                enabled = price.toDoubleOrNullFlexible() != null && liter.toDoubleOrNullFlexible() != null,
            ) {
                Text("Save")
            }
            OutlinedButton(onClick = onDiscard) {
                Text("Discard", color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelHistoryScreen(
    readings: List<GasReading>,
    onDelete: (GasReading) -> Unit,
    onBack: () -> Unit,
) {
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tank-Historie") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", fontSize = 20.sp)
                    }
                },
            )
        },
    ) { padding ->
        if (readings.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Noch keine Tankvorgänge gespeichert.", textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(readings, key = { it.id }) { reading ->
                    FuelReadingCard(
                        reading = reading,
                        dateStr = fmt.format(Date(reading.timestamp)),
                        onDelete = { onDelete(reading) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FuelReadingCard(reading: GasReading, dateStr: String, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                reading.price?.let {
                    Text("€${"%.2f".format(it)}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                } ?: Text("—", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                reading.liter?.let {
                    Text("${"%.2f".format(it)} l")
                }
                reading.km?.let {
                    Text("$it km")
                }
                Text(dateStr, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                reading.price?.let {
                    Text(
                        "Gesamtkosten: €${"%.2f".format(it)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
