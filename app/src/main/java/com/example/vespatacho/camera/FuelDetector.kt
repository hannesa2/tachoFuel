package com.example.vespatacho.camera

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object FuelDetector {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val pricePattern = Regex("""\d+[.,]\d{1,3}""")

    data class FuelResult(val price: String, val rawOcrTextKm: String)

    suspend fun detectPrice(bitmap: Bitmap): FuelResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = recognizer.process(image).await()
        val rawText = visionText.text
        val price = pricePattern.find(rawText)?.value?.replace(',', '.') ?: ""
        return FuelResult(price = price, rawOcrTextKm = rawText)
    }

    private suspend fun <T> Task<T>.await(): T =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { cont.resume(it) }
            addOnFailureListener { cont.resumeWithException(it) }
        }
}
