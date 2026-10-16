package com.shibi.musicplayer.ui.background

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

internal class SceneScratch {
    val path = Path()
}

internal class DriftField(
    val count: Int,
    seed: Int,
) {
    val x = FloatArray(count)
    val y = FloatArray(count)
    val s = FloatArray(count)
    val speed = FloatArray(count)
    val spin = FloatArray(count)

    init {
        val random = Random(seed.toLong())
        for (i in 0 until count) {
            x[i] = random.nextFloat()
            y[i] = random.nextFloat()
            s[i] = 0.018f + random.nextFloat() * 0.045f
            speed[i] = 0.35f + random.nextFloat() * 0.7f
            spin[i] = random.nextFloat()
        }
    }
}

@Composable
internal fun SceneCanvas(
    sceneId: String,
    colors: List<Long>,
    phase: Float,
    modifier: Modifier = Modifier,
) {
    val scratch = remember { SceneScratch() }
    val field = remember(sceneId) { DriftField(12, sceneId.hashCode()) }
    Canvas(modifier = modifier) {
        drawScene(sceneId, colors, phase, scratch, field)
    }
}

internal fun DrawScope.drawScene(
    sceneId: String,
    colors: List<Long>,
    phase: Float,
    scratch: SceneScratch,
    field: DriftField,
) {
    when (sceneId) {
        "waves" -> drawWaves(colors, phase, scratch)
        "bubbles" -> drawBubbles(colors, phase, field)
        "clouds" -> drawClouds(colors, phase, field)
        "sparkles" -> drawSparkles(colors, phase, field)
        "petals" -> drawPetals(colors, phase, field, scratch)
        "aurora" -> drawAurora(colors, phase)
        else -> drawRect(color = colors.colorAt(0, 0xFFF5EDE0))
    }
}

private fun DrawScope.drawWaves(colors: List<Long>, phase: Float, scratch: SceneScratch) {
    drawRect(colors.colorAt(0, 0xFFB8E4FF))
    val water = colors.colorAt(1, 0xFF7EC8E3)
    val foam = colors.colorAt(2, 0xFFE8F7FF)
    val w = size.width
    val h = size.height
    for (band in 0 until 3) {
        val path = scratch.path
        path.rewind()
        val base = h * (0.42f + band * 0.16f)
        val amp = h * (0.045f + band * 0.012f)
        val shift = phase * 2f * PI.toFloat() + band * 0.9f
        path.moveTo(0f, h)
        path.lineTo(0f, base)
        var x = 0f
        while (x <= w) {
            val y = base + sin(x / w * 2.4f * PI.toFloat() + shift) * amp
            path.lineTo(x, y)
            x += w / 16f
        }
        path.lineTo(w, h)
        path.close()
        drawPath(path, water.copy(alpha = 0.42f + band * 0.12f))
        if (band == 0) {
            drawPath(path, foam.copy(alpha = 0.35f), style = Stroke(width = h * 0.012f))
        }
    }
}

private fun DrawScope.drawBubbles(colors: List<Long>, phase: Float, field: DriftField) {
    drawRect(colors.colorAt(0, 0xFFBFE8F5))
    val bubble = colors.colorAt(1, 0xFFE8F7FF)
    val shine = colors.colorAt(2, 0xFFF8E0F0)
    val w = size.width
    val h = size.height
    for (i in 0 until field.count) {
        val rise = (field.y[i] - phase * field.speed[i]).let { t ->
            t - kotlin.math.floor(t.toDouble()).toFloat()
        }
        val cx = field.x[i] * w
        val cy = rise * h
        val r = field.s[i] * min(w, h) * 2.1f
        drawCircle(bubble.copy(alpha = 0.55f), r, Offset(cx, cy))
        drawCircle(shine.copy(alpha = 0.7f), r * 0.28f, Offset(cx - r * 0.28f, cy - r * 0.28f))
    }
}

private fun DrawScope.drawClouds(colors: List<Long>, phase: Float, field: DriftField) {
    drawRect(colors.colorAt(0, 0xFFC5E4F7))
    val cloud = colors.colorAt(1, 0xFFFFFFF6)
    val blush = colors.colorAt(2, 0xFFF8C8D4)
    val w = size.width
    val h = size.height
    val count = min(6, field.count)
    for (i in 0 until count) {
        val drift = (field.x[i] + phase * 0.22f * field.speed[i]).let { t ->
            t - kotlin.math.floor(t.toDouble()).toFloat()
        }
        val cx = drift * w
        val cy = (0.12f + field.y[i] * 0.28f) * h
        val r = field.s[i] * min(w, h) * 3.4f
        drawCircle(cloud.copy(alpha = 0.88f), r, Offset(cx, cy))
        drawCircle(cloud.copy(alpha = 0.88f), r * 0.78f, Offset(cx - r * 0.7f, cy + r * 0.12f))
        drawCircle(cloud.copy(alpha = 0.88f), r * 0.7f, Offset(cx + r * 0.62f, cy + r * 0.16f))
        drawCircle(blush.copy(alpha = 0.28f), r * 0.35f, Offset(cx + r * 0.2f, cy + r * 0.2f))
    }
}

private fun DrawScope.drawSparkles(colors: List<Long>, phase: Float, field: DriftField) {
    drawRect(colors.colorAt(0, 0xFFE6D0F0))
    val star = colors.colorAt(1, 0xFFFFFFF2)
    val glow = colors.colorAt(2, 0xFFF8C8E0)
    val w = size.width
    val h = size.height
    for (i in 0 until field.count) {
        val twinkle = 0.25f + 0.75f * abs(sin((phase + field.spin[i]) * 2f * PI.toFloat()))
        val cx = field.x[i] * w
        val cy = field.y[i] * h
        val r = field.s[i] * min(w, h) * 1.4f * (0.6f + twinkle)
        drawCircle(glow.copy(alpha = 0.22f * twinkle), r * 2.4f, Offset(cx, cy))
        drawCircle(star.copy(alpha = 0.55f + 0.4f * twinkle), r, Offset(cx, cy))
    }
}

private fun DrawScope.drawPetals(
    colors: List<Long>,
    phase: Float,
    field: DriftField,
    scratch: SceneScratch,
) {
    drawRect(colors.colorAt(0, 0xFFF5EDE0))
    val petal = colors.colorAt(1, 0xFFF0C4D0)
    val stem = colors.colorAt(2, 0xFFC5D9C3)
    val w = size.width
    val h = size.height
    val unit = min(w, h)
    for (i in 0 until field.count) {
        val fall = (field.y[i] + phase * field.speed[i]).let { t ->
            t - kotlin.math.floor(t.toDouble()).toFloat()
        }
        val sway = sin((phase + field.spin[i]) * 2f * PI.toFloat()) * unit * 0.04f
        val cx = field.x[i] * w + sway
        val cy = fall * h
        val r = field.s[i] * unit * 1.8f
        val path = scratch.path
        path.rewind()
        path.moveTo(cx, cy - r)
        path.quadraticTo(cx + r, cy, cx, cy + r)
        path.quadraticTo(cx - r, cy, cx, cy - r)
        drawPath(path, petal.copy(alpha = 0.78f))
        drawCircle(stem.copy(alpha = 0.45f), r * 0.18f, Offset(cx, cy))
    }
}

private fun DrawScope.drawAurora(colors: List<Long>, phase: Float) {
    val night = colors.colorAt(0, 0xFFD8E0F8)
    val rose = colors.colorAt(1, 0xFFF0C8E0)
    val mint = colors.colorAt(2, 0xFFC8EDE0)
    val lilac = colors.colorAt(3, 0xFFDCC8F0)
    drawRect(night)
    val w = size.width
    val h = size.height
    val shift = sin(phase * 2f * PI.toFloat())
    val band = w * 0.42f
    val x0 = w * (0.08f + 0.06f * shift)
    drawRect(rose.copy(alpha = 0.36f), topLeft = Offset(x0, 0f), size = Size(band, h))
    drawRect(mint.copy(alpha = 0.28f), topLeft = Offset(x0 + band * 0.45f, 0f), size = Size(band, h))
    drawRect(lilac.copy(alpha = 0.32f), topLeft = Offset(x0 + band * 0.85f, 0f), size = Size(band * 0.8f, h))
}

private fun List<Long>.colorAt(index: Int, fallback: Long): Color {
    val value = getOrNull(index) ?: fallback
    return value.toComposeColor()
}
