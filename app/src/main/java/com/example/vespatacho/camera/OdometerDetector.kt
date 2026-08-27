package com.example.vespatacho.camera

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Detects the odometer value from a Vespa tacho photo using ML Kit on-device OCR.
 *
 * The Vespa Veglia Borletti odometer shows 5 digits on a black drum (e.g. "28041").
 * Strategy:
 *  1. Run ML Kit text recognition on the full bitmap.
 *  2. Collect all digit sequences of 4–6 characters.
 *  3. Prefer the sequence whose numeric value is closest to the latest known reading
 *     (avoids picking up "100" from the speed scale).
 *  4. Strip the last digit if it appears to be a tenths indicator (shown in red on the drum).
 */
object OdometerDetector {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Returns the detected km value and the full raw OCR text, or null if nothing plausible found. */
    suspend fun detect(bitmap: Bitmap, lastKnownKm: Int? = null): OdometerResult? {
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = recognizer.process(image).await()

        val rawText = visionText.text

        // Search each OCR element independently to avoid merging unrelated numbers
        // (e.g. speedometer scale digits concatenating with the odometer reading).
        val digitPattern = Regex("""\d{4,6}""")
        val candidates = visionText.textBlocks
            .flatMap { it.lines }
            .flatMap { it.elements }
            .flatMap { digitPattern.findAll(it.text.replace(Regex("\\s"), "")) }
            .mapNotNull { it.value.toIntOrNull() }
            // Plausible odometer range: 0 – 999 999 km
            .filter { it in 0..999_999 }
            // The Veglia Borletti tenths drum is shown as a 6th digit in the OCR output.
            // Strip it so the result is always a whole-km value.
            .map { if (it >= 100_000) it / 10 else it }
            .toList()

        if (candidates.isEmpty()) return OdometerResult(km = null, rawOcrText = rawText)

        // If we have a reference point, pick the candidate closest to it.
        val best = if (lastKnownKm != null) {
            candidates.minByOrNull { kotlin.math.abs(it - lastKnownKm) }
        } else {
            // Without a reference, pick the largest 5-digit number (most likely the odometer)
            candidates.maxOrNull()
        }

        return OdometerResult(km = best, rawOcrText = rawText)
    }

    data class OdometerResult(val km: Int?, val rawOcrText: String)

    /** Extension to use ML Kit Tasks as a coroutine. */
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { cont.resume(it) }
            addOnFailureListener { cont.resumeWithException(it) }
        }
}
