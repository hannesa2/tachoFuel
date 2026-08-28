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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import java.util.*
import java.util.concurrent.Executors

@Composable
fun VespaTachoApp(onSaved: () -> Unit = {}, viewModel: MainViewModel = viewModel()) {
    var showHistory by remember { mutableStateOf(false) }
    val captureState by viewModel.captureState.collectAsState()
    val readings by viewModel.readings.collectAsState()

    MaterialTheme {
        if (showHistory) {
            HistoryScreen(
                readings = readings,
                onDelete = viewModel::deleteReading,
                onBack = { showHistory = false },
            )
        } else {
            CameraScreen(
                captureState = captureState,
                onCapture = viewModel::captureAndAnalyse,
                onSave = { km, raw -> viewModel.saveReading(km, raw); onSaved() },
                onDiscard = viewModel::resetCapture,
                onShowHistory = { showHistory = true },
                latestKm = readings.firstOrNull()?.km,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraScreen(
    captureState: MainViewModel.CaptureState,
    onCapture: (ImageCapture, java.util.concurrent.Executor) -> Unit,
    onSave: (Int, String) -> Unit,
    onDiscard: () -> Unit,
    onShowHistory: () -> Unit,
    latestKm: Int?,
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
                title = { Text("🛵 Vespa Tacho") },
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
                // Camera preview — binding is managed by DisposableEffect above
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Camera permission required", textAlign = TextAlign.Center)
                }
            }

            // Overlay: latest km + capture button
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                latestKm?.let {
                    Text(
                        "Last saved: $it km",
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                }
                when (val state = captureState) {
                    is MainViewModel.CaptureState.Idle -> {
                        FloatingActionButton(onClick = { onCapture(imageCapture, executor) }) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Take photo")
                        }
                    }
                    is MainViewModel.CaptureState.Processing -> {
                        CircularProgressIndicator(color = Color.White)
                        Text("Detecting odometer…", color = Color.White)
                    }
                    is MainViewModel.CaptureState.Detected -> {
                        DetectedOverlay(km = state.km, rawText = state.rawText, onSave = onSave, onDiscard = onDiscard)
                    }
                    is MainViewModel.CaptureState.Error -> {
                        Text(state.message, color = Color.Red, textAlign = TextAlign.Center)
                        TextButton(onClick = onDiscard) { Text("Retry", color = Color.White) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetectedOverlay(km: Int, rawText: String, onSave: (Int, String) -> Unit, onDiscard: () -> Unit) {
    var editedKm by remember(km) { mutableStateOf(km.toString()) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Detected odometer reading:", color = Color.White, fontSize = 14.sp)
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.7f),
        ) {
            OutlinedTextField(
                value = editedKm,
                onValueChange = { editedKm = it.filter { c -> c.isDigit() } },
                label = { Text("km", color = Color.White) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.Gray,
                ),
                textStyle = LocalTextStyle.current.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.width(180.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { editedKm.toIntOrNull()?.let { onSave(it, rawText) } },
                enabled = editedKm.isNotBlank(),
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
fun HistoryScreen(
    readings: List<GasReading>,
    onDelete: (GasReading) -> Unit,
    onBack: () -> Unit,
) {
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val readingsWithKm = remember(readings) { readings.filter { it.km != null } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", fontSize = 20.sp)
                    }
                },
            )
        },
    ) { padding ->
        if (readings.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No readings yet.\nTake a photo of your tacho!", textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (readingsWithKm.size >= 2) {
                    item {
                        val first = readingsWithKm.last().km!!
                        val last = readingsWithKm.first().km!!
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Text(
                                "Total recorded: ${last - first} km",
                                modifier = Modifier.padding(16.dp),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                items(readings, key = { it.id }) { reading ->
                    val prevKm = readings.getOrNull(readings.indexOf(reading) + 1)?.km
                    ReadingCard(
                        reading = reading,
                        prevKm = prevKm,
                        dateStr = fmt.format(Date(reading.timestamp)),
                        onDelete = { onDelete(reading) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadingCard(reading: GasReading, prevKm: Int?, dateStr: String, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${reading.km ?: "—"} km", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(dateStr, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val currentKm = reading.km
                if (currentKm != null && prevKm != null) {
                    val delta = currentKm - prevKm
                    if (delta != 0) {
                        Text(
                            "${if (delta > 0) "+" else ""}$delta km since previous",
                            fontSize = 12.sp,
                            color = if (delta >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
