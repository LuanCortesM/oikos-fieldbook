package com.oikos.fieldbook.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/*
 * Graficos da tela de Analise, desenhados no Canvas, sem biblioteca. As cores
 * vem do tema do app: preto e laranja no escuro, branco e laranja no claro.
 */

/**
 * Barras verticais (registros por mes, por hora). O valor aparece em cima da
 * barra quando cabe; os rotulos do eixo pulam alguns se nao couberem todos.
 */
@Composable
fun AnalysisBarChart(values: List<Int>, labels: List<String>, description: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val valueStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurface, fontWeight = FontWeight.Bold)
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurface)
    Canvas(modifier.fillMaxWidth().height(190.dp).semantics { contentDescription = description }) {
        if (values.isEmpty()) return@Canvas
        val step = niceStep(values.max())
        val top = (ceil(values.max().coerceAtLeast(1) / step.toDouble()) * step).toFloat()
        val left = 30.dp.toPx()
        val right = 4.dp.toPx()
        val above = 18.dp.toPx()
        val below = 24.dp.toPx()
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

        val slot = plotWidth / values.size
        val barWidth = slot * 0.62f
        values.forEachIndexed { index, value ->
            if (value <= 0) return@forEachIndexed
            val x = left + index * slot + (slot - barWidth) / 2f
            drawRoundRect(
                colors.primary, topLeft = Offset(x, y(value.toFloat())), size = Size(barWidth, y(0f) - y(value.toFloat())),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
            val text = measurer.measure(value.toString(), valueStyle)
            if (text.size.width <= slot) {
                drawText(text, topLeft = Offset(x + barWidth / 2f - text.size.width / 2f, y(value.toFloat()) - text.size.height - 2.dp.toPx()))
            }
        }

        val measured = labels.map { measurer.measure(it, labelStyle) }
        val widest = measured.maxOfOrNull { it.size.width } ?: 0
        val every = ceil((widest + 4.dp.toPx()) / slot).toInt().coerceAtLeast(1)
        measured.forEachIndexed { index, text ->
            if (index < values.size && index % every == 0) {
                drawText(text, topLeft = Offset(left + index * slot + slot / 2f - text.size.width / 2f, size.height - below + 6.dp.toPx()))
            }
        }
    }
}

/**
 * Rank-abundancia (Whittaker): abundancia relativa de cada taxon, do mais
 * abundante ao mais raro, em escala logaritmica. Curva que cai devagar e
 * comunidade equilibrada; curva que despenca e uma ou poucas dominantes.
 */
@Composable
fun RankAbundanceChart(percents: List<Double>, description: String, background: Color, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    Canvas(modifier.fillMaxWidth().height(200.dp).semantics { contentDescription = description }) {
        val values = percents.filter { it > 0.0 }
        if (values.isEmpty()) return@Canvas
        val topPower = 2.0
        val bottomPower = min(floor(log10(values.min())), 1.0)
        val left = 44.dp.toPx()
        val right = 12.dp.toPx()
        val above = 10.dp.toPx()
        val below = 22.dp.toPx()
        val plotWidth = size.width - left - right
        val plotHeight = size.height - above - below
        fun y(percent: Double) = (above + plotHeight * (topPower - log10(percent)) / (topPower - bottomPower)).toFloat()

        var power = topPower
        while (power >= bottomPower) {
            val percent = 10.0.pow(power)
            drawLine(colors.onSurface.copy(alpha = 0.12f), Offset(left, y(percent)), Offset(size.width - right, y(percent)), strokeWidth = 1.dp.toPx())
            val label = if (power >= 0) "${percent.toLong()}%" else "${"%.${(-power).toInt()}f".format(percent)}%"
            val text = measurer.measure(label, axisStyle)
            drawText(text, topLeft = Offset(left - 8.dp.toPx() - text.size.width, y(percent) - text.size.height / 2f))
            power -= 1.0
        }

        val xs = values.indices.map { if (values.size == 1) left + plotWidth / 2f else left + it * plotWidth / (values.size - 1) }
        val line = Path().apply {
            values.forEachIndexed { index, value -> if (index == 0) moveTo(xs[index], y(value)) else lineTo(xs[index], y(value)) }
        }
        drawPath(line, colors.primary, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val radius = (if (values.size <= 30) 4.5 else 2.5).dp.toPx()
        values.forEachIndexed { index, value ->
            drawCircle(background, radius + 1.5.dp.toPx(), Offset(xs[index], y(value)))
            drawCircle(colors.primary, radius, Offset(xs[index], y(value)))
        }
        val first = measurer.measure("1", axisStyle)
        drawText(first, topLeft = Offset(xs.first() - first.size.width / 2f, size.height - below + 6.dp.toPx()))
        if (values.size > 1) {
            val last = measurer.measure(values.size.toString(), axisStyle)
            drawText(last, topLeft = Offset(xs.last() - last.size.width / 2f, size.height - below + 6.dp.toPx()))
        }
    }
}

/**
 * Similaridade entre expedicoes: metade de baixo da matriz, com as
 * expedicoes numeradas na ordem das datas. Quanto mais laranja, mais
 * parecidas; celula cinza com traco e par sem valor.
 */
@Composable
fun SimilarityHeatmap(values: List<List<Double?>>, format: (Double) -> String, description: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val count = values.size
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val cellStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
    Canvas(modifier.fillMaxWidth().height(((count - 1).coerceAtLeast(1) * 44 + 24).coerceAtMost(330).dp).semantics { contentDescription = description }) {
        if (count < 2) return@Canvas
        val axis = 24.dp.toPx()
        val cell = min((size.width - axis) / (count - 1), (size.height - axis) / (count - 1))
        val gap = max(1.dp.toPx(), cell * 0.06f)
        val showValues = cell >= 34.dp.toPx()
        // Centralizada no cartao.
        val start = (size.width - axis - cell * (count - 1)) / 2f
        val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        for (row in 1 until count) {
            val label = measurer.measure((row + 1).toString(), axisStyle)
            drawText(label, topLeft = Offset(start + axis / 2f - label.size.width / 2f, (row - 1) * cell + cell / 2f - label.size.height / 2f))
            for (column in 0 until row) {
                val value = values[row][column]
                val topLeft = Offset(start + axis + column * cell + gap / 2f, (row - 1) * cell + gap / 2f)
                val cellSize = Size(cell - gap, cell - gap)
                // Fundo neutro e o laranja por cima, na forca do valor: 0 fica cinza, 1 fica laranja cheio.
                drawRoundRect(colors.onSurface.copy(alpha = 0.08f), topLeft = topLeft, size = cellSize, cornerRadius = corner)
                if (value != null && value > 0.0) {
                    drawRoundRect(colors.primary.copy(alpha = value.toFloat().coerceIn(0f, 1f)), topLeft = topLeft, size = cellSize, cornerRadius = corner)
                }
                if (showValues) {
                    val strong = value != null && value > 0.55
                    val style = cellStyle.copy(color = if (strong) colors.onPrimary else colors.onSurface)
                    val text = measurer.measure(value?.let(format) ?: "—", style)
                    drawText(text, topLeft = Offset(topLeft.x + (cell - gap) / 2f - text.size.width / 2f, topLeft.y + (cell - gap) / 2f - text.size.height / 2f))
                }
            }
        }
        for (column in 0 until count - 1) {
            val label = measurer.measure((column + 1).toString(), axisStyle)
            drawText(label, topLeft = Offset(start + axis + column * cell + cell / 2f - label.size.width / 2f, (count - 1) * cell + 4.dp.toPx()))
        }
    }
}

/** Passo das linhas de grade: 1, 2, 5, 10, 20, 50... com no maximo 5 faixas. */
internal fun niceStep(max: Int): Int {
    var magnitude = 1
    while (true) {
        for (factor in intArrayOf(1, 2, 5)) {
            val step = factor * magnitude
            if (max <= step * 5) return step
        }
        magnitude *= 10
    }
}
