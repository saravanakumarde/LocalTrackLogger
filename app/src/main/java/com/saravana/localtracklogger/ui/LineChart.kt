package com.saravana.localtracklogger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Minimal line chart: [xs] and [ys] must have the same size; x starts at 0 and grows. */
@Composable
fun LineChart(
    title: String,
    xs: List<Double>,
    ys: List<Double>,
    formatY: (Double) -> String,
    xEndLabel: String,
    modifier: Modifier = Modifier
) {
    if (ys.size < 2 || xs.size != ys.size) return
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val minY = ys.min()
    val maxY = ys.max()
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text("${formatY(minY)} – ${formatY(maxY)}", style = MaterialTheme.typography.bodySmall)
        }
        Canvas(Modifier.fillMaxWidth().height(110.dp).padding(vertical = 4.dp)) {
            val spanY = if (maxY - minY > 1e-6) maxY - minY else 1.0
            val maxX = if (xs.last() > 1e-9) xs.last() else 1.0
            for (i in 0..2) {
                val y = size.height * i / 2f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            val path = Path()
            for (i in xs.indices) {
                val px = (xs[i] / maxX * size.width).toFloat()
                val py = (size.height - (ys[i] - minY) / spanY * size.height).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0", style = MaterialTheme.typography.bodySmall)
            Text(xEndLabel, style = MaterialTheme.typography.bodySmall)
        }
    }
}
