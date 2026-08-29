package com.example.vespatacho.ui

import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.vespatacho.data.GasReading
import info.appdev.charting.charts.LineChart
import info.appdev.charting.components.AxisBase
import info.appdev.charting.components.XAxis
import info.appdev.charting.components.YAxis
import info.appdev.charting.data.EntryFloat
import info.appdev.charting.data.LineData
import info.appdev.charting.data.LineDataSet
import info.appdev.charting.formatter.IAxisValueFormatter

/**
 * Cubic line chart showing average fuel consumption (l/100km) over time.
 *
 * Only readings that have both km and liter data are used. Consecutive such
 * readings are paired to compute consumption: (liter / km_delta) * 100.
 */
@Composable
fun FuelConsumptionChart(readings: List<GasReading>, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val points: List<Pair<Long, Float>> = remember(readings) {
        val sorted = readings
            .filter { it.km != null && it.liter != null }
            .sortedBy { it.timestamp }
        sorted.zipWithNext { prev, curr ->
            val kmDelta = (curr.km!! - prev.km!!).toFloat()
            if (kmDelta > 0) Pair(curr.km.toLong(), (curr.liter!!.toFloat() / kmDelta) * 100f)
            else null
        }.filterNotNull()
    }

    val chart = remember { LineChart(context) }

    AndroidView(
        factory = { chart },
        modifier = modifier.fillMaxSize(),
        update = { lineChart ->
            lineChart.description.isEnabled = false
            lineChart.legend.isEnabled = false
            lineChart.setTouchEnabled(true)
            lineChart.isDragEnabled = true
            lineChart.setScaleEnabled(true)
            lineChart.isPinchZoom = false
            lineChart.setDrawGridBackground(false)
            lineChart.axisRight.isEnabled = false

            lineChart.xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                isDrawGridLines = false
                granularity = 1f
                textSize = 12f
                labelRotationAngle = 45f
                valueFormatter = object : IAxisValueFormatter {
                    private val fmt = java.text.DecimalFormat("#,###", java.text.DecimalFormatSymbols(java.util.Locale.GERMAN))
                    override fun getFormattedValue(value: Float, axis: AxisBase?) =
                        "${fmt.format(value.toLong())} km"
                }
            }

            lineChart.axisLeft.apply {
                setLabelCount(5, false)
                setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                isDrawGridLines = true
                valueFormatter = object : IAxisValueFormatter {
                    override fun getFormattedValue(value: Float, axis: AxisBase?) =
                        "${"%.1f".format(value)} l"
                }
            }

            if (points.size < 2) {
                lineChart.data = null
                lineChart.setNoDataText("Nicht genug Daten für Verbrauchsdiagramm")
                lineChart.invalidate()
                return@AndroidView
            }

            val entries = points.map { (ts, consumption) ->
                EntryFloat(ts.toFloat(), consumption)
            }.toMutableList()

            if (lineChart.data != null && lineChart.lineData.dataSetCount > 0) {
                @Suppress("UNCHECKED_CAST")
                val ds = lineChart.lineData.getDataSetByIndex(0) as LineDataSet<EntryFloat>
                ds.entries = entries
                lineChart.lineData.notifyDataChanged()
                lineChart.notifyDataSetChanged()
            } else {
                val dataSet = LineDataSet(entries, "l/100km").apply {
                    lineMode = LineDataSet.Mode.CUBIC_BEZIER
                    cubicIntensity = 0.2f
                    lineWidth = 2f
                    color = Color.rgb(76, 175, 80)
                    setCircleColor(Color.rgb(56, 142, 60))
                    circleRadius = 4f
                    isDrawCircles = true
                    isDrawValues = false
                    isDrawFilled = true
                    fillColor = Color.rgb(76, 175, 80)
                    fillAlpha = 60
                }
                lineChart.data = LineData(dataSet)
                lineChart.animateX(800)
            }
            lineChart.invalidate()
        },
    )
}
