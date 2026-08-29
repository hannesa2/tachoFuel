package com.example.vespatacho.camera

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/**
 * Detects fuel price and liter amount from a German fuel pump display.
 *
 * Physical layout of this pump type:
 *   [0005,85]  €      ← price in LCD, "€" is a panel label to the right
 *   [0002,46]  Liter  ← liters in LCD, "Liter" is a panel label to the right
 *   Volumen kompensiert auf 15°C
 *   Cent/Liter
 *   [237,9]           ← price per liter in Cent, separate smaller LCD
 *
 * OCR challenges:
 *   - 7-segment LCD: "2," is often misread as "c" → "0002,46" becomes "000c46"
 *   - "€" and "Liter" are panel labels, often on the SAME OCR line as the number
 *     or in a separate text block at the same Y position
 *
 * Strategy:
 *  1. Normalise LCD misreads before parsing (c→2, for digit-c-digit pattern).
 *  2. Pass 1 — same-line label: number and label on same OCR line (e.g. "000c46 Liter").
 *  3. Pass 2 — next-line label: label appears on line below the number.
 *  4. Pass 3 — spatial proximity: find text blocks containing "€"/"Liter" labels and
 *     match them to the closest numeric text block by Y-coordinate.
 *  5. Pass 4 — magnitude fallback.
 */
object FuelDetector {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    data class FuelResult(
        val price: String,
        val liter: String,
        val rawOcrTextFuel: String,
    )

    suspend fun detect(bitmap: Bitmap): FuelResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = recognizer.process(image).await()
        val rawText = visionText.text

        Timber.d("rawOcrTextFuel:\n$rawText")

        // Flat list of (lineText, centerY) for spatial matching
        data class OcrLine(val text: String, val centerY: Int)
        val allLines = visionText.textBlocks
            .flatMap { block -> block.lines.map { line ->
                OcrLine(
                    text = line.elements.joinToString(" ") { it.text },
                    centerY = line.boundingBox?.centerY() ?: 0,
                )
            }}

        var detectedLiter: Double? = null
        var detectedPrice: Double? = null
        var detectedCentPerLiter: Double? = null

        // ── Pass 1: same-line label (most common for this pump) ─────────────
        for (line in allLines) {
            val norm = normaliseLcd(line.text)
            val up = norm.uppercase()
            val value = parseDecimal(norm) ?: continue
            when {
                (up.contains("LITER") || up.contains(Regex("""\bL\b"""))) && !up.contains("CENT") && detectedLiter == null ->
                    detectedLiter = value
                up.contains("€") || up.contains(Regex("""\bEUR\b""")) && detectedPrice == null ->
                    detectedPrice = value
                up.contains("CENT") && up.contains("LITER") && detectedCentPerLiter == null ->
                    detectedCentPerLiter = value
            }
        }

        // ── Pass 2: next-line label ──────────────────────────────────────────
        for (i in allLines.indices) {
            val value = parseDecimal(normaliseLcd(allLines[i].text)) ?: continue
            val nextUp = allLines.getOrNull(i + 1)?.text?.uppercase() ?: continue
            when {
                nextUp.contains("LITER") && !nextUp.contains("CENT") && detectedLiter == null ->
                    detectedLiter = value
                (nextUp.contains("€") || nextUp.contains(Regex("""\bEUR\b"""))) && detectedPrice == null ->
                    detectedPrice = value
                nextUp.contains("CENT") && nextUp.contains("LITER") && detectedCentPerLiter == null ->
                    detectedCentPerLiter = value
            }
        }

        // ── Pass 3: spatial label matching ──────────────────────────────────
        // Find lines that are pure labels (no number), find the closest numeric line by Y
        if (detectedLiter == null || detectedPrice == null) {
            val numericLines = allLines.mapNotNull { line ->
                parseDecimal(normaliseLcd(line.text))?.let { Pair(it, line.centerY) }
            }
            for (line in allLines) {
                val up = line.text.uppercase()
                val isLiterLabel = up.contains("LITER") && !up.contains("CENT") && parseDecimal(normaliseLcd(line.text)) == null
                val isPriceLabel = (up.contains("€") || up.contains(Regex("""\bEUR\b"""))) && parseDecimal(normaliseLcd(line.text)) == null
                val isCentLabel = up.contains("CENT") && up.contains("LITER") && parseDecimal(normaliseLcd(line.text)) == null

                val closest = numericLines.minByOrNull { abs(it.second - line.centerY) } ?: continue
                when {
                    isLiterLabel && detectedLiter == null -> detectedLiter = closest.first
                    isPriceLabel && detectedPrice == null -> detectedPrice = closest.first
                    isCentLabel && detectedCentPerLiter == null -> detectedCentPerLiter = closest.first
                }
            }
        }

        // ── Pass 4: magnitude-based fallback ────────────────────────────────
        if (detectedLiter == null || detectedPrice == null) {
            val allValues = allLines.mapNotNull { parseDecimal(normaliseLcd(it.text)) }.distinct().sorted()
            val ppl = detectedCentPerLiter?.div(100.0)
                ?: allValues.filter { it in 1.0..3.0 }.firstOrNull()
            val remaining = if (ppl != null) allValues.filter { abs(it - ppl) > 0.05 } else allValues

            if (detectedLiter == null)
                detectedLiter = remaining.filter { it in 0.5..25.0 }.firstOrNull()
            if (detectedPrice == null) {
                val lit = detectedLiter
                detectedPrice = remaining.filter { if (lit != null) abs(it - lit) > 0.05 else true }.maxOrNull()
            }
            // Cross-validate
            if (ppl != null && detectedLiter != null && detectedPrice != null) {
                val expected = detectedLiter * ppl
                if (abs(expected - detectedPrice) / detectedPrice > 0.15) {
                    val swapped = detectedPrice * ppl
                    if (abs(swapped - detectedLiter) / detectedLiter < 0.15) {
                        val tmp = detectedLiter; detectedLiter = detectedPrice; detectedPrice = tmp
                    }
                }
            }
        }

        Timber.d("FuelDetector → price=$detectedPrice liter=$detectedLiter centPerLiter=$detectedCentPerLiter")

        return FuelResult(
            price = detectedPrice?.let { "%.2f".format(it) } ?: "",
            liter = detectedLiter?.let { "%.3f".format(it) } ?: "",
            rawOcrTextFuel = rawText,
        )
    }

    /**
     * Normalises 7-segment LCD OCR misreads common on fuel pumps:
     *  - "2," merged into "c":  "000c46" → "0002,46"
     *  - "b" misread for "6":   e.g. "5b85" → leave as-is (lower confidence)
     */
    private fun normaliseLcd(text: String): String =
        // digit + "c" + digit: the "c" is a misread of "2,"
        text.replace(Regex("""(\d)c(\d)"""), "$12,$2")

    /**
     * Extracts the first decimal number from a (normalised) text line.
     * Handles pump leading-zero format like "0002,46" → 2.46.
     */
    private fun parseDecimal(text: String): Double? {
        val pattern = Regex("""\d{1,6}[.,]\d{1,3}""")
        return pattern.find(text.trim())?.value?.replace(',', '.')?.toDoubleOrNull()
    }

    private suspend fun <T> Task<T>.await(): T =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { cont.resume(it) }
            addOnFailureListener { cont.resumeWithException(it) }
        }
}
