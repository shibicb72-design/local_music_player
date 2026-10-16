package com.shibi.musicplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.fillMaxWidth
import com.shibi.musicplayer.domain.TrackDisplay
import com.shibi.musicplayer.domain.containsTamil
import com.shibi.musicplayer.ui.theme.Jakarta
import com.shibi.musicplayer.ui.theme.NotoTamil

/**
 * One line of a title. Tamil uses Noto, which has no ellipsis glyph; Compose then
 * draws the overflow mark twice. The cut is measured here and the single ellipsis
 * is set in Jakarta.
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
    var limit by remember(text) { mutableIntStateOf(text.length) }
    val display = remember(text, limit) { tamilLine(text, limit) }
    Text(
        text = display,
        modifier = modifier.fillMaxWidth(),
        style = style,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        fontFamily = if (limit >= text.length) NotoTamil else null,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { layout ->
            if (layout.hasVisualOverflow || layout.didOverflowWidth) {
                val end = layout.getLineEnd(lineIndex = 0, visibleEnd = true)
                val next = TrackDisplay.tamilEllipsisCut(text, end)
                if (next < limit) limit = next
            }
        },
    )
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
