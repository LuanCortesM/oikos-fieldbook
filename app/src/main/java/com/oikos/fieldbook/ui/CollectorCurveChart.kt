package com.oikos.fieldbook.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * Curva do coletor: taxons acumulados a cada expedicao, na ordem das datas.
 *
 * Desenhada no Canvas, sem biblioteca de grafico. As cores vem do tema do
 * app, entao fica preta e laranja no tema escuro e branca e laranja no claro.
 * [labels] e o rotulo de cada ponto no eixo (pode ter duas linhas); com
 * muitas expedicoes so parte deles aparece, para nao encavalarem.
 * [background] e a cor do cartao, que contorna cada ponto.
 */
@Composable
fun CollectorCurveChart(values: List<Int>, labels: List<String>, description: String, background: Color, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val valueStyle = MaterialTheme.typography.labelLarge.copy(color = colors.onSurface, fontWeight = FontWeight.Bold)
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurface)
    Canvas(modifier.fillMaxWidth().height(230.dp).semantics { contentDescription = description }) {
        if (values.isEmpty()) return@Canvas
        val step = niceStep(values.max())
        val top = (ceil(values.max().coerceAtLeast(1) / step.toDouble()) * step).toFloat()
        val left = 30.dp.toPx()
        val right = 12.dp.toPx()
        val above = 24.dp.toPx()
        val below = 40.dp.toPx()
        val plotWidth = size.width - left - right
        val plotHeight = size.height - above - below
        fun y(value: Float) = above + plotHeight * (1f - value / top)

        var tick = 0
        while (tick <= top) {
            drawLine(colors.onSurface.copy(alpha = 0.12f), Offset(left, y(tick.toFloat())), Offset(size.width - right, y(tick.toFloat())), strokeWidth = 1.dp.toPx())
            val text = measurer.measure(tick.toString(), axisStyle)
            drawText(text, topLeft = Offset(left - 8.dp.toPx() - text.size.width, y(tick.toFloat()) - text.size.height / 2f))
            tick += step
        }

        val xs = values.indices.map { left + (it + 0.5f) * plotWidth / values.size }
        val area = Path().apply {
            moveTo(xs.first(), y(0f))
            values.forEachIndexed { index, value -> lineTo(xs[index], y(value.toFloat())) }
            lineTo(xs.last(), y(0f))
            close()
        }
        drawPath(area, colors.primary.copy(alpha = 0.14f))
        val line = Path().apply {
            values.forEachIndexed { index, value ->
                if (index == 0) moveTo(xs[index], y(value.toFloat())) else lineTo(xs[index], y(value.toFloat()))
            }
        }
        drawPath(line, colors.primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Valor em cada ponto ate 12 expedicoes; com mais, so no ultimo.
        val radius = (if (values.size <= 24) 5 else 3).dp.toPx()
        values.forEachIndexed { index, value ->
            val center = Offset(xs[index], y(value.toFloat()))
            drawCircle(background, radius + 2.dp.toPx(), center)
            drawCircle(colors.primary, radius, center)
            if (values.size <= 12 || index == values.lastIndex) {
                val text = measurer.measure(value.toString(), valueStyle)
                drawText(text, topLeft = Offset(center.x - text.size.width / 2f, center.y - radius - 4.dp.toPx() - text.size.height))
            }
        }

        val measured = labels.map { measurer.measure(it, labelStyle) }
        val widest = measured.maxOfOrNull { it.size.width } ?: 0
        val every = ceil((widest + 6.dp.toPx()) * values.size / plotWidth).toInt().coerceAtLeast(1)
        measured.forEachIndexed { index, text ->
            if (index < xs.size && index % every == 0) {
                drawText(text, topLeft = Offset(xs[index] - text.size.width / 2f, size.height - below + 8.dp.toPx()))
            }
        }
    }
}
