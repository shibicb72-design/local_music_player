package com.shibi.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.ArtTile
import com.shibi.musicplayer.ui.theme.Jakarta

@Composable
fun AlbumArtwork(
    albumArtUri: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    title: String = "",
) {
    if (albumArtUri.isNullOrBlank()) {
        GeneratedArtTile(title = title, modifier = modifier.clip(shape))
        return
    }
    SubcomposeAsyncImage(
        model = albumArtUri,
        contentDescription = stringResource(R.string.album_art),
        modifier = modifier.clip(shape),
        contentScale = ContentScale.Crop,
    ) {
        val imageState by painter.state.collectAsState()
        if (imageState is AsyncImagePainter.State.Success) {
            SubcomposeAsyncImageContent()
        } else {
            GeneratedArtTile(title = title, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun GeneratedArtTile(title: String, modifier: Modifier = Modifier) {
    val spec = remember(title) { ArtTile.specFor(title) }
    BoxWithConstraints(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(Color(spec.startArgb), Color(spec.endArgb)),
                start = Offset.Zero,
                end = Offset.Infinite,
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset.Zero,
                    ),
                ),
        )
        Text(
            text = spec.glyph,
            color = Color.White.copy(alpha = 0.92f),
            fontFamily = Jakarta,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (maxWidth.value * 0.42f).sp,
        )
    }
}
