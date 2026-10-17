package com.shibi.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.ArtTile
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoAccess
import com.shibi.musicplayer.domain.VideoFormat
import com.shibi.musicplayer.library.VideoLibraryUiState
import com.shibi.musicplayer.library.VideoLibraryViewModel
import com.shibi.musicplayer.library.videoThumbnailRequest
import com.shibi.musicplayer.ui.background.LocalBackgroundChrome
import com.shibi.musicplayer.ui.background.rememberSurfaceRoles
import com.shibi.musicplayer.ui.background.toComposeColor
import com.shibi.musicplayer.ui.theme.Spacing

private val ThumbnailShape = RoundedCornerShape(12.dp)

/**
 * The Videos section. The view model (and so the MediaStore query and the permission
 * request) exists only while this destination does: music-only users are never asked.
 * Access is re-checked on every resume; the first visit asks once if access is denied.
 */
@Composable
fun VideosScreen(
    onVideoClick: (Video) -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
    viewModel: VideoLibraryViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var checked by remember { mutableStateOf(false) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val requestAccess = rememberVideoAccessRequest(onResult = viewModel::refresh)

    // Access granted or revoked in system Settings only shows up when the app resumes.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        checked = true
        onPauseOrDispose { }
    }
    LaunchedEffect(checked) {
        // Read the flow, not the collected state, which can still hold the initial Denied.
        if (checked && !asked && viewModel.uiState.value.access == VideoAccess.Denied) {
            asked = true
            requestAccess()
        }
    }

    VideosContent(
        state = state,
        checked = checked,
        onVideoClick = onVideoClick,
        onRequestAccess = requestAccess,
        onOpenSettings = { openAppSettings(context) },
        contentBottomPadding = contentBottomPadding,
        modifier = modifier,
    )
}

/** Stateless body, so screenshots feed it a fake list and no MediaStore. */
@Composable
internal fun VideosContent(
    state: VideoLibraryUiState,
    onVideoClick: (Video) -> Unit,
    onRequestAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
    checked: Boolean = true,
    playingVideoId: Long? = null,
    listState: LazyListState = rememberLazyListState(),
) {
    val denied = state.access == VideoAccess.Denied
    VideosFrame(
        subtitle = if (!checked || denied) {
            ""
        } else {
            pluralStringResource(R.plurals.video_count, state.videos.size, state.videos.size)
        },
        modifier = modifier,
    ) {
        when {
            !checked -> Unit
            denied -> Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                VideoPermissionPrompt(
                    onGrant = onRequestAccess,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, bottom = contentBottomPadding),
                )
            }
            else -> {
                if (state.access == VideoAccess.Partial) {
                    VideoPartialBanner(
                        onAllowAll = onRequestAccess,
                        onPickMore = onRequestAccess,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    )
                }
                VideoList(
                    state = state,
                    onVideoClick = onVideoClick,
                    contentBottomPadding = contentBottomPadding,
                    playingVideoId = playingVideoId,
                    listState = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun VideoList(
    state: VideoLibraryUiState,
    onVideoClick: (Video) -> Unit,
    contentBottomPadding: Dp,
    playingVideoId: Long?,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val videos = state.videos
    when {
        videos.isEmpty() && state.isLoading -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        videos.isEmpty() && state.hasError -> VideosMessage(
            icon = Icons.Rounded.VideoLibrary,
            message = stringResource(R.string.video_library_error),
            modifier = modifier.padding(bottom = contentBottomPadding),
        )
        videos.isEmpty() -> VideosMessage(
            icon = Icons.Rounded.VideoLibrary,
            message = stringResource(R.string.videos_empty),
            modifier = modifier.padding(bottom = contentBottomPadding),
        )
        else -> LazyColumn(
            modifier = modifier,
            state = listState,
            contentPadding = PaddingValues(bottom = contentBottomPadding),
        ) {
            items(videos, key = { it.id }) { video ->
                VideoRow(
                    video = video,
                    isPlaying = video.id == playingVideoId,
                    onClick = { onVideoClick(video) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/** The `TrackRow` pill with a 16:9 thumbnail; text colours come from the row's own fill. */
@Composable
internal fun VideoRow(
    video: Video,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = LocalBackgroundChrome.current
    val fill = if (isPlaying) chrome.playingRowFill else chrome.rowFill
    val roles = rememberSurfaceRoles(fill)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VideoThumbnail(
            video = video,
            modifier = Modifier.size(width = 96.dp, height = 54.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.md)
                .clipToBounds(),
        ) {
            TrackTitle(
                text = video.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = roles.onSurfaceArgb.toComposeColor(),
                modifier = Modifier.fillMaxWidth(),
            )
            val subtitle = videoSubtitle(video)
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = roles.onVariantArgb.toComposeColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (isPlaying) {
            Spacer(Modifier.width(Spacing.sm))
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = null,
                tint = chrome.accentText,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** `1.4 GB · 1080p · Camera`; values MediaStore could not read are left out, not dashed. */
internal fun videoSubtitle(video: Video): String = listOfNotNull(
    VideoFormat.size(video.sizeBytes).takeIf { it != VideoFormat.MISSING },
    VideoFormat.resolution(video.width, video.height).takeIf { it != VideoFormat.MISSING },
    video.folder,
).joinToString(" · ")

@Composable
private fun VideoThumbnail(video: Video, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // The request carries its own fetcher factory, so build it once per video.
    val request = remember(video.contentUri) { videoThumbnailRequest(context, video.contentUri) }
    Box(modifier = modifier.clip(ThumbnailShape)) {
        SubcomposeAsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        ) {
            val imageState by painter.state.collectAsState()
            if (imageState is AsyncImagePainter.State.Success) {
                SubcomposeAsyncImageContent()
            } else {
                FilmTile(title = video.title, modifier = Modifier.fillMaxSize())
            }
        }
        DurationBadge(
            text = VideoFormat.duration(video.durationMs),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp),
        )
    }
}

/** `GeneratedArtTile`'s gradient with a film glyph, for WMV and anything else with no frame. */
@Composable
private fun FilmTile(title: String, modifier: Modifier = Modifier) {
    val spec = remember(title) { ArtTile.specFor(title) }
    Box(
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
        Icon(
            imageVector = Icons.Rounded.Movie,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Fixed dark scrim and white text, so it reads the same over any frame or backdrop. */
@Composable
private fun DurationBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        maxLines = 1,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun VideosFrame(
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.videos_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        content()
    }
}

@Composable
private fun VideosMessage(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
