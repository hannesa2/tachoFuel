package com.example.vespatacho.mlkit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vespatacho.mlkit.textdetector.TextRecProcessor
import com.example.vespatacho.ui.FuelInputOverlay
import com.example.vespatacho.ui.GasStationViewModel
import com.google.android.gms.common.annotation.KeepName
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

/** Minimum time between two [FuelDetector]-via-[GasStationViewModel.analyseLivePreviewFrame] runs. */
private const val LIVE_ANALYSIS_INTERVAL_MS = 800L

@KeepName
class GasStationCameraXActivity : ComponentActivity() {

    @ExperimentalGetImage
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Timber.d("onCreate")
        setContent {
            MaterialTheme {
                CameraXLivePreviewScreen(onSaved = { finish() }, onCancel = { finish() })
            }
        }
    }
}

/**
 * Compose replacement for the former XML layout + [android.app.Activity] lifecycle callbacks.
 * [PreviewView] and [GraphicOverlay] remain plain Android views (CameraX and the detection
 * drawing routines are tightly coupled to them) and are hosted via [AndroidView].
 *
 * The price/liter fields (same [FuelInputOverlay] used by [com.example.vespatacho.GasStationActivity])
 * are shown immediately and continuously auto-filled from [GasStationViewModel.analyseLivePreviewFrame]
 * — no explicit "take photo" step is required; the user only reviews/corrects the values and taps Save.
 */
@ExperimentalGetImage
@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraXLivePreviewScreen(
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    viewModel: GasStationViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val liveDetection by viewModel.liveDetection.collectAsState()

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val previewView = remember { PreviewView(context) }
    val graphicOverlay = remember { GraphicOverlay(context, null) }

    // Throttles how often a frame is grabbed as a bitmap and sent through FuelDetector, without
    // triggering recomposition (and therefore without rebinding the camera) on every frame.
    val lastAnalysisMillis = remember { AtomicLong(0L) }

    DisposableEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {}
    }

    DisposableEffect(cameraProvider, lensFacing) {
        val provider = cameraProvider
        var imageProcessor: VisionImageProcessor? = null

        if (provider != null) {
            provider.unbindAll()

            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            if (PreferenceUtils.isCameraLiveViewportEnabled(context)) {
                val previewBuilder = Preview.Builder()
                PreferenceUtils.getCameraXTargetResolution(context, lensFacing)?.let {
                    previewBuilder.setTargetResolution(it)
                }
                val previewUseCase = previewBuilder.build()
                previewUseCase.setSurfaceProvider(previewView.surfaceProvider)
                provider.bindToLifecycle(lifecycleOwner, cameraSelector, previewUseCase)
            }

            imageProcessor =
                try {
                    Timber.i("Using on-device Text recognition Processor for Latin")
                    TextRecProcessor(
                        context,
                        TextRecognizerOptions.Builder().build(),
                        onPriceDetected = viewModel::updateDetectedPrice,
                        onLiterDetected = viewModel::updateDetectedLiter,
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Can not create image processor")
                    null
                }

            if (imageProcessor != null) {
                val analysisBuilder = ImageAnalysis.Builder()
                PreferenceUtils.getCameraXTargetResolution(context, lensFacing)?.let {
                    analysisBuilder.setTargetResolution(it)
                }
                val analysisUseCase = analysisBuilder.build()
                var needUpdateGraphicOverlayImageSourceInfo = true

                analysisUseCase.setAnalyzer(
                    ContextCompat.getMainExecutor(context),
                ) { imageProxy: ImageProxy ->
                    if (needUpdateGraphicOverlayImageSourceInfo) {
                        val isImageFlipped = lensFacing == CameraSelector.LENS_FACING_FRONT
                        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                        if (rotationDegrees == 0 || rotationDegrees == 180) {
                            graphicOverlay.setImageSourceInfo(imageProxy.width, imageProxy.height, isImageFlipped)
                        } else {
                            graphicOverlay.setImageSourceInfo(imageProxy.height, imageProxy.width, isImageFlipped)
                        }
                        needUpdateGraphicOverlayImageSourceInfo = false
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastAnalysisMillis.get() >= LIVE_ANALYSIS_INTERVAL_MS) {
                        lastAnalysisMillis.set(now)
                        BitmapUtils.getBitmap(imageProxy)?.let { bitmap -> viewModel.analyseLivePreviewFrame(bitmap) }
                    }
                    try {
                        imageProcessor.processImageProxy(imageProxy, graphicOverlay)
                    } catch (e: MlKitException) {
                        Timber.e(e, "Failed to process image. Error: ${e.localizedMessage}")
                    }
                }
                provider.bindToLifecycle(lifecycleOwner, cameraSelector, analysisUseCase)
            }
        }

        onDispose {
            imageProcessor?.stop()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        AndroidView(factory = { graphicOverlay }, modifier = Modifier.fillMaxSize())

        Row(modifier = Modifier.align(Alignment.TopEnd)) {
            IconButton(
                onClick = {
                    val newLensFacing =
                        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                            CameraSelector.LENS_FACING_BACK
                        } else {
                            CameraSelector.LENS_FACING_FRONT
                        }
                    val provider = cameraProvider
                    if (provider != null) {
                        try {
                            if (provider.hasCamera(
                                    CameraSelector.Builder().requireLensFacing(newLensFacing).build(),
                                )
                            ) {
                                Timber.d("Set facing to $newLensFacing")
                                lensFacing = newLensFacing
                            }
                        } catch (_: CameraInfoUnavailableException) {
                            // Falls through, keep current facing.
                        }
                    }
                },
            ) {
                Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch camera", tint = Color.White)
            }
            IconButton(onClick = { /* no settings screen yet */ }) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FuelInputOverlay(
                detectedPrice = liveDetection.price,
                detectedLiter = liveDetection.liter,
                rawOcrTextFuel = liveDetection.rawOcrTextFuel,
                onSave = { price, liter, rawText ->
                    viewModel.saveLiveReading(price, liter, rawText)
                    onSaved()
                },
                onDiscard = onCancel,
            )
        }
    }
}
