package com.bu.paperclip.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.bu.paperclip.data.RunActivityDay
import com.bu.paperclip.ui.components.Dot
import com.bu.paperclip.ui.theme.LocalStatusColors
import androidx.compose.ui.res.stringResource
import com.bu.paperclip.R
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import com.bu.paperclip.util.appString

/**
 * 14-day stacked run chart: succeeded (bottom), failed, other (top).
 * Tap a bar to see that day's numbers; legend always shown since there are 3 series.
 */
@Composable
fun DashRunActivityChart(days: List<RunActivityDay>, modifier: Modifier = Modifier) {
    val status = LocalStatusColors.current
    val okColor = status.green
    val failColor = status.red
    val otherColor = status.gray.copy(alpha = 0.6f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val data = remember(days) { days.takeLast(14) }
    var selected by remember(data) { mutableIntStateOf(data.lastIndex) }

    Column(modifier) {
        val sel = data.getOrNull(selected)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (sel != null) dashDayLabel(sel.date, long = true) else stringResource(R.string.no_runs_yet),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            if (sel != null) {
                Text(
                    stringResource(R.string.x_ok_x_failed_x_other, sel.succeeded, sel.failed, sel.other + sel.recovered),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (data.isEmpty()) return@Column
        val max = (data.maxOfOrNull { it.succeeded + it.failed + it.other + it.recovered } ?: 0).coerceAtLeast(1)
        Canvas(
            Modifier.fillMaxWidth().height(120.dp).pointerInput(data) {
                detectTapGestures { pos ->
                    val slot = size.width / data.size.toFloat()
                    selected = (pos.x / slot).toInt().coerceIn(0, data.lastIndex)
                }
            },
        ) {
            val slot = size.width / data.size
            val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            val gap = 2.dp.toPx()
            // Recessive baseline + midline.
            drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            drawLine(gridColor.copy(alpha = 0.5f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
            data.forEachIndexed { i, d ->
                val x = i * slot + (slot - barW) / 2
                if (i == selected) {
                    drawRoundRect(highlight, Offset(i * slot + 1, 0f), Size(slot - 2, size.height), CornerRadius(6.dp.toPx()))
                }
                val parts = listOf(d.succeeded to okColor, d.failed to failColor, (d.other + d.recovered) to otherColor).filter { it.first > 0 }
                var yBottom = size.height
                parts.forEachIndexed { pi, (v, c) ->
                    val h = size.height * v / max
                    val top = yBottom - h
                    val isTop = pi == parts.lastIndex
                    dashBarSegment(c, x, top, barW, h, rounded = isTop)
                    // 2px surface gap between stacked segments.
                    if (!isTop) drawRect(surface, Offset(x, top - gap / 2), Size(barW, gap))
                    yBottom = top
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            data.forEachIndexed { i, d ->
                Text(
                    if (i % 2 == data.lastIndex % 2) dashDayLabel(d.date) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            DashLegend(okColor, stringResource(R.string.succeeded))
            DashLegend(failColor, stringResource(R.string.failed))
            DashLegend(otherColor, stringResource(R.string.other))
        }
    }
}

@Composable
private fun DashLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color, 8.dp)
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun DrawScope.dashBarSegment(color: Color, x: Float, top: Float, w: Float, h: Float, rounded: Boolean) {
    if (h <= 0f) return
    if (!rounded) {
        drawRect(color, Offset(x, top), Size(w, h))
        return
    }
    val r = minOf(4.dp.toPx(), h, w / 2)
    val path = Path().apply {
        moveTo(x, top + h)
        lineTo(x, top + r)
        quadraticTo(x, top, x + r, top)
        lineTo(x + w - r, top)
        quadraticTo(x + w, top, x + w, top + r)
        lineTo(x + w, top + h)
        close()
    }
    drawPath(path, color)
}

private val dashMonths = (1..12).map { Month.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

internal fun dashDayLabel(date: String, long: Boolean = false): String {
    val parts = date.take(10).split("-")
    if (parts.size != 3) return date
    val day = parts[2].trimStart('0')
    val month = parts[1].toIntOrNull()?.let { dashMonths.getOrNull(it - 1) } ?: ""
    return if (long) "$day $month" else day
}
