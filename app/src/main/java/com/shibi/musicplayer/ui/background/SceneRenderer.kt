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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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
        "unicorn" -> drawUnicorn(colors, phase, field, scratch)
        "cat" -> drawCat(colors, phase, field, scratch)
        "puppy" -> drawPuppy(colors, phase, scratch)
        "bunnies" -> drawBunnies(colors, phase)
        "whale" -> drawWhale(colors, phase, field, scratch)
        "panda" -> drawPanda(colors, phase)
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

private fun DrawScope.drawUnicorn(
    colors: List<Long>,
    phase: Float,
    field: DriftField,
    scratch: SceneScratch,
) {
    drawClouds(colors, phase, field)
    val coat = colors.colorAt(1, 0xFFFFFFF4)
    val mane = colors.colorAt(2, 0xFFF0C8E0)
    val star = colors.colorAt(3, 0xFFF7E6A1)
    val compact = isCompact()
    val unit = min(size.width, size.height)
    val scale = if (compact) unit * 0.30f else unit * 0.14f
    val origin = if (compact) {
        Offset(size.width * 0.58f, size.height * 0.52f)
    } else {
        Offset(size.width * 0.82f, size.height * 0.18f)
    }
    val bob = sin(phase * 2f * PI.toFloat()) * scale * 0.12f
    translate(origin.x, origin.y + bob) {
        drawCircle(coat, scale, Offset(0f, 0f))
        drawCircle(coat, scale * 0.72f, Offset(scale * 0.85f, scale * 0.1f))
        drawCircle(mane, scale * 0.22f, Offset(-scale * 0.35f, -scale * 0.15f))
        drawCircle(mane, scale * 0.18f, Offset(-scale * 0.1f, -scale * 0.42f))
        val horn = scratch.path
        horn.rewind()
        horn.moveTo(scale * 0.7f, -scale * 0.35f)
        horn.lineTo(scale * 1.15f, -scale * 1.05f)
        horn.lineTo(scale * 0.95f, -scale * 0.22f)
        horn.close()
        drawPath(horn, star)
        drawCircle(Color(0xFF3A3450), scale * 0.06f, Offset(scale * 1.05f, 0f))
        drawCircle(mane, scale * 0.08f, Offset(scale * 1.12f, scale * 0.18f))
    }
    val twinkle = 0.4f + 0.6f * abs(sin(phase * 2f * PI.toFloat()))
    drawCircle(star.copy(alpha = 0.55f * twinkle), scale * 0.12f, Offset(origin.x - scale * 1.6f, origin.y - scale * 0.8f))
    drawCircle(star.copy(alpha = 0.4f * twinkle), scale * 0.08f, Offset(origin.x - scale * 0.4f, origin.y - scale * 1.4f))
}

private fun DrawScope.drawCat(
    colors: List<Long>,
    phase: Float,
    field: DriftField,
    scratch: SceneScratch,
) {
    drawRect(colors.colorAt(0, 0xFFF5EDE0))
    val fur = colors.colorAt(1, 0xFFE8D0C0)
    val bubble = colors.colorAt(2, 0xFFC5E4F7)
    val nose = colors.colorAt(3, 0xFFF0C4D0)
    val compact = isCompact()
    val unit = min(size.width, size.height)
    val scale = if (compact) unit * 0.28f else unit * 0.13f
    val origin = if (compact) {
        Offset(size.width * 0.42f, size.height * 0.58f)
    } else {
        Offset(size.width * 0.14f, size.height * 0.78f)
    }
    val breathe = 1f + 0.04f * sin(phase * 2f * PI.toFloat())
    translate(origin.x, origin.y) {
        drawOval(fur, Offset(-scale * 0.9f, -scale * 0.15f), Size(scale * 1.8f * breathe, scale * 1.05f))
        drawCircle(fur, scale * 0.55f, Offset(scale * 0.7f, -scale * 0.15f))
        val ear = scratch.path
        ear.rewind()
        ear.moveTo(scale * 0.4f, -scale * 0.55f)
        ear.lineTo(scale * 0.55f, -scale * 1.05f)
        ear.lineTo(scale * 0.85f, -scale * 0.4f)
        ear.close()
        drawPath(ear, fur)
        ear.rewind()
        ear.moveTo(scale * 0.75f, -scale * 0.5f)
        ear.lineTo(scale * 1.05f, -scale * 0.95f)
        ear.lineTo(scale * 1.15f, -scale * 0.28f)
        ear.close()
        drawPath(ear, fur)
        drawCircle(Color(0xFF3A3450), scale * 0.06f, Offset(scale * 0.55f, -scale * 0.2f))
        drawCircle(Color(0xFF3A3450), scale * 0.06f, Offset(scale * 0.88f, -scale * 0.18f))
        drawCircle(nose, scale * 0.07f, Offset(scale * 0.78f, -scale * 0.02f))
    }
    val bubbleY = origin.y - scale * 1.1f - sin(phase * 2f * PI.toFloat()) * scale * 0.35f
    drawCircle(bubble.copy(alpha = 0.7f), scale * 0.28f, Offset(origin.x + scale * 1.6f, bubbleY))
    drawCircle(Color.White.copy(alpha = 0.7f), scale * 0.08f, Offset(origin.x + scale * 1.5f, bubbleY - scale * 0.08f))
    if (!compact) {
        for (i in 0 until 3) {
            val rise = (field.y[i] - phase * 0.4f).let { t -> t - kotlin.math.floor(t.toDouble()).toFloat() }
            drawCircle(
                bubble.copy(alpha = 0.35f),
                scale * 0.12f,
                Offset(size.width * (0.72f + field.x[i] * 0.18f), rise * size.height),
            )
        }
    }
}

private fun DrawScope.drawPuppy(colors: List<Long>, phase: Float, scratch: SceneScratch) {
    drawRect(colors.colorAt(0, 0xFFC5D9C3))
    val fur = colors.colorAt(1, 0xFFF8D4B8)
    val ear = colors.colorAt(2, 0xFFE0C8D8)
    val tongue = colors.colorAt(3, 0xFFF5C4B8)
    val compact = isCompact()
    val unit = min(size.width, size.height)
    val scale = if (compact) unit * 0.28f else unit * 0.13f
    val origin = if (compact) {
        Offset(size.width * 0.50f, size.height * 0.58f)
    } else {
        Offset(size.width * 0.16f, size.height * 0.8f)
    }
    val wag = sin(phase * 2f * PI.toFloat()) * 22f
    translate(origin.x, origin.y) {
        drawOval(fur, Offset(-scale * 0.85f, -scale * 0.2f), Size(scale * 1.7f, scale * 1.1f))
        drawCircle(fur, scale * 0.52f, Offset(scale * 0.75f, -scale * 0.2f))
        drawOval(ear, Offset(scale * 0.15f, -scale * 0.85f), Size(scale * 0.42f, scale * 0.7f))
        drawOval(ear, Offset(scale * 0.72f, -scale * 0.78f), Size(scale * 0.38f, scale * 0.62f))
        drawCircle(Color(0xFF3A3450), scale * 0.06f, Offset(scale * 0.62f, -scale * 0.25f))
        drawCircle(Color(0xFF3A3450), scale * 0.06f, Offset(scale * 0.92f, -scale * 0.22f))
        drawCircle(tongue, scale * 0.09f, Offset(scale * 0.82f, scale * 0.02f))
        rotate(wag, Offset(-scale * 0.7f, scale * 0.15f)) {
            drawOval(fur, Offset(-scale * 1.35f, 0f), Size(scale * 0.7f, scale * 0.28f))
        }
    }
}

private fun DrawScope.drawBunnies(colors: List<Long>, phase: Float) {
    drawRect(colors.colorAt(0, 0xFFE8F5D8))
    val fur = colors.colorAt(1, 0xFFFFFFF4)
    val ear = colors.colorAt(2, 0xFFF0C8E0)
    val flower = colors.colorAt(3, 0xFFF8C8D4)
    val compact = isCompact()
    val unit = min(size.width, size.height)
    val scale = if (compact) unit * 0.22f else unit * 0.11f
    val hop = abs(sin(phase * 2f * PI.toFloat())) * scale * 0.55f
    val hop2 = abs(sin((phase + 0.35f) * 2f * PI.toFloat())) * scale * 0.55f
    val left = if (compact) size.width * 0.3f else size.width * 0.12f
    val right = if (compact) size.width * 0.68f else size.width * 0.28f
    val ground = if (compact) size.height * 0.68f else size.height * 0.82f
    drawBunny(left, ground - hop, scale, fur, ear)
    drawBunny(right, ground - hop2, scale * 0.86f, fur, ear)
    drawCircle(flower, scale * 0.16f, Offset(left + scale * 1.4f, ground + scale * 0.15f))
    drawCircle(flower.copy(alpha = 0.7f), scale * 0.12f, Offset(right - scale * 0.9f, ground + scale * 0.2f))
}

private fun DrawScope.drawBunny(x: Float, y: Float, scale: Float, fur: Color, ear: Color) {
    translate(x, y) {
        drawOval(fur, Offset(-scale * 0.7f, -scale * 0.1f), Size(scale * 1.35f, scale * 0.95f))
        drawCircle(fur, scale * 0.42f, Offset(scale * 0.55f, -scale * 0.25f))
        drawOval(ear, Offset(scale * 0.22f, -scale * 1.15f), Size(scale * 0.22f, scale * 0.75f))
        drawOval(ear, Offset(scale * 0.52f, -scale * 1.1f), Size(scale * 0.2f, scale * 0.7f))
        drawCircle(Color(0xFF3A3450), scale * 0.05f, Offset(scale * 0.45f, -scale * 0.28f))
        drawCircle(Color(0xFF3A3450), scale * 0.05f, Offset(scale * 0.68f, -scale * 0.26f))
    }
}

private fun DrawScope.drawWhale(
    colors: List<Long>,
    phase: Float,
    field: DriftField,
    scratch: SceneScratch,
) {
    drawBubbles(colors, phase, field)
    val body = colors.colorAt(1, 0xFF9EC8E8)
    val belly = colors.colorAt(2, 0xFFFFFFF0)
    val compact = isCompact()
    val unit = min(size.width, size.height)
    val scale = if (compact) unit * 0.30f else unit * 0.15f
    val travel = if (compact) {
        size.width * 0.5f
    } else {
        size.width * (0.18f + 0.08f * sin(phase * 2f * PI.toFloat()))
    }
    val bob = sin(phase * 2f * PI.toFloat()) * scale * 0.15f
    val y = if (compact) size.height * 0.58f else size.height * 0.78f
    translate(travel, y + bob) {
        drawOval(body, Offset(-scale * 1.2f, -scale * 0.45f), Size(scale * 2.3f, scale * 1.05f))
        drawOval(belly, Offset(-scale * 0.7f, -scale * 0.05f), Size(scale * 1.5f, scale * 0.5f))
        drawCircle(body, scale * 0.42f, Offset(scale * 1.05f, -scale * 0.05f))
        drawCircle(Color(0xFF3A3450), scale * 0.06f, Offset(scale * 1.2f, -scale * 0.12f))
        val tail = scratch.path
        tail.rewind()
        tail.moveTo(-scale * 1.15f, 0f)
        tail.lineTo(-scale * 1.7f, -scale * 0.45f)
        tail.lineTo(-scale * 1.55f, 0.05f)
        tail.lineTo(-scale * 1.7f, scale * 0.4f)
        tail.close()
        drawPath(tail, body)
        val spray = 0.5f + 0.5f * abs(sin(phase * 2f * PI.toFloat()))
        drawCircle(belly.copy(alpha = 0.55f * spray), scale * 0.1f, Offset(scale * 0.9f, -scale * 0.7f))
        drawCircle(belly.copy(alpha = 0.4f * spray), scale * 0.07f, Offset(scale * 0.78f, -scale * 0.95f))
    }
}

private fun DrawScope.drawPanda(colors: List<Long>, phase: Float) {
    drawRect(colors.colorAt(0, 0xFFC5E4F7))
    val fur = colors.colorAt(1, 0xFFF4F4F8)
    val balloon = colors.colorAt(2, 0xFFF8C8D4)
    val cheek = colors.colorAt(3, 0xFFFFD4B8)
    val compact = isCompact()
    val unit = min(size.width, size.height)
    val scale = if (compact) unit * 0.26f else unit * 0.13f
    val origin = if (compact) {
        Offset(size.width * 0.50f, size.height * 0.60f)
    } else {
        Offset(size.width * 0.84f, size.height * 0.78f)
    }
    val float = sin(phase * 2f * PI.toFloat()) * scale * 0.18f
    translate(origin.x, origin.y) {
        drawOval(fur, Offset(-scale * 0.75f, -scale * 0.1f), Size(scale * 1.5f, scale * 1.15f))
        drawCircle(fur, scale * 0.55f, Offset(0f, -scale * 0.55f))
        drawCircle(Color(0xFF2A2A35), scale * 0.22f, Offset(-scale * 0.42f, -scale * 0.85f))
        drawCircle(Color(0xFF2A2A35), scale * 0.22f, Offset(scale * 0.42f, -scale * 0.85f))
        drawCircle(Color(0xFF2A2A35), scale * 0.14f, Offset(-scale * 0.16f, -scale * 0.58f))
        drawCircle(Color(0xFF2A2A35), scale * 0.14f, Offset(scale * 0.16f, -scale * 0.58f))
        drawCircle(Color.White, scale * 0.05f, Offset(-scale * 0.13f, -scale * 0.62f))
        drawCircle(Color.White, scale * 0.05f, Offset(scale * 0.19f, -scale * 0.62f))
        drawCircle(Color(0xFF2A2A35), scale * 0.07f, Offset(0f, -scale * 0.42f))
        drawCircle(cheek.copy(alpha = 0.8f), scale * 0.1f, Offset(-scale * 0.32f, -scale * 0.38f))
        drawCircle(cheek.copy(alpha = 0.8f), scale * 0.1f, Offset(scale * 0.32f, -scale * 0.38f))
    }
    val bx = origin.x - scale * 0.95f
    val by = origin.y - scale * 1.85f + float
    drawCircle(balloon, scale * 0.42f, Offset(bx, by))
    drawCircle(colors.colorAt(3, 0xFFFFD4B8), scale * 0.34f, Offset(bx + scale * 0.7f, by - scale * 0.15f))
    drawLine(
        Color(0xFF3A3450).copy(alpha = 0.45f),
        Offset(bx, by + scale * 0.42f),
        Offset(origin.x - scale * 0.2f, origin.y - scale * 0.2f),
        strokeWidth = 2f,
    )
    drawLine(
        Color(0xFF3A3450).copy(alpha = 0.45f),
        Offset(bx + scale * 0.7f, by + scale * 0.18f),
        Offset(origin.x + scale * 0.15f, origin.y - scale * 0.15f),
        strokeWidth = 2f,
    )
}

private fun DrawScope.isCompact(): Boolean = min(size.width, size.height) < 720f

private fun List<Long>.colorAt(index: Int, fallback: Long): Color {
    val value = getOrNull(index) ?: fallback
    return value.toComposeColor()
}
