package com.shibi.musicplayer.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import com.shibi.musicplayer.domain.TrackDisplay
import com.shibi.musicplayer.domain.containsTamil
import com.shibi.musicplayer.ui.theme.Jakarta
import com.shibi.musicplayer.ui.theme.NotoTamil

private const val ELLIPSIS = "\u2026"

/**
 * One line of a title. Overflow is a single Jakarta ellipsis on a
 * grapheme-aligned prefix: Tamil is a Noto span inside a Jakarta paragraph
 * (Noto has no U+2026, and drawing it from Noto paints the mark twice).
 * A trailing space is dropped so a narrow two-pane row does not read
 * `என்னை ...`.
 */
@Composable
fun TrackTitle(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
) {
    val tamil = containsTamil(text)
    val merged = style.merge(fontFamily = Jakarta, fontWeight = fontWeight)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxWidth = constraints.maxWidth
        val display = remember(text, tamil, merged, maxWidth) {
            titleLine(text, tamil, merged, maxWidth, measurer)
        }
        Text(
            text = display,
            modifier = Modifier.fillMaxWidth(),
            style = merged,
            color = color,
            textAlign = textAlign,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

private fun titleLine(
    text: String,
    tamil: Boolean,
    style: TextStyle,
    maxWidth: Int,
    measurer: TextMeasurer,
): AnnotatedString {
    fun line(value: String): AnnotatedString {
        return if (tamil) {
            buildAnnotatedString {
                withStyle(SpanStyle(fontFamily = NotoTamil)) {
                    append(value)
                }
            }
        } else {
            AnnotatedString(value)
        }
    }

    fun fits(value: String): Boolean {
        if (maxWidth <= 0 || maxWidth == Constraints.Infinity) return true
        val layout = measurer.measure(
            text = line(value),
            style = style,
            overflow = TextOverflow.Clip,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = maxWidth),
        )
        return !layout.didOverflowWidth && layout.size.width <= maxWidth
    }

    if (fits(text)) return line(text)
    val limit = TrackDisplay.ellipsisLimit(text) { end ->
        val cut = TrackDisplay.ellipsisEnd(text, end)
        fits(text.take(cut) + ELLIPSIS)
    }
    val cut = TrackDisplay.ellipsisEnd(text, limit)
    return line(text.take(cut) + ELLIPSIS)
}
