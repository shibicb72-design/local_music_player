package com.shibi.musicplayer

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.ui.SettingsContent
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Theatrum launcher icon review set. The layers come from the real adaptive icon
 * (`R.mipmap.ic_launcher`), drawn at 1.5x the mask size like a launcher does
 * (108dp canvas, 72dp visible), so a clip outside the 66dp safe zone would show here.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class IconScreenshotTest {

    @Test
    fun masks() {
        captureRoboImage("build/outputs/roborazzi/icon_masks.png") {
            IconSheet(background = Color(0xFF3C4A5C)) { shape, size ->
                FullColorIcon(shape, size)
            }
        }
    }

    @Test
    fun monochromeLight() {
        captureRoboImage("build/outputs/roborazzi/icon_monochrome_light.png") {
            IconSheet(background = Color(0xFFF3EEE8)) { shape, size ->
                ThemedIcon(shape, size, container = Color(0xFFFFDDB3), tint = Color(0xFF2A1800))
            }
        }
    }

    @Test
    fun monochromeDark() {
        captureRoboImage("build/outputs/roborazzi/icon_monochrome_dark.png") {
            IconSheet(background = Color(0xFF1C1B1F)) { shape, size ->
                ThemedIcon(shape, size, container = Color(0xFF3A2E22), tint = Color(0xFFFFDDB3))
            }
        }
    }

    /** The full 108dp canvas at 2x with the 72dp viewport square and the 66dp safe circle. */
    @Test
    fun safeZone() {
        captureRoboImage("build/outputs/roborazzi/icon_safe_zone.png") {
            val icon = adaptiveIcon()
            Canvas(modifier = Modifier.size(216.dp)) {
                drawLayer(icon.background, visible = 1f)
                drawLayer(icon.foreground, visible = 1f)
                val unit = size.width / 108f
                val guide = Color(0xCCFFFFFF)
                drawCircle(guide, radius = 33f * unit, style = Stroke(width = 1.dp.toPx()))
                drawRect(
                    guide,
                    topLeft = Offset(18f * unit, 18f * unit),
                    size = Size(72f * unit, 72f * unit),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }
    }

    @Test
    fun notificationGlyph() {
        captureRoboImage("build/outputs/roborazzi/icon_notification.png") {
            Row {
                listOf(
                    Color(0xFF1C1B1F) to Color.White,
                    Color(0xFFF3EEE8) to Color(0xFF1C1B1F),
                ).forEach { (field, tint) ->
                    Row(
                        modifier = Modifier.background(field).padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf(24.dp, 48.dp).forEach { size ->
                            Icon(
                                painter = painterResource(R.drawable.ic_notification),
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(size),
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun settingsAbout() {
        captureRoboImage("build/outputs/roborazzi/icon_settings_about.png") {
            MusicPlayerTheme {
                SettingsContent(
                    darkTheme = true,
                    onDarkThemeChange = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    /** 128dp at xxxhdpi is exactly 512 px. */
    @Test
    @Config(qualifiers = "w411dp-h891dp-xxxhdpi")
    fun large512() {
        captureRoboImage("build/outputs/roborazzi/theatrum_icon_512.png") {
            FullColorIcon(CircleShape, 128.dp)
        }
    }
}

/** A superellipse |x|^4 + |y|^4 = 1, close to the Pixel and One UI squircle. */
private val SquircleShape: Shape = GenericShape { size, _ ->
    val rx = size.width / 2f
    val ry = size.height / 2f
    val steps = 128
    for (i in 0..steps) {
        val t = 2.0 * Math.PI * i / steps
        val c = cos(t)
        val s = sin(t)
        val x = rx + rx * (sign(c) * abs(c).pow(0.5)).toFloat()
        val y = ry + ry * (sign(s) * abs(s).pow(0.5)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private val Masks: List<Pair<String, Shape>> = listOf(
    "Circle" to CircleShape,
    "Squircle" to SquircleShape,
    "Rounded square" to RoundedCornerShape(percent = 22),
)

@Composable
private fun IconSheet(
    background: Color,
    icon: @Composable (Shape, Dp) -> Unit,
) {
    val label = if (background.luminance() > 0.5f) Color(0xFF1C1B1F) else Color.White
    Column(
        modifier = Modifier.background(background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Masks.forEach { (name, shape) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(width = 96.dp, height = 108.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(text = name, color = label, fontSize = 13.sp)
                }
                icon(shape, 48.dp)
                icon(shape, 108.dp)
            }
        }
    }
}

@Composable
private fun adaptiveIcon(): AdaptiveIconDrawable =
    LocalContext.current.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable

@Composable
private fun FullColorIcon(shape: Shape, size: Dp) {
    val icon = adaptiveIcon()
    Canvas(modifier = Modifier.size(size).clip(shape)) {
        drawLayer(icon.background)
        drawLayer(icon.foreground)
    }
}

@Composable
private fun ThemedIcon(shape: Shape, size: Dp, container: Color, tint: Color) {
    val monochrome = requireNotNull(adaptiveIcon().monochrome).mutate().apply { setTint(tint.toArgb()) }
    Canvas(modifier = Modifier.size(size).clip(shape).background(container)) {
        drawLayer(monochrome)
    }
}

/** Draws an adaptive layer so the visible mask covers its middle 72 of 108 dp. */
private fun DrawScope.drawLayer(layer: Drawable, visible: Float = 72f / 108f) {
    val side = size.width / visible
    val offset = -(side - size.width) / 2f
    layer.setBounds(offset.toInt(), offset.toInt(), (offset + side).toInt(), (offset + side).toInt())
    drawIntoCanvas { canvas -> layer.draw(canvas.nativeCanvas) }
}
