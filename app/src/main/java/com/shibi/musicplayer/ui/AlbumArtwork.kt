package com.shibi.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.shibi.musicplayer.R

@Composable
fun AlbumArtwork(
    albumArtUri: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
) {
    if (albumArtUri.isNullOrBlank()) {
        AlbumArtPlaceholder(modifier.clip(shape))
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
            AlbumArtPlaceholder(Modifier.fillMaxSize())
        }
    }
}

@Composable
fun AlbumArtPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxSize(0.42f),
        )
    }
}
