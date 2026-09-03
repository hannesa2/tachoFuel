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
 *   [0005,85]  €      ← price in LCD, "€" is a panel label to the right (often OCR-misread
 *                        as a trailing "e"/"E" glued to the digits, e.g. "D0D364e")
 *   [0002,46]  Liter  ← liters in LCD, "Liter" is a panel label to the right
 *   Volumen kompensiert auf 15°C
 *
 * Note: the small "Cent/Liter" sub-display (price per liter in cents) is intentionally
 * ignored — it is not needed for [FuelResult] and its OCR text ("Cent/Liter" is frequently
 * misread as e.g. "ConLiter") only caused false matches against the real "Liter" label.
 *
 * OCR challenges:
 *   - 7-segment LCD: "2," is often misread as "c" → "0002,46" becomes "000c46"
 *   - "€" and "Liter" are panel labels, often on the SAME OCR line as the number
 *     or in a separate text block at the same Y position
 *   - "€" itself is frequently misread as a lowercase/uppercase "e" glued to the digits
 *
 * Strategy:
 *  1. Normalise LCD misreads before parsing (c→2, for digit-c-digit pattern).
 *  2. Pass 1 — same-line label: number and label on same OCR line (e.g. "000c46 Liter").
 *  3. Pass 2 — next-line label: label appears on line below the number.
 *  4. Pass 3 — spatial proximity: find text blocks containing "€"/"Liter" labels and
 *     match them to the closest *plausible* numeric text block by Y-coordinate.
 *  5. Pass 4 — magnitude fallback.
 */
object FuelDetector {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Plausible value ranges used to reject obviously-wrong OCR matches (serial numbers, etc.)
    private val PRICE_RANGE = 0.3..3.5
    private val LITER_RANGE = 0.1..60.0
    private val PRICE_SUFFIX_CHARS = charArrayOf('e', 'E', '€')

    data class FuelResult(
        val price: String,
        val liter: String,
        val rawOcrTextFuel: String,
    )

    suspend fun detect(bitmap: Bitmap): FuelResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = recognizer.process(image).await()
        val rawText = visionText.text

        if (rawText.isNotBlank()) {
            Timber.d("rawOcrTextFuel:$rawText")

        // Flat list of (lineText, centerY) for spatial matching
        data class OcrLine(val text: String, val centerY: Int)

        val allLines = visionText.textBlocks
            .flatMap { block ->
                block.lines.map { line ->
                    OcrLine(
                        text = line.elements.joinToString(" ") { it.text },
                        centerY = line.boundingBox?.centerY() ?: 0,
                    )
                }
            }

        var detectedLiter: Double? = null
        var detectedPrice: Double? = null

        // ── Pass 1: same-line label (most common for this pump) ─────────────
        for (line in allLines) {
            val norm = normaliseLcd(line.text)
            val up = norm.uppercase()
            val value = parseDecimal(norm) ?: continue
            when {
                isLiterLabelText(up) && detectedLiter == null -> detectedLiter = value
                hasPriceIndicator(up) && detectedPrice == null -> detectedPrice = value
            }
        }

        // ── Pass 2: next-line label ──────────────────────────────────────────
        for (i in allLines.indices) {
            val value = parseDecimal(normaliseLcd(allLines[i].text)) ?: continue
            val nextUp = allLines.getOrNull(i + 1)?.text?.uppercase() ?: continue
            when {
                isLiterLabelText(nextUp) && detectedLiter == null -> detectedLiter = value
                hasPriceIndicator(nextUp) && detectedPrice == null -> detectedPrice = value
            }
        }

        // ── Pass 3: spatial label matching ──────────────────────────────────
        // Find lines that are pure labels (no number), find the closest *plausible* numeric
        // line by Y-coordinate (values outside the expected range are ignored, since they are
        // usually serial numbers or unrelated OCR noise).
        if (detectedLiter == null || detectedPrice == null) {
            val numericLines = allLines.mapNotNull { line ->
                parseDecimal(normaliseLcd(line.text))?.let { Pair(it, line.centerY) }
            }

            fun closestInRange(centerY: Int, range: ClosedFloatingPointRange<Double>) =
                numericLines.filter { it.first in range }.minByOrNull { abs(it.second - centerY) }

            for (line in allLines) {
                val up = line.text.uppercase()
                val hasOwnValue = parseDecimal(normaliseLcd(line.text)) != null
                val isLiterLabel = isLiterLabelText(up) && !hasOwnValue
                val isPriceLabel = hasPriceIndicator(up) && !hasOwnValue

                if (isLiterLabel && detectedLiter == null) {
                    closestInRange(line.centerY, LITER_RANGE)?.let { detectedLiter = it.first }
                }
                if (isPriceLabel && detectedPrice == null) {
                    closestInRange(line.centerY, PRICE_RANGE)?.let { detectedPrice = it.first }
                }
            }
        }

        // ── Pass 4: magnitude-based fallback ────────────────────────────────
        if (detectedLiter == null || detectedPrice == null) {
            val allValues = allLines.mapNotNull { parseDecimal(normaliseLcd(it.text)) }.distinct().sorted()

            if (detectedLiter == null)
                detectedLiter = allValues.filter { it in LITER_RANGE }.firstOrNull()
            if (detectedPrice == null) {
                val lit = detectedLiter
                detectedPrice = allValues
                    .filter { it in PRICE_RANGE }
                    .filter { if (lit != null) abs(it - lit) > 0.05 else true }
                    .maxOrNull()
            }
        }

        Timber.d("FuelDetector → price=$detectedPrice liter=$detectedLiter")

        return FuelResult(
            price = detectedPrice?.let { "%.2f".format(it) } ?: "",
            liter = detectedLiter?.let { "%.3f".format(it) } ?: "",
            rawOcrTextFuel = rawText,
        )
    } else {
            return FuelResult("","", rawText)
        }
    }

    /** True if the (uppercased) text looks like the "Liter" panel label. */
    private fun isLiterLabelText(up: String): Boolean =
        (up.contains("LITER") || up.contains(Regex("""\bL\b"""))) && !up.contains("CENT")

    /**
     * True if the (uppercased) text indicates the price/Euro panel label: "€", "EURO"/"EUR",
     * or a bare "E" — including the common OCR misread where "€" is glued directly after the
     * price digits, e.g. "364E".
     */
    private fun hasPriceIndicator(up: String): Boolean =
        up.contains("€") ||
            Regex("""\bEURO?\b""").containsMatchIn(up) ||
            Regex("""\dE\b""").containsMatchIn(up) ||
            Regex("""(^|\s)E(\s|$)""").containsMatchIn(up)

    /**
     * If [line] ends with "e", "E", or "€" (the price panel label, frequently OCR-misread as a
     * trailing letter glued to the digits, e.g. "D0D364e"), extracts and returns the price
     * formatted as "%.2f", or null if the line doesn't end with that suffix or contains no
     * plausible price value. Intended for immediate, per-frame autofill of the "Preis (€)"
     * field straight from the live OCR stream (see [com.example.vespatacho.mlkit.textdetector.TextRecProcessor]),
     * independent of the throttled [detect] bitmap analysis.
     *
     * Unlike [detect]'s [PRICE_RANGE]-bounded passes (tuned for the per-liter unit price), this
     * looks at the *total* amount shown next to the "e"/"E"/"€" label, so it uses a wider sanity
     * range. The digits are also frequently glued together without a recognised decimal
     * separator at all, with the leading zeros OCR-misread as "D"/"O" (e.g. "DOO364e" for
     * "0003,64e") — both cases are handled below.
     */
    fun extractPriceIfLineEndsWithPriceSuffix(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.last() !in PRICE_SUFFIX_CHARS) return null
        var body = trimmed.dropLast(1).trim()
        if (body.isEmpty()) return null
        // Leading LCD zeros are frequently OCR-misread as look-alike letters.
        body = body.replace(Regex("[DOdo]"), "0")
        val normalisedBody = normaliseLcd(body)

        var value = parseDecimal(normalisedBody)
        if (value == null) {
            // No decimal separator recognised at all (e.g. "000364") — assume the pump's fixed
            // 2-decimal-place format and insert the separator before the last 2 digits.
            val digits = normalisedBody.filter { it.isDigit() }
            if (digits.length in 3..6) {
                value = "${digits.dropLast(2)}.${digits.takeLast(2)}".toDoubleOrNull()
            }
        }

        if (value == null || value <= 0.0 || value > 999.99) return null
        return "%.2f".format(value)
    }

    /**
     * True if the (uppercased, trimmed) text is a "Liter" panel label — "Liter"/"Lter" or a
     * lone "L" — but NOT the "Cent/Liter" (price-per-liter) sub-display, which must never match.
     */
    private fun isLiterIndicatorLine(up: String): Boolean {
        if (up.contains("CENT")) return false
        return up == "L" || up.endsWith("LITER") || up.endsWith("LTER") || Regex("""\bL\b""").containsMatchIn(up)
    }

    /**
     * Converts a run of OCR-glued digits (no recognised decimal separator) into the pump's
     * fixed 2-decimal-place value, e.g. "000161" → 1.61, formatted as "%.3f" for consistency
     * with [FuelResult.liter].
     */
    private fun digitsToLiterValue(digits: String): String? {
        if (digits.length !in 3..6) return null
        val value = "${digits.dropLast(2)}.${digits.takeLast(2)}".toDoubleOrNull() ?: return null
        if (value <= 0.0 || value > 999.99) return null
        return "%.3f".format(value)
    }

    /**
     * Scans consecutive OCR [lines] for the fuel pump's liter value: the digits and the
     * "Liter"/"Lter"/"L" label may appear on the same line (e.g. "000161 Liter") or on
     * consecutive lines (digits, then "Liter" on the next line — the common case, since the
     * label often lands as its own OCR line). The "Cent/Liter" sub-display never matches (see
     * [isLiterIndicatorLine]). Intended for immediate, per-frame autofill of the "Liter" field
     * straight from the live OCR stream (see
     * [com.example.vespatacho.mlkit.textdetector.TextRecProcessor]).
     */
    fun extractLiterFromLines(lines: List<String>): String? {
        for (i in lines.indices) {
            val up = lines[i].trim().uppercase()
            if (!isLiterIndicatorLine(up)) continue

            // Same-line: digits and label together on this line.
            digitsToLiterValue(lines[i].filter { it.isDigit() })?.let { return it }

            // Next most common case: label is its own line, digits are on the previous line.
            lines.getOrNull(i - 1)?.let { prev ->
                digitsToLiterValue(prev.filter { it.isDigit() })?.let { return it }
            }
        }
        return null
    }

    /**
     * Normalises 7-segment LCD OCR misreads common on fuel pumps:
     *  - "2," merged into "c":  "000c46" → "0002,46"
     *  - "b" misread for "6":   e.g. "5b85" → leave as-is (lower confidence)
     *  - "L" misread for "1" between digits: "0L6" → "016" (e.g. "000 L6 I Liter" → liters 1.61)
     *  - "I" misread for "," between digits: "6I1" → "6,1"
     */
    private fun normaliseLcd(text: String): String {
        var normalised = text
        // digit + "c" + digit: the "c" is a misread of "2,"
        normalised = normalised.replace(Regex("""(\d)c(\d)"""), "$12,$2")
        // digit + "L" + digit: the "L" is a misread of "1"
        normalised = normalised.replace(Regex("""(\d)L(\d)"""), "\${1}1$2")
        // digit + "I" + digit: the "I" is a misread of "," (decimal separator)
        normalised = normalised.replace(Regex("""(\d)I(\d)"""), "$1,$2")
        return normalised
    }

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
