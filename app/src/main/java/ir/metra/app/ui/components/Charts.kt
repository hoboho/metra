package ir.metra.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.metra.app.R
import ir.metra.app.core.format.PersianDigits

/** One point on a chart. */
data class ChartPoint(
    val label: String,
    val value: Double,
    /** Optional secondary series drawn as a line over the bars. */
    val secondaryValue: Double? = null,
)

/**
 * Lightweight charts drawn on [Canvas].
 *
 * No charting library is pulled in: these two chart types cover every screen in
 * the app, they render instantly on low-end hardware, and they avoid a runtime
 * dependency for what is essentially a bar and a polyline.
 */

@Composable
fun BarChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    valueFormatter: (Double) -> String = { PersianDigits.toPersian(it.toLong().toString()) },
    highlightLast: Boolean = false,
    threshold: Double? = null,
    thresholdLabel: String? = null,
    barColor: Color = MaterialTheme.colorScheme.primary,
    thresholdColor: Color = MaterialTheme.colorScheme.tertiary,
) {
    if (points.isEmpty()) {
        EmptyChart(modifier)
        return
    }
    val maxValue = (points.maxOf { it.value }.coerceAtLeast(1.0))
        .let { base -> threshold?.let { maxOf(base, it) } ?: base }
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                val chartHeight = size.height
                val chartWidth = size.width
                val slot = chartWidth / points.size
                val barWidth = slot * 0.62f

                // Horizontal grid lines at quarters of the maximum.
                for (step in 0..4) {
                    val y = chartHeight - (chartHeight * step / 4f)
                    drawLine(gridColor, Offset(0f, y), Offset(chartWidth, y), strokeWidth = 1f)
                }

                points.forEachIndexed { index, point ->
                    val fraction = (point.value / maxValue).toFloat().coerceIn(0f, 1f)
                    val barHeight = chartHeight * fraction
                    val left = index * slot + (slot - barWidth) / 2f
                    val color = if (highlightLast && index == points.lastIndex) {
                        barColor
                    } else {
                        barColor.copy(alpha = 0.72f)
                    }
                    drawRect(
                        color = color,
                        topLeft = Offset(left, chartHeight - barHeight),
                        size = Size(barWidth, barHeight),
                    )
                }

                threshold?.let { limit ->
                    val y = chartHeight - (chartHeight * (limit / maxValue).toFloat()).coerceIn(0f, 1f)
                    drawLine(thresholdColor, Offset(0f, y), Offset(chartWidth, y), strokeWidth = 2f)
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEach { point ->
                Text(
                    text = point.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
        thresholdLabel?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = thresholdColor,
            )
        }
    }
}

@Composable
fun LineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillAlpha: Float = 0.14f,
) {
    if (points.size < 2) {
        EmptyChart(modifier)
        return
    }
    val maxValue = points.maxOf { it.value }.coerceAtLeast(1.0)
    val minValue = points.minOf { it.value }.coerceAtMost(0.0)
    val span = (maxValue - minValue).coerceAtLeast(1.0)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) {
            val stepX = size.width / (points.size - 1)
            fun offsetFor(index: Int): Offset {
                val fraction = ((points[index].value - minValue) / span).toFloat()
                return Offset(index * stepX, size.height - size.height * fraction)
            }

            for (step in 0..4) {
                val y = size.height - (size.height * step / 4f)
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }

            val linePath = Path().apply {
                moveTo(offsetFor(0).x, offsetFor(0).y)
                for (index in 1 until points.size) lineTo(offsetFor(index).x, offsetFor(index).y)
            }
            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(fillPath, lineColor.copy(alpha = fillAlpha))
            drawPath(linePath, lineColor, style = Stroke(width = 3f))
            for (index in points.indices) {
                val offset = offsetFor(index)
                drawCircle(lineColor, radius = 4f, center = offset)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEachIndexed { index, point ->
                if (index % maxOf(1, points.size / 6) == 0 || index == points.lastIndex) {
                    Text(
                        text = point.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyChart(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.chart_no_data),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Horizontal two-value comparison bar used by the monthly comparison screen. */
@Composable
fun ComparisonBar(
    label: String,
    firstValue: Double,
    secondValue: Double,
    firstLabel: String,
    secondLabel: String,
    valueFormatter: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val max = maxOf(firstValue, secondValue, 1.0)
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        ComparisonRow(firstLabel, firstValue, max, valueFormatter, MaterialTheme.colorScheme.primary)
        ComparisonRow(secondLabel, secondValue, max, valueFormatter, MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    value: Double,
    max: Double,
    valueFormatter: (Double) -> String,
    color: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(end = 4.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(12.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                drawRect(color.copy(alpha = 0.18f), size = Size(size.width, size.height))
                drawRect(color, size = Size(size.width * (value / max).toFloat(), size.height))
            }
        }
        Text(
            text = valueFormatter(value),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
