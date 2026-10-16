package com.shibi.musicplayer.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberArtworkColor(albumArtUri: String?): Color? {
    val context = LocalContext.current
    var color by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(albumArtUri) {
        color = if (albumArtUri.isNullOrBlank()) {
            null
        } else {
            loadArtworkColor(context, albumArtUri)
        }
    }
    return color
}

private suspend fun loadArtworkColor(context: Context, uri: String): Color? {
    val request = ImageRequest.Builder(context)
        .data(uri)
        .allowHardware(false)
        .build()
    val result = context.imageLoader.execute(request)
    if (result !is SuccessResult) return null
    val bitmap = result.image.toBitmap()
    return withContext(Dispatchers.Default) {
        val palette = Palette.from(bitmap).generate()
        val swatch = palette.vibrantSwatch ?: palette.dominantSwatch
        swatch?.rgb?.let { Color(it) }
    }
}
