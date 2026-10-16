package com.shibi.musicplayer.ui

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.ArtTile
import com.shibi.musicplayer.domain.OutputKind
import com.shibi.musicplayer.domain.PlaybackTime
import com.shibi.musicplayer.domain.TrackDisplay
import com.shibi.musicplayer.domain.containsTamil
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.ui.theme.TextHigh

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    selectedOutputId: Int?,
    onSelectOutput: (Int?) -> Unit,
    showBack: Boolean = true,
    includeNavigationBars: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val backDescription = stringResource(R.string.back)
    val outputs by rememberAudioOutputs()
    var showSheet by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val glow = (
        rememberArtworkColor(state.albumArtUri)
            ?: Color(ArtTile.specFor(state.title).startArgb)
        ).copy(alpha = 0.9f)
    val effective = effectiveOutput(outputs, selectedOutputId)

    Box(modifier = modifier.fillMaxSize()) {
        AlbumArtwork(
            albumArtUri = state.albumArtUri,
            title = state.title,
            shape = RectangleShape,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(90.dp)
                    } else {
                        Modifier
                    },
                )
                .graphicsLayer {
                    alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.55f else 0.30f
                    scaleX = 1.3f
                    scaleY = 1.3f
                },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to colorScheme.background.copy(alpha = 0.72f),
                        0.45f to colorScheme.background.copy(alpha = 0.78f),
                        1f to colorScheme.background,
                    ),
                ),
        )
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = colorScheme.onBackground,
            contentWindowInsets = if (includeNavigationBars) {
                ScaffoldDefaults.contentWindowInsets
            } else {
                WindowInsets.statusBars.union(
                    WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
                )
            },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.player_title).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            letterSpacing = 2.sp,
                            color = TextHigh,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        navigationIconContentColor = colorScheme.onBackground,
                    ),
                    navigationIcon = {
                        if (showBack) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = backDescription,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    },
                )
            },
        ) { padding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                val compact = maxHeight < 700.dp
                val artSize = minOf(
                    maxWidth * 0.86f,
                    maxHeight * (if (compact) 0.40f else 0.50f),
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        AlbumArtwork(
                            albumArtUri = state.albumArtUri,
                            title = state.title,
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier
                                .size(artSize)
                                .shadow(
                                    elevation = 36.dp,
                                    shape = RoundedCornerShape(28.dp),
                                    ambientColor = glow,
                                    spotColor = glow,
                                ),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    val titleText = TrackDisplay.title(state.title)
                        .ifBlank { stringResource(R.string.nothing_playing) }
                    if (containsTamil(titleText)) {
                        TrackTitle(
                            text = titleText,
                            style = MaterialTheme.typography.headlineSmall,
                            color = colorScheme.onBackground,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.headlineSmall,
                            color = colorScheme.onBackground,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    TrackDisplay.artist(state.artist)?.let { artist ->
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
                    SeekControl(
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        enabled = state.hasMedia && state.durationMs > 0L,
                        onSeek = onSeek,
                    )
                    Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
                    TransportControls(
                        isPlaying = state.isPlaying,
                        hasMedia = state.hasMedia,
                        hasNext = state.hasNext,
                        hasPrevious = state.hasPrevious,
                        compact = compact,
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                    )
                    Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
                    Surface(
                        onClick = { showSheet = true },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.10f),
                        contentColor = colorScheme.onBackground,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = outputIcon(effective?.kind),
                                contentDescription = null,
                                tint = OutputAccent,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = effective?.name
                                    ?: stringResource(R.string.output_this_phone),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
                }
            }
        }
    }
    if (showSheet) {
        OutputPickerSheet(
            outputs = outputs,
            selectedId = selectedOutputId,
            onSelect = { id -> onSelectOutput(id) },
            onDismiss = { showSheet = false },
        )
    }
}

private fun outputIcon(kind: OutputKind?): ImageVector = when (kind) {
    OutputKind.BLUETOOTH -> Icons.Rounded.Bluetooth
    OutputKind.WIRED -> Icons.Rounded.Headphones
    OutputKind.USB -> Icons.Rounded.Usb
    OutputKind.PHONE_SPEAKER, null -> Icons.Rounded.Smartphone
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekControl(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    val rangeEnd = durationMs.coerceAtLeast(1L).toFloat()
    val displayed = if (dragging) dragPosition else positionMs.toFloat()
    val sliderValue = displayed.coerceIn(0f, if (durationMs > 0L) durationMs.toFloat() else 1f)
    val seekDescription = stringResource(R.string.seek)
    val shownPosition = if (dragging) dragPosition.toLong() else positionMs

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = sliderValue,
            onValueChange = { value ->
                dragging = true
                dragPosition = value
            },
            onValueChangeFinished = {
                onSeek(dragPosition.toLong())
                dragging = false
            },
            enabled = enabled,
            valueRange = 0f..rangeEnd,
            thumb = {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .shadow(4.dp, CircleShape)
                        .background(Color.White, CircleShape),
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(4.dp),
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                    ),
                    thumbTrackGapSize = 0.dp,
                    drawStopIndicator = null,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = seekDescription },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = PlaybackTime.format(shownPosition),
                style = MaterialTheme.typography.labelSmall,
                color = TextHigh,
            )
            Text(
                text = PlaybackTime.format(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = TextHigh,
            )
        }
    }
}

@Composable
private fun TransportControls(
    isPlaying: Boolean,
    hasMedia: Boolean,
    hasNext: Boolean,
    hasPrevious: Boolean,
    compact: Boolean,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val playButtonSize = if (compact) 64.dp else 80.dp
    val playIconSize = if (compact) 34.dp else 42.dp
    val skipButtonSize = if (compact) 36.dp else 44.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = hasPrevious,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = colorScheme.onBackground,
                disabledContentColor = colorScheme.onBackground.copy(alpha = 0.38f),
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.previous),
                modifier = Modifier.size(skipButtonSize),
            )
        }
        Box(
            modifier = Modifier
                .size(playButtonSize)
                .shadow(16.dp, CircleShape, spotColor = colorScheme.primary)
                .background(colorScheme.primary, CircleShape)
                .clip(CircleShape)
                .clickable(enabled = hasMedia, onClick = onTogglePlayPause),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    (fadeIn() + scaleIn()) togetherWith (fadeOut() + scaleOut())
                },
                label = "playPause",
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(
                        if (playing) R.string.pause else R.string.play,
                    ),
                    tint = colorScheme.onPrimary,
                    modifier = Modifier.size(playIconSize),
                )
            }
        }
        IconButton(
            onClick = onNext,
            enabled = hasNext,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = colorScheme.onBackground,
                disabledContentColor = colorScheme.onBackground.copy(alpha = 0.38f),
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.next),
                modifier = Modifier.size(skipButtonSize),
            )
        }
    }
}
