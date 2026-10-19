package com.oikos.fieldbook.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oikos.fieldbook.R
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

private val Track = Color.White.copy(alpha = 0.14f)
private val Orange = Color(0xFFFF6A00)
private val HikerColor = Color(0xFFF2F2F2)

/** Passadas completas (duas pernas) do inicio ao fim do contorno; inteiro para parar na pose do desenho. */
private const val STRIDES = 5

/** Caminhada com inicio e fim suaves; a passada acompanha a distancia, entao freia junto. */
private val WalkEasing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)

/**
 * Barra de carregamento da abertura: o trilheiro do Luan sobe e desce o contorno do
 * Itaguare (os dois desenhos dele, vetorizados por tools/splash) e o caminho andado se
 * enche de laranja sobre o contorno apagado, a partir de [running].
 */
@Composable
fun ItaguareLoadingBar(
    running: Boolean,
    durationMillis: Int,
    modifier: Modifier = Modifier,
    hikerHeight: Dp = 15.dp
) {
    val mountain = ImageVector.vectorResource(R.drawable.itaguare_contorno)
    val hikerVector = ImageVector.vectorResource(R.drawable.trilheiro)
    val contour = remember(mountain) { mountain.root.filterIsInstance<VectorPath>().first().pathData.toPath() }
    val measure = remember(contour) { PathMeasure().apply { setPath(contour, false) } }
    val hiker = remember(hikerVector) { Hiker(hikerVector) }
    val ridge = remember(measure) { Ridge(measure, mountain.viewportWidth) }
    val walked = remember { Path() }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(running) {
        if (running) progress.animateTo(1f, tween(durationMillis, easing = WalkEasing))
    }
    Canvas(modifier.aspectRatio(mountain.viewportWidth / mountain.viewportHeight)) {
        val factor = size.width / mountain.viewportWidth
        val strokeWidth = 3.dp.toPx()
        val stroke = Stroke(width = strokeWidth / factor, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val distance = measure.length * progress.value
        scale(factor, pivot = Offset.Zero) {
            drawPath(contour, Track, style = stroke)
            walked.reset()
            if (measure.getSegment(0f, distance, walked, true)) drawPath(walked, Orange, style = stroke)
        }
        val hipX = measure.getPosition(distance).x * factor
        val halfStroke = strokeWidth / 2f
        // Cada pe pisa na borda de cima do traco, na altura do contorno embaixo dele.
        drawHiker(hiker, hipX, { x -> ridge.topAt(x / factor) * factor - halfStroke }, hikerHeight.toPx(), 2f * PI.toFloat() * STRIDES * progress.value)
    }
}

/** As tres partes do trilheiro (res/drawable/trilheiro.xml) e o que a passada precisa delas. */
private class Hiker(vector: ImageVector) {
    val height = vector.viewportHeight
    val body: Path
    val nearLeg: Leg
    val farLeg: Leg

    class Leg(group: VectorGroup) {
        val path: Path
        val pivot = Offset(group.pivotX, group.pivotY)
        val points: FloatArray
        val footAngle: Float

        init {
            val nodes = group.filterIsInstance<VectorPath>().first().pathData
            path = nodes.toPath().apply { fillType = PathFillType.EvenOdd }
            val coords = ArrayList<Float>()
            for (node in nodes) {
                when (node) {
                    is PathNode.MoveTo -> { coords += node.x; coords += node.y }
                    is PathNode.LineTo -> { coords += node.x; coords += node.y }
                    else -> Unit
                }
            }
            points = coords.toFloatArray()
            // Direcao do pe: media dos vertices nos 90 px de baixo, como no gerador.
            var bottom = 0f
            for (i in 1 until points.size step 2) bottom = maxOf(bottom, points[i])
            var sx = 0f
            var sy = 0f
            var n = 0
            for (i in points.indices step 2) {
                if (points[i + 1] > bottom - 90f) { sx += points[i]; sy += points[i + 1]; n++ }
            }
            footAngle = atan2(sx / n - pivot.x, sy / n - pivot.y)
        }
    }

    init {
        val groups = vector.root.filterIsInstance<VectorGroup>()
        farLeg = Leg(groups.first { it.name == "perna_distante" })
        nearLeg = Leg(groups.first { it.name == "perna_proxima" })
        body = vector.root.filterIsInstance<VectorPath>().first { it.name == "corpo" }.pathData.toPath()
            .apply { fillType = PathFillType.EvenOdd }
    }

    /** Abertura entre os pes no desenho: o quanto cada perna gira para trocar de lugar. */
    val spread = nearLeg.footAngle - farLeg.footAngle

    /** Ponto do quadril, ancorado no caminho. */
    val hipX = (nearLeg.pivot.x + farLeg.pivot.x) / 2f
}

/**
 * Ponto mais alto do contorno em cada coluna x (unidades do desenho): o chao sob cada pe.
 * Nos degraus quase verticais perto do cume vale o nivel de cima.
 */
private class Ridge(measure: PathMeasure, width: Float) {
    private val top = FloatArray(width.toInt() + 2) { Float.MAX_VALUE }

    init {
        var d = 0f
        while (d <= measure.length) {
            val p = measure.getPosition(d)
            val i = p.x.roundToInt().coerceIn(0, top.lastIndex)
            top[i] = minOf(top[i], p.y)
            d += 0.5f
        }
        // Antes do inicio e depois do fim do contorno, repete a ponta mais proxima.
        val first = top.indexOfFirst { it != Float.MAX_VALUE }
        val last = top.indexOfLast { it != Float.MAX_VALUE }
        for (i in 0 until first) top[i] = top[first]
        for (i in last + 1 until top.size) top[i] = top[last]
    }

    fun topAt(x: Float): Float {
        val c = x.coerceIn(0f, (top.size - 1).toFloat())
        val i = c.toInt().coerceAtMost(top.size - 2)
        val f = c - i
        return top[i] * (1f - f) + top[i + 1] * f
    }
}

/**
 * Desenha o trilheiro com o quadril na coluna [hipX] (canvas) e o pe mais baixo encostado no
 * chao dado por [groundAt]. A perna proxima gira para tras e a distante para a frente ate
 * trocarem de lugar em meia passada; [phase] em radianos.
 */
private fun DrawScope.drawHiker(hiker: Hiker, hipX: Float, groundAt: (Float) -> Float, heightPx: Float, phase: Float) {
    val s = heightPx / hiker.height
    val swing = hiker.spread * (1f - cos(phase)) / 2f
    // Desce o corpo ate o primeiro pe tocar o chao.
    var offsetY = Float.MAX_VALUE
    for ((leg, angle) in listOf(hiker.nearLeg to swing, hiker.farLeg to -swing)) {
        val c = cos(angle)
        val sn = sin(angle)
        val p = leg.points
        for (i in p.indices step 2) {
            val dx = p[i] - leg.pivot.x
            val dy = p[i + 1] - leg.pivot.y
            val x = leg.pivot.x + dx * c - dy * sn
            val y = leg.pivot.y + dx * sn + dy * c
            offsetY = minOf(offsetY, groundAt(hipX + (x - hiker.hipX) * s) - y * s)
        }
    }
    val degrees = swing * 180f / PI.toFloat()
    translate(hipX - hiker.hipX * s, offsetY) {
        scale(s, pivot = Offset.Zero) {
            rotate(-degrees, hiker.farLeg.pivot) { drawPath(hiker.farLeg.path, HikerColor) }
            rotate(degrees, hiker.nearLeg.pivot) { drawPath(hiker.nearLeg.path, HikerColor) }
            drawPath(hiker.body, HikerColor)
        }
    }
}
