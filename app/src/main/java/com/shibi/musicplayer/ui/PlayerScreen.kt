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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.ArtTile
import com.shibi.musicplayer.domain.OutputKind
import com.shibi.musicplayer.domain.PlaybackTime
import com.shibi.musicplayer.domain.RepeatMode
import com.shibi.musicplayer.domain.ShuffleBadge
import com.shibi.musicplayer.domain.ShuffleContext
import com.shibi.musicplayer.domain.TrackDisplay
import com.shibi.musicplayer.domain.containsTamil
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.ui.background.ContrastSurface
import com.shibi.musicplayer.ui.background.LocalBackgroundChrome
import com.shibi.musicplayer.ui.theme.Base0

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
    onToggleShuffle: () -> Unit = {},
    onCycleRepeat: () -> Unit = {},
    showBack: Boolean = true,
    includeNavigationBars: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val backDescription = stringResource(R.string.back)
    val outputs by rememberAudioOutputs()
    var showSheet by remember { mutableStateOf(false) }
    val chrome = LocalBackgroundChrome.current
    val paneColor = if (chrome.isLight) chrome.frost else Base0
    val glow = (
        rememberArtworkColor(state.albumArtUri)
            ?: Color(ArtTile.specFor(state.title).startArgb)
        ).copy(alpha = 0.9f)
    val effective = effectiveOutput(outputs, selectedOutputId)

    ContrastSurface(
        color = paneColor,
        modifier = modifier.fillMaxSize(),
    ) {
        val onPane = LocalContentColor.current
        val onPaneVariant = MaterialTheme.colorScheme.onSurfaceVariant
        Box(modifier = Modifier.fillMaxSize()) {
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
                        0f to paneColor.copy(alpha = if (chrome.isLight) 0.38f else 0.72f),
                        0.45f to paneColor.copy(alpha = if (chrome.isLight) 0.58f else 0.78f),
                        1f to paneColor.copy(alpha = if (chrome.isLight) 0.82f else 1f),
                    ),
                ),
        )
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = onPane,
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
                            color = onPane,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        navigationIconContentColor = onPane,
                        titleContentColor = onPane,
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
                            color = onPane,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.headlineSmall,
                            color = onPane,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(),
                        )
                    }
                    val subtitle = TrackDisplay.subtitle(state.artist, state.album)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle ?: "\u00A0",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (subtitle == null) {
                            Color.Transparent
                        } else {
                            onPaneVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                        shuffleEnabled = state.shuffleEnabled,
                        shuffleContext = state.shuffleContext,
                        repeatMode = state.repeatMode,
                        compact = compact,
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat,
                    )
                    Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
                    ContrastSurface(
                        onClick = { showSheet = true },
                        shape = CircleShape,
                        color = if (chrome.isLight) {
                            Color.White.copy(alpha = 0.62f)
                        } else {
                            Color.White.copy(alpha = 0.10f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (chrome.isLight) chrome.frostBorder else Color.White.copy(alpha = 0.12f),
                        ),
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
    val onPane = LocalContentColor.current

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
                        .background(onPane, CircleShape),
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(4.dp),
                    colors = SliderDefaults.colors(
                        activeTrackColor = onPane,
                        inactiveTrackColor = onPane.copy(alpha = 0.18f),
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
                color = onPane,
            )
            Text(
                text = PlaybackTime.format(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = onPane.copy(alpha = 0.72f),
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
    shuffleEnabled: Boolean,
    shuffleContext: ShuffleContext,
    repeatMode: RepeatMode,
    compact: Boolean,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val chrome = LocalBackgroundChrome.current
    val playButtonSize = if (compact) 64.dp else 80.dp
    val playIconSize = if (compact) 34.dp else 42.dp
    val skipButtonSize = if (compact) 36.dp else 44.dp
    val modeButtonSize = if (compact) 26.dp else 30.dp
    val onPane = LocalContentColor.current
    val accent = if (chrome.isLight) chrome.progress else colorScheme.secondary
    val dimmed = onPane.copy(alpha = 0.40f)
    val shuffleBadge = ShuffleBadge.label(shuffleEnabled, shuffleContext)?.toString()
    val repeatBadge = when (repeatMode) {
        RepeatMode.OFF -> null
        RepeatMode.ONCE -> stringResource(R.string.repeat_badge_once)
        RepeatMode.THRICE -> stringResource(R.string.repeat_badge_thrice)
        RepeatMode.INFINITE -> stringResource(R.string.repeat_badge_infinite)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeButton(
            onClick = onToggleShuffle,
            enabled = hasMedia,
            tint = if (shuffleEnabled) accent else dimmed,
            badge = shuffleBadge,
            description = stringResource(
                if (shuffleEnabled) {
                    if (shuffleContext == ShuffleContext.PLAYLIST) {
                        R.string.shuffle_on_playlist
                    } else {
                        R.string.shuffle_on_all
                    }
                } else {
                    R.string.shuffle_off
                },
            ),
            icon = Icons.Rounded.Shuffle,
            iconSize = modeButtonSize,
        )
        IconButton(
            onClick = onPrevious,
            enabled = hasPrevious,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = onPane,
                disabledContentColor = onPane.copy(alpha = 0.38f),
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
                contentColor = onPane,
                disabledContentColor = onPane.copy(alpha = 0.38f),
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.next),
                modifier = Modifier.size(skipButtonSize),
            )
        }
        ModeButton(
            onClick = onCycleRepeat,
            enabled = hasMedia,
            tint = if (repeatMode == RepeatMode.OFF) dimmed else accent,
            badge = repeatBadge,
            description = stringResource(
                when (repeatMode) {
                    RepeatMode.OFF -> R.string.repeat_off
                    RepeatMode.ONCE -> R.string.repeat_once
                    RepeatMode.THRICE -> R.string.repeat_thrice
                    RepeatMode.INFINITE -> R.string.repeat_infinite
                },
            ),
            icon = Icons.Rounded.Repeat,
            iconSize = modeButtonSize,
        )
    }
}

@Composable
private fun ModeButton(
    onClick: () -> Unit,
    enabled: Boolean,
    tint: Color,
    badge: String?,
    description: String,
    icon: ImageVector,
    iconSize: Dp,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(56.dp),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = tint,
            disabledContentColor = tint.copy(alpha = 0.38f),
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-6).dp),
                )
            }
        }
    }
}
