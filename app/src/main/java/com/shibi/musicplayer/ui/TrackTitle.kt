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

/**
 * One line of a title. Tamil uses Noto, which has no ellipsis glyph; Compose then
 * draws the overflow mark twice. The cut is measured here and the single ellipsis
 * is set in Jakarta. Every width, including the two-pane list, uses this measure.
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
    if (!tamil) {
        Text(
            text = text,
            modifier = modifier.fillMaxWidth(),
            style = style,
            color = color,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    val measurer = rememberTextMeasurer()
    val measureStyle = style.merge(fontWeight = fontWeight)
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxWidthPx = constraints.maxWidth
        val limit = remember(text, maxWidthPx, measureStyle) {
            if (maxWidthPx == Constraints.Infinity) {
                text.length
            } else {
                TrackDisplay.ellipsisLimit(text) { end ->
                    measurer.measure(
                        text = tamilLine(text, end),
                        style = measureStyle,
                        overflow = TextOverflow.Visible,
                        softWrap = false,
                        maxLines = 1,
                    ).size.width <= maxWidthPx
                }
            }
        }
        Text(
            text = tamilLine(text, limit),
            modifier = Modifier.fillMaxWidth(),
            style = measureStyle,
            color = color,
            textAlign = textAlign,
            fontFamily = if (limit >= text.length) NotoTamil else null,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

private fun tamilLine(text: String, limit: Int): AnnotatedString {
    if (limit >= text.length) return AnnotatedString(text)
    return buildAnnotatedString {
        withStyle(SpanStyle(fontFamily = NotoTamil)) {
            append(text.take(limit).trimEnd())
        }
        withStyle(SpanStyle(fontFamily = Jakarta)) {
            append("\u2026")
        }
    }
}
