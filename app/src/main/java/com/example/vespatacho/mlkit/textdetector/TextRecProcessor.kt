package com.example.vespatacho.mlkit.textdetector

import android.content.Context
import com.example.vespatacho.camera.FuelDetector
import com.example.vespatacho.mlkit.GraphicOverlay
import com.example.vespatacho.mlkit.PreferenceUtils.shouldGroupRecognizedTextInBlocks
import com.example.vespatacho.mlkit.PreferenceUtils.shouldShowTextConfidence
import com.example.vespatacho.mlkit.PreferenceUtils.showLanguageTag
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognizerOptionsInterface
import timber.log.Timber

/** Processor for the text detector demo.  */
class TextRecProcessor(
    context: Context,
    textRecognizerOptions: TextRecognizerOptionsInterface,
    private val onPriceDetected: (String) -> Unit = {},
    private val onLiterDetected: (String) -> Unit = {},
) : VisionProcessorBase<Text?>(context) {
    private val textRecognizer: TextRecognizer = TextRecognition.getClient(textRecognizerOptions)
    private val shouldGroupRecognizedTextInBlocks: Boolean = shouldGroupRecognizedTextInBlocks(context)
    private val showLanguageTag: Boolean = showLanguageTag(context)
    private val showConfidence: Boolean = shouldShowTextConfidence(context)

    override fun stop() {
        super.stop()
        textRecognizer.close()
    }

    override fun detectInImage(image: InputImage?): Task<Text?> {
        return textRecognizer.process(image!!)
    }

    override fun onSuccess(results: Text?, graphicOverlay: GraphicOverlay) {
        if (results?.text?.isNotEmpty() == true) {
            Timber.d("On-device detection \n${results.text}")
            logExtrasForTesting(results)

            // Immediate autofill of "Preis (€)"/"Liter" straight from this live OCR pass,
            // without waiting for the throttled FuelDetector.detect(bitmap) analysis.
            val lines = results.textBlocks.flatMap { it.lines }
            lines.firstNotNullOfOrNull { line -> FuelDetector.extractPriceIfLineEndsWithPriceSuffix(line.text) }
                ?.let(onPriceDetected)
            FuelDetector.extractLiterFromLines(lines.map { it.text })?.let(onLiterDetected)

            graphicOverlay.add(
                TextGraphic(
                    graphicOverlay,
                    results,
                    shouldGroupRecognizedTextInBlocks,
                    showLanguageTag,
                    showConfidence
                )
            )
        }
    }

    override fun onFailure(e: Exception) {
        Timber.w("Text detection failed.$e")
    }

    companion object {

        private fun logExtrasForTesting(results: Text?) {
            if (results != null) {
                Timber.v("Detected text has : ${results.textBlocks.size} blocks")
                for (i in results.textBlocks.indices) {
                    val lines = results.textBlocks[i].lines
                    Timber.v(
                        String.format("Detected text block $i has ${lines.size} lines")
                    )
                    for (j in lines.indices) {
                        val elements = lines[j]!!.elements
                        Timber.v(String.format("Detected text line $j has ${elements.size} elements"))
                        for (k in elements.indices) {
                            val element = elements[k]
                            Timber.v(String.format("Detected text element $k says: ${element.text}"))
                            Timber.v("Detected text element $k has a bounding box:${element.boundingBox!!.flattenToString()}")
                            String.format("Expected corner point size is 4, get ${element.cornerPoints!!.size}")
                            for (point in element.cornerPoints!!) {
                                Timber.v("Corner point for element $k is located at: x - ${point.x}, y = ${point.y}")
                            }
                            String.format("Expected corner point size is 4, get ${element.cornerPoints!!.size}")
                            for (point in element.cornerPoints!!) {
                                Timber.v("Corner point for element $k is located at: x - ${point.x}, y = ${point.y}")
                            }
                        }
                    }
                }
            }
        }
    }
}
