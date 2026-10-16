package com.shibi.musicplayer.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.shibi.musicplayer.domain.containsTamil
import com.shibi.musicplayer.ui.theme.Jakarta
import com.shibi.musicplayer.ui.theme.NotoTamil

/**
 * One line of a title. The characters are never rewritten: overflow is always
 * [TextOverflow.Ellipsis]. Tamil is a Noto span inside a Jakarta paragraph so
 * the ellipsis glyph comes from Jakarta (Noto has none, and drawing it from
 * Noto paints the mark twice) while the letters stay intact.
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
    Text(
        text = if (tamil) {
            buildAnnotatedString {
                withStyle(SpanStyle(fontFamily = NotoTamil)) {
                    append(text)
                }
            }
        } else {
            AnnotatedString(text)
        },
        modifier = modifier.fillMaxWidth(),
        style = style.merge(fontFamily = Jakarta, fontWeight = fontWeight),
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}
