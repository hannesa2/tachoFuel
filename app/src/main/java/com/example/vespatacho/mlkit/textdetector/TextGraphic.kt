package com.example.vespatacho.mlkit.textdetector

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.vespatacho.mlkit.GraphicOverlay
import com.example.vespatacho.mlkit.GraphicOverlay.Graphic
import com.google.mlkit.vision.text.Text
import timber.log.Timber
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Graphic instance for rendering TextBlock position, size, and ID within an associated graphic
 * overlay view.
 */
class TextGraphic internal constructor(
    overlay: GraphicOverlay,
    private val text: Text,
    private val shouldGroupTextInBlocks: Boolean,
    private val showLanguageTag: Boolean,
    private val showConfidence: Boolean
) : Graphic(overlay) {
    private val rectPaint: Paint = Paint()
    private val textPaint: Paint
    private val labelPaint: Paint

    init {
        rectPaint.color = MARKER_COLOR
        rectPaint.style = Paint.Style.STROKE
        rectPaint.strokeWidth = STROKE_WIDTH

        textPaint = Paint()
        textPaint.color = TEXT_COLOR
        textPaint.textSize = TEXT_SIZE

        labelPaint = Paint()
        labelPaint.color = MARKER_COLOR
        labelPaint.style = Paint.Style.FILL
        // Redraw the overlay, as this graphic has been added.
        postInvalidate()
    }

    /** Draws the text block annotations for position, size, and raw value on the supplied canvas.  */
    override fun draw(canvas: Canvas) {
        if (text.text.isNotEmpty()) {
            Timber.v("Text is: ${text.text}")
            for (textBlock in text.textBlocks) {
                // Renders the text at the bottom of the box.
                Timber.v("TextBlock text is: ${textBlock.text}")
                Timber.v("TextBlock boundingbox is: ${textBlock.boundingBox}")
                Timber.v("TextBlock cornerpoint is: ${textBlock.cornerPoints.contentToString()}")
                if (shouldGroupTextInBlocks) {
                    val text =
                        if (showLanguageTag) String.format(
                            TEXT_WITH_LANGUAGE_TAG_FORMAT,
                            textBlock.recognizedLanguage,
                            textBlock.text
                        ) else
                            textBlock.text
                    drawText(
                        text,
                        RectF(textBlock.boundingBox),
                        TEXT_SIZE * textBlock.lines.size + 2 * STROKE_WIDTH,
                        canvas
                    )
                } else {
                    for (line in textBlock.lines) {
                        Timber.v("Line text is: ${line.text}")
                        Timber.v("Line boundingbox is: ${line.boundingBox}")
                        Timber.v("Line cornerpoint is: ${line.cornerPoints.contentToString()}")
                        Timber.v("Line confidence is: ${line.confidence}")
                        Timber.v("Line angle is: ${line.angle}")
                        var text =
                            if (showLanguageTag) String.format(
                                TEXT_WITH_LANGUAGE_TAG_FORMAT, line.recognizedLanguage, line.text
                            ) else
                                line.text
                        text =
                            if (showConfidence) String.format(Locale.US, "%s (%.2f)", text, line.confidence) else
                                text
                        drawText(text, RectF(line.boundingBox), TEXT_SIZE + 2 * STROKE_WIDTH, canvas)

                        for (element in line.elements) {
                            Timber.v("Element text is: ${element.text}")
                            Timber.v("Element boundingbox is: ${element.boundingBox}")
                            Timber.v("Element cornerpoint is: ${element.cornerPoints.contentToString()}")
                            Timber.v("Element language is: ${element.recognizedLanguage}")
                            Timber.v("Element confidence is: ${element.confidence}")
                            Timber.v("Element angle is: ${element.angle}")
                            for (symbol in element.symbols) {
                                Timber.v("Symbol text is: ${symbol.text}")
                                Timber.v("Symbol boundingbox is: ${symbol.boundingBox}")
                                Timber.v("Symbol cornerpoint is: ${symbol.cornerPoints.contentToString()}")
                                Timber.v("Symbol confidence is: ${symbol.confidence}")
                                Timber.v("Symbol angle is: ${symbol.angle}")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun drawText(text: String, rect: RectF, textHeight: Float, canvas: Canvas) {
        // If the image is flipped, the left will be translated to right, and the right to left.
        val x0 = translateX(rect.left)
        val x1 = translateX(rect.right)
        rect.left = min(x0, x1)
        rect.right = max(x0, x1)
        rect.top = translateY(rect.top)
        rect.bottom = translateY(rect.bottom)
        canvas.drawRect(rect, rectPaint)
        val textWidth = textPaint.measureText(text)
        canvas.drawRect(
            rect.left - STROKE_WIDTH,
            rect.top - textHeight,
            rect.left + textWidth + 2 * STROKE_WIDTH,
            rect.top,
            labelPaint
        )
        // Renders the text at the bottom of the box.
        canvas.drawText(text, rect.left, rect.top - STROKE_WIDTH, textPaint)
    }

    companion object {
        private const val TEXT_WITH_LANGUAGE_TAG_FORMAT = "%s:%s"

        private const val TEXT_COLOR = Color.BLACK
        private const val MARKER_COLOR = Color.WHITE
        private const val TEXT_SIZE = 54.0f
        private const val STROKE_WIDTH = 4.0f
    }
}
