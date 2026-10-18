package com.shibi.musicplayer.ui

import android.content.res.Configuration
import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shibi.musicplayer.R
import com.shibi.musicplayer.background.BackgroundViewModel
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.AudioOutput
import com.shibi.musicplayer.domain.LikeShufflePicker
import com.shibi.musicplayer.domain.PlaybackArbiter
import com.shibi.musicplayer.domain.ShuffleContext
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.shownWith
import com.shibi.musicplayer.library.LibraryViewModel
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.playback.PlayerViewModel
import com.shibi.musicplayer.playlist.PlaylistViewModel
import com.shibi.musicplayer.ui.background.AppBackgroundHost
import com.shibi.musicplayer.ui.background.BackgroundPickerScreen
import com.shibi.musicplayer.ui.background.backgroundTitleRes
import com.shibi.musicplayer.ui.background.titleId
import com.shibi.musicplayer.video.MusicControl
import com.shibi.musicplayer.video.PlayerSurface
import com.shibi.musicplayer.video.VideoEngine
import com.shibi.musicplayer.video.VideoStatus
import com.shibi.musicplayer.video.VideoUiState
import com.shibi.musicplayer.video.VideoViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.roundToInt

private const val SongsRoute = SectionRoutes.Songs
private const val PlaylistsRoute = SectionRoutes.Playlists
private const val PlayerRoute = SectionRoutes.Player
private const val PlaylistDetailRoute = SectionRoutes.PlaylistDetail
private const val DevicesRoute = SectionRoutes.Devices
private const val FavoritesRoute = SectionRoutes.Favorites
private const val RecentRoute = SectionRoutes.Recent
private const val SearchRoute = SectionRoutes.Search
private const val SettingsRoute = SectionRoutes.Settings
private const val BackgroundsRoute = SectionRoutes.Backgrounds
private const val LicensesRoute = SectionRoutes.Licenses
private const val ErrorLogRoute = SectionRoutes.ErrorLog
private const val VideosRoute = SectionRoutes.Videos
private const val VideoFullscreenRoute = SectionRoutes.VideoFullscreen
private const val PlaylistIdArg = "playlistId"
private val TwoPaneBreakpoint = 600.dp

@Composable
fun MusicApp(
    libraryViewModel: LibraryViewModel = viewModel(),
    playerViewModel: PlayerViewModel = viewModel(),
    playlistViewModel: PlaylistViewModel = viewModel(),
    backgroundViewModel: BackgroundViewModel = viewModel(),
    // Activity-scoped, so a fold or unfold keeps the video playing. Creates no engine yet.
    videoViewModel: VideoViewModel = viewModel(),
    // Taps on the video's notification or lock-screen card.
    openVideoRequests: Flow<Unit> = emptyFlow(),
    // Videos opened from another app (Files, Gallery) through "Open with Theatrum".
    externalVideos: Flow<Video> = emptyFlow(),
) {
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val videoState by videoViewModel.uiState.collectAsStateWithLifecycle()
    val videoEngine by videoViewModel.engine.collectAsStateWithLifecycle()
    val inPip by videoViewModel.inPip.collectAsStateWithLifecycle()
    val brightnessSwipe by videoViewModel.brightnessSwipe.collectAsStateWithLifecycle()
    val trackSheet by videoViewModel.trackSheet.collectAsStateWithLifecycle()
    val videoBounds = remember { VideoBounds() }
    // The tracks sheet, over the box, the pane or fullscreen; closing the video closes it.
    var showTracks by rememberSaveable { mutableStateOf(false) }
    val tracksOpen = showTracks && videoState.isActive
    // While in picture-in-picture only the video is on screen; nothing else may be composed.
    val pipOnly = inPip && videoState.isActive
    var musicPausedByVideo by rememberSaveable { mutableStateOf(false) }
    val selectedOutputId by playerViewModel.preferredOutputId.collectAsStateWithLifecycle()
    val playlistState by playlistViewModel.state.collectAsStateWithLifecycle()
    val favoriteIds by playlistViewModel.favoriteIds.collectAsStateWithLifecycle()
    val playbackRoute by rememberPlaybackRoute(selectedOutputId)
    val playingTrackId = playerState.currentMediaId?.toLongOrNull()
    val playingIsFavorite = playingTrackId != null && playingTrackId in favoriteIds
    val recentIds by playlistViewModel.recentTrackIds.collectAsStateWithLifecycle()
    val recentSearches by playlistViewModel.recentSearches.collectAsStateWithLifecycle()
    val darkTheme by playlistViewModel.darkTheme.collectAsStateWithLifecycle()
    val appBackground by backgroundViewModel.selection.collectAsStateWithLifecycle()
    val backgroundColors by backgroundViewModel.customColors.collectAsStateWithLifecycle()
    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val tracksById = remember(libraryState.tracks) {
        libraryState.tracks.associateBy { it.id }
    }
    val recentTracks = remember(recentIds, tracksById) {
        recentIds.mapNotNull { tracksById[it] }
    }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    var section by remember { mutableStateOf(NavSection.Songs) }
    // The section before the current one: where an unfold lands if Now Playing leaves the ring.
    var previousSection by remember { mutableStateOf<NavSection?>(null) }
    var showDevices by remember { mutableStateOf(false) }
    var pendingTrack by remember { mutableStateOf<Track?>(null) }
    val outputs by rememberAudioOutputs()

    fun select(next: NavSection) {
        if (next == section) return
        previousSection = section
        section = next
    }

    fun closeVideo() {
        videoViewModel.close()
        musicPausedByVideo = false
    }

    fun playVideo(video: Video) {
        val decision = PlaybackArbiter.onVideoStart(playerViewModel.uiState.value.isPlaying)
        if (decision.pauseMusic) {
            playerViewModel.pause()
            musicPausedByVideo = true
        }
        videoViewModel.open(video)
    }

    // A video played from its own notification, lock screen or earbuds keeps the music paused.
    DisposableEffect(playerViewModel, videoViewModel) {
        val music = object : MusicControl {
            override val isPlaying: Boolean get() = playerViewModel.uiState.value.isPlaying
            override fun pause() = playerViewModel.pause()
        }
        videoViewModel.setMusicControl(music)
        onDispose { videoViewModel.clearMusicControl(music) }
    }
    // Music started by any path (notification, lock screen, Now Playing) closes the video,
    // which also ends the video's session and stops its service.
    LaunchedEffect(playerViewModel, videoViewModel) {
        var wasPlaying = playerViewModel.uiState.value.isPlaying
        playerViewModel.uiState.collect { music ->
            val decision = PlaybackArbiter.onMusicPlayingChanged(
                wasPlaying = wasPlaying,
                isPlaying = music.isPlaying,
                videoActive = videoViewModel.uiState.value.isActive,
            )
            wasPlaying = music.isPlaying
            if (decision.closeVideo) closeVideo()
        }
    }
    // The last route seen, so a fold (a new composition) never counts as leaving Videos.
    val previousRoute = remember { mutableStateOf<String?>(null) }
    // Fullscreen covers the whole window, over either layout; the carousel and chrome hide.
    val fullscreen = route == VideoFullscreenRoute && videoState.isActive
    LaunchedEffect(fullscreen) { videoViewModel.setFullscreen(fullscreen) }
    // The box's place in the window, as the PiP source hint; null while fullscreen covers all.
    val fullscreenState = rememberUpdatedState(fullscreen)
    LaunchedEffect(videoBounds) {
        snapshotFlow { if (fullscreenState.value) null else videoBounds.rect }
            .collect { b ->
                videoViewModel.setPlayerBounds(
                    b?.let {
                        Rect(
                            it.left.roundToInt(),
                            it.top.roundToInt(),
                            (it.left + it.width).roundToInt(),
                            (it.top + it.height).roundToInt(),
                        )
                    },
                )
            }
    }
    // The video closed under fullscreen (music started, or a cold start restored the route).
    LaunchedEffect(route, videoState.isActive) {
        if (route == VideoFullscreenRoute && !videoState.isActive) navController.popBackStack()
    }
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // A phone or the folded cover screen; the unfolded inner screen is at least 600dp both ways.
    val compactDevice = configuration.smallestScreenWidthDp < TwoPaneBreakpoint.value
    // Null on a new composition, so only a rotation seen by this composition counts.
    val previousLandscape = remember { mutableStateOf<Boolean?>(null) }

    // Dark theme paints the dark default over the chosen backdrop without rewriting it.
    CompositionLocalProvider(LocalVideoBounds provides videoBounds) {
    AppBackgroundHost(
        background = appBackground.shownWith(darkTheme),
        customColors = backgroundColors,
        // A still backdrop while a video is open: an animated one redraws every frame behind the picture.
        forceStatic = videoState.isActive,
    ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= TwoPaneBreakpoint
        // Named in the error log, so a report says where the picture was.
        LaunchedEffect(fullscreen, wide) {
            videoViewModel.setPlayerSurface(
                when {
                    fullscreen -> PlayerSurface.Fullscreen
                    wide -> PlayerSurface.VideoPane
                    else -> PlayerSurface.InlineBox
                },
            )
        }
        val openTracks = { showTracks = true }

        LaunchedEffect(videoState.status, wide) {
            if (videoState.status == VideoStatus.Ended) {
                val decision = PlaybackArbiter.onVideoEnded(musicPausedByVideo, twoPane = wide)
                if (decision.resumeMusic && !playerViewModel.uiState.value.isPlaying) {
                    playerViewModel.togglePlayPause()
                }
                // Two-pane: no auto-next; Now Playing comes back in the right pane.
                if (decision.closeVideo) closeVideo()
            }
        }
        // Leaving Videos pauses the video in both layouts (the right pane follows the tab when unfolded).
        LaunchedEffect(route) {
            val from = previousRoute.value
            if (route != null) previousRoute.value = route
            if (from == null || route == null) return@LaunchedEffect
            val decision = PlaybackArbiter.onRouteChanged(
                fromVideos = SectionRoutes.sectionFor(from) == NavSection.Videos,
                toVideos = SectionRoutes.sectionFor(route) == NavSection.Videos,
            )
            if (decision.pauseVideo) videoViewModel.pause()
        }

        val sections = SectionRoutes.sectionsFor(expanded = wide)
        // Pure, so the first expanded frame already shows the fallback: the ring never spins in.
        val carouselSection = SectionRoutes.selectionFor(wide, section, previousSection)
        // A second play button would fight the video; the bar returns when it closes.
        val showMini = playerState.hasMedia && route != PlayerRoute && !videoState.isActive
        val activeOutputId = playbackRoute.activeDeviceId

        fun openFullscreen() {
            if (navController.currentDestination?.route != VideoFullscreenRoute) {
                navController.navigate(VideoFullscreenRoute) { launchSingleTop = true }
            }
        }

        fun exitFullscreen() {
            if (navController.currentDestination?.route == VideoFullscreenRoute) navController.popBackStack()
            // Opened from the unfolded pane over another section, then folded: the box is on Videos.
            if (!wide && videoViewModel.uiState.value.isActive &&
                navController.currentDestination?.route != VideosRoute
            ) {
                select(NavSection.Videos)
                navController.openSection(VideosRoute)
            }
        }

        // Returning from the PiP window taps back into the video, which reopens fullscreen.
        var wasInPip by remember { mutableStateOf(false) }
        LaunchedEffect(inPip) {
            if (wasInPip && !inPip && videoState.isActive) openFullscreen()
            wasInPip = inPip
        }

        fun playQueue(
            tracks: List<Track>,
            startId: Long,
            context: ShuffleContext = ShuffleContext.ALL_SONGS,
        ) {
            if (tracks.isEmpty()) return
            if (PlaybackArbiter.onMusicStart(videoViewModel.uiState.value.isActive).closeVideo) closeVideo()
            playerViewModel.playTrack(tracks, startId, context)
            playlistViewModel.recordPlay(startId)
            if (!wide) {
                select(NavSection.NowPlaying)
                navController.openPlayer()
            }
        }

        fun onSection(destination: NavSection) {
            select(destination)
            when (destination) {
                NavSection.Songs -> {
                    if (route != SongsRoute) navController.openSection(SongsRoute)
                }
                NavSection.Playlists -> {
                    val inPlaylists = route == PlaylistsRoute || route == PlaylistDetailRoute
                    if (!inPlaylists) navController.openSection(PlaylistsRoute)
                }
                NavSection.NowPlaying -> {
                    if (!wide && route != PlayerRoute) navController.openPlayer()
                }
                NavSection.Devices -> {
                    if (route != DevicesRoute) navController.openSection(DevicesRoute)
                }
                NavSection.Favorites -> {
                    if (route != FavoritesRoute) navController.openSection(FavoritesRoute)
                }
                NavSection.Recent -> {
                    if (route != RecentRoute) navController.openSection(RecentRoute)
                }
                NavSection.Search -> {
                    if (route != SearchRoute) navController.openSection(SearchRoute)
                }
                NavSection.Settings -> {
                    if (route != SettingsRoute) navController.openSection(SettingsRoute)
                }
                NavSection.Videos -> {
                    if (route != VideosRoute) navController.openSection(VideosRoute)
                }
            }
        }

        LaunchedEffect(route, wide) {
            val mapped = SectionRoutes.sectionFor(route) ?: return@LaunchedEffect
            if (mapped in sections) select(mapped)
        }
        LaunchedEffect(wide) {
            if (wide && navController.currentDestination?.route == PlayerRoute) {
                navController.popBackStack()
            }
            // Unfolding on Now Playing: keep the fallback the ring already shows.
            if (carouselSection != section) onSection(carouselSection)
            // Mid-video: folded, the box is on Videos; unfolded, the list sits by the pane.
            // Fullscreen stays fullscreen: turning sideways changes the width too.
            if (navController.currentDestination?.route != VideoFullscreenRoute &&
                PlaybackArbiter.showVideosAfterLayoutChange(videoViewModel.uiState.value.isActive)
            ) {
                select(NavSection.Videos)
                if (navController.currentDestination?.route != VideosRoute) {
                    navController.openSection(VideosRoute)
                }
            }
        }

        // The video's notification opens the app where the video is.
        LaunchedEffect(openVideoRequests) {
            openVideoRequests.collect {
                val current = navController.currentDestination?.route
                val show = PlaybackArbiter.showVideosOnSessionTap(
                    videoActive = videoViewModel.uiState.value.isActive,
                    fullscreen = current == VideoFullscreenRoute,
                )
                if (show) {
                    select(NavSection.Videos)
                    if (current != VideosRoute) navController.openSection(VideosRoute)
                }
            }
        }

        // A video opened from another app (Files, Gallery) plays straight into fullscreen;
        // Back from fullscreen lands on Videos when folded.
        LaunchedEffect(externalVideos) {
            externalVideos.collect { video ->
                playVideo(video)
                openFullscreen()
            }
        }

        // Turning the phone sideways while the compact box plays goes fullscreen, like YouTube.
        // After the width effect above, so its "land on Videos" never undoes this.
        LaunchedEffect(landscape) {
            val was = previousLandscape.value
            previousLandscape.value = landscape
            val enter = PlaybackArbiter.enterFullscreenOnRotate(
                wasLandscape = was,
                isLandscape = landscape,
                compactDevice = compactDevice,
                videoActive = videoViewModel.uiState.value.isActive,
                onVideos = navController.currentDestination?.route == VideosRoute,
            )
            if (enter) openFullscreen()
        }

        val miniBar: @Composable () -> Unit = {
            NowPlayingBar(
                state = playerState,
                onOpen = {
                    // Two-pane already shows Now Playing on the right.
                    if (!wide) {
                        select(NavSection.NowPlaying)
                        navController.openPlayer()
                    }
                },
                onTogglePlayPause = playerViewModel::togglePlayPause,
                route = playbackRoute.presentation,
                isFavorite = playingIsFavorite,
                onToggleFavorite = {
                    playingTrackId?.let(playlistViewModel::toggleFavorite)
                },
                onOpenDevices = { showDevices = true },
                padForNavigationBars = false,
                bottomPadding = MiniPlayerCarouselGap,
            )
        }
        val carouselBar: @Composable (Modifier) -> Unit = { carouselModifier ->
            NavCarousel(
                selected = carouselSection,
                onSelect = { destination -> onSection(destination) },
                sections = sections,
                modifier = carouselModifier.windowInsetsPadding(
                    WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
                ),
            )
        }
        val playerPane: @Composable (Dp) -> Unit = { extraBottom ->
            val paneModifier = Modifier
                .fillMaxHeight()
                .padding(start = 8.dp, top = 16.dp, end = 16.dp, bottom = extraBottom)
                .clip(RoundedCornerShape(28.dp))
            val engine = videoEngine
            // Loaded, playing or paused: the video pane on the Videos tab, Now Playing elsewhere.
            RightPane(
                showVideo = PlaybackArbiter.showVideoPane(
                    videoState.isActive,
                    onVideos = SectionRoutes.sectionFor(route) == NavSection.Videos,
                ),
                video = {
                    VideoPane(
                        state = videoState,
                        onTogglePlayPause = videoViewModel::togglePlayPause,
                        onSeek = videoViewModel::seekTo,
                        onClose = ::closeVideo,
                        // Fullscreen owns the picture while it is open; one view is attached at a time.
                        surface = { if (!fullscreen) engine?.let { VideoSurface(it) } },
                        onFullscreen = ::openFullscreen,
                        onOpenTracks = openTracks,
                        holdControls = tracksOpen,
                        onStartOver = videoViewModel::startOver,
                        modifier = paneModifier,
                    )
                },
                nowPlaying = {
                    PlayerScreen(
                        state = playerState,
                        onBack = {},
                        onTogglePlayPause = playerViewModel::togglePlayPause,
                        onNext = playerViewModel::skipNext,
                        onPrevious = playerViewModel::skipPrevious,
                        onSeek = playerViewModel::seekTo,
                        selectedOutputId = activeOutputId,
                        onSelectOutput = playerViewModel::setOutputDevice,
                        onToggleShuffle = playerViewModel::toggleShuffle,
                        onCycleRepeat = playerViewModel::cycleRepeat,
                        showBack = false,
                        includeNavigationBars = false,
                        modifier = paneModifier,
                    )
                },
            )
        }

        // While in picture-in-picture only the video is drawn, so a single VideoSurface exists.
        if (pipOnly) {
            PipVideoLayout(surface = { videoEngine?.let { VideoSurface(it) } })
        } else {
        // Under fullscreen the layout stays composed (the NavHost keeps its state) but silent.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (fullscreen) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
        if (!wide) {
            SectionScaffold(
                modifier = Modifier.fillMaxSize(),
                showMiniPlayer = showMini,
                // Stays on Now Playing too, so any section is one tap away.
                showCarousel = true,
                contentBottomPadding = SongListBottomPadding,
                miniPlayer = miniBar,
                carousel = { carouselBar(Modifier) },
            ) { bottomPadding ->
                AppNavHost(
                    navController = navController,
                    libraryViewModel = libraryViewModel,
                    playerViewModel = playerViewModel,
                    playlistViewModel = playlistViewModel,
                    playerState = playerState,
                    favoriteIds = favoriteIds,
                    libraryTracks = libraryState.tracks,
                    tracksById = tracksById,
                    recentTracks = recentTracks,
                    recentSearches = recentSearches,
                    darkTheme = darkTheme,
                    appBackground = appBackground,
                    backgroundColors = backgroundColors,
                    onSelectBackground = backgroundViewModel::select,
                    onBackgroundColors = backgroundViewModel::setColors,
                    onResetBackgroundColors = backgroundViewModel::resetColors,
                    wide = false,
                    contentBottomPadding = bottomPadding,
                    activeOutputId = activeOutputId,
                    outputs = outputs,
                    onPlayQueue = { tracks, startId, context -> playQueue(tracks, startId, context) },
                    onOpenPlayer = {
                        select(NavSection.NowPlaying)
                        navController.openPlayer()
                    },
                    onAddToPlaylist = { pendingTrack = it },
                    videoState = videoState,
                    videoEngine = videoEngine,
                    onPlayVideo = ::playVideo,
                    videoViewModel = videoViewModel,
                    onCloseVideo = ::closeVideo,
                    onFullscreen = ::openFullscreen,
                    onOpenTracks = openTracks,
                    holdControls = tracksOpen,
                    brightnessSwipe = brightnessSwipe,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            // One tree for both chrome layouts, so the NavHost (and the left pane's state)
            // stays put when the mini player hides, as it does while a video is active.
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight(),
                    ) {
                        ListViewport(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                        ) {
                        AppNavHost(
                            navController = navController,
                            libraryViewModel = libraryViewModel,
                            playerViewModel = playerViewModel,
                            playlistViewModel = playlistViewModel,
                            playerState = playerState,
                            favoriteIds = favoriteIds,
                            libraryTracks = libraryState.tracks,
                            tracksById = tracksById,
                            recentTracks = recentTracks,
                            recentSearches = recentSearches,
                            darkTheme = darkTheme,
                            appBackground = appBackground,
                            backgroundColors = backgroundColors,
                            onSelectBackground = backgroundViewModel::select,
                            onBackgroundColors = backgroundViewModel::setColors,
                            onResetBackgroundColors = backgroundViewModel::resetColors,
                            wide = true,
                            contentBottomPadding = SongListBottomPadding,
                            activeOutputId = activeOutputId,
                            outputs = outputs,
                            onPlayQueue = { tracks, startId, context -> playQueue(tracks, startId, context) },
                            onOpenPlayer = {},
                            onAddToPlaylist = { pendingTrack = it },
                            videoState = videoState,
                            videoEngine = videoEngine,
                            onPlayVideo = ::playVideo,
                            videoViewModel = videoViewModel,
                            onCloseVideo = ::closeVideo,
                            onFullscreen = ::openFullscreen,
                            onOpenTracks = openTracks,
                            holdControls = tracksOpen,
                            brightnessSwipe = brightnessSwipe,
                            modifier = Modifier.fillMaxSize(),
                        )
                        }
                        // Without a mini player the carousel stays under the list column.
                        if (!showMini) {
                            ChromeSlot {
                                carouselBar(
                                    Modifier.padding(
                                        top = TwoPaneCarouselPadding,
                                        bottom = TwoPaneCarouselPadding,
                                    ),
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                            .then(if (showMini) Modifier else Modifier.navigationBarsPadding()),
                    ) {
                        playerPane(if (showMini) SongListBottomPadding else TwoPanePlayerBottomPadding)
                    }
                }
                // With a loaded track the same chrome is window-wide under the split.
                if (showMini) {
                    ChromeSlot {
                        miniBar()
                        carouselBar(Modifier)
                    }
                }
            }
        }

        }

        // Composed after the NavHost, so its callback is newer and wins: Back closes the
        // two-pane video before it pops the left pane. Fullscreen's own handler comes later.
        BackHandler(
            enabled = !fullscreen && PlaybackArbiter.onBack(videoState.isActive, twoPane = wide).closeVideo,
        ) {
            closeVideo()
        }

        if (fullscreen) {
            val engine = videoEngine
            FullscreenVideo(
                state = videoState,
                brightnessSwipe = brightnessSwipe,
                onTogglePlayPause = videoViewModel::togglePlayPause,
                onSeek = videoViewModel::seekTo,
                onCycleAspect = videoViewModel::cycleAspectMode,
                onExit = ::exitFullscreen,
                surface = { engine?.let { VideoSurface(it) } },
                onOpenTracks = openTracks,
                holdControls = tracksOpen,
                onStartOver = videoViewModel::startOver,
            )
        }
        }
    }

    if (tracksOpen && !pipOnly) {
        TrackSheet(
            state = trackSheet,
            actions = TrackSheetActions(
                onSelectAudio = videoViewModel::selectAudioTrack,
                onSelectSubtitle = videoViewModel::selectSubtitleTrack,
                onStepAudioDelay = videoViewModel::stepAudioDelay,
                onStepSubtitleDelay = videoViewModel::stepSubtitleDelay,
                onTextSize = videoViewModel::setSubtitleTextSize,
                onHardwareDecoding = videoViewModel::setHardwareDecoding,
                onLoadFile = videoViewModel::loadExternalTrack,
                onPickerShown = videoViewModel::setFilePickerShown,
            ),
            onDismiss = {
                showTracks = false
                videoViewModel.dismissTrackMessage()
            },
        )
    }
    // A closed video takes the sheet with it, so the next one does not open with it up.
    LaunchedEffect(videoState.isActive) {
        if (!videoState.isActive) showTracks = false
    }

    if (showDevices) {
        OutputPickerSheet(
            outputs = outputs,
            selectedId = playbackRoute.activeDeviceId,
            onSelect = { deviceId -> playerViewModel.setOutputDevice(deviceId) },
            // The sheet is only the mini player's shortcut; it never owns the selection.
            onDismiss = { showDevices = false },
        )
    }
    pendingTrack?.let { track ->
        AddToPlaylistSheet(
            trackTitle = track.title,
            playlists = playlistState.playlists.map { it.toCard(tracksById) },
            onPick = { playlistId ->
                playlistViewModel.addSong(playlistId, track.id)
                pendingTrack = null
            },
            onCreate = { name ->
                playlistViewModel.create(name) { playlistId ->
                    playlistViewModel.addSong(playlistId, track.id)
                }
                pendingTrack = null
            },
            onDismiss = { pendingTrack = null },
        )
    }
    }
}
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel,
    playerState: PlayerUiState,
    favoriteIds: Set<Long>,
    libraryTracks: List<Track>,
    tracksById: Map<Long, Track>,
    recentTracks: List<Track>,
    recentSearches: List<String>,
    darkTheme: Boolean,
    appBackground: AppBackground,
    backgroundColors: Map<String, List<Long>>,
    onSelectBackground: (AppBackground) -> Unit,
    onBackgroundColors: (String, List<Long>) -> Unit,
    onResetBackgroundColors: (String) -> Unit,
    wide: Boolean,
    contentBottomPadding: Dp,
    activeOutputId: Int?,
    outputs: List<AudioOutput>,
    onPlayQueue: (List<Track>, Long, ShuffleContext) -> Unit,
    onOpenPlayer: () -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    videoState: VideoUiState,
    videoEngine: VideoEngine?,
    onPlayVideo: (Video) -> Unit,
    videoViewModel: VideoViewModel,
    onCloseVideo: () -> Unit,
    onFullscreen: () -> Unit,
    onOpenTracks: () -> Unit,
    holdControls: Boolean,
    brightnessSwipe: Boolean,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = SongsRoute,
        modifier = modifier,
    ) {
        composable(SongsRoute) {
            LibraryScreen(
                viewModel = libraryViewModel,
                nowPlaying = playerState,
                onTrackSelected = { tracks, trackId ->
                    onPlayQueue(tracks, trackId, ShuffleContext.ALL_SONGS)
                },
                onOpenPlayer = onOpenPlayer,
                onTogglePlayPause = playerViewModel::togglePlayPause,
                onAddToPlaylist = onAddToPlaylist,
                favoriteIds = favoriteIds,
                onToggleFavorite = { playlistViewModel.toggleFavorite(it.id) },
                showMiniPlayer = false,
                contentBottomPadding = contentBottomPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(PlaylistsRoute) {
            PlaylistsScreen(
                viewModel = playlistViewModel,
                tracksById = tracksById,
                onOpen = { id ->
                    navController.navigate("playlist/$id") { launchSingleTop = true }
                },
                contentBottomPadding = contentBottomPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(
            route = PlaylistDetailRoute,
            arguments = listOf(navArgument(PlaylistIdArg) { type = NavType.LongType }),
        ) { entry ->
            val playlistId = entry.arguments?.getLong(PlaylistIdArg) ?: return@composable
            PlaylistDetailScreen(
                playlistId = playlistId,
                viewModel = playlistViewModel,
                tracksById = tracksById,
                onBack = { navController.popBackStack() },
                onPlay = { tracks, startId ->
                    onPlayQueue(tracks, startId, ShuffleContext.PLAYLIST)
                },
                onShuffle = { tracks ->
                    // Shuffle mode on, so the like-weighted shuffle picks everything after the first song.
                    LikeShufflePicker(tracks.map { it.id }, favoriteIds).draw()?.let { startId ->
                        playerViewModel.shuffleNextQueue()
                        onPlayQueue(tracks, startId, ShuffleContext.PLAYLIST)
                    }
                },
                contentBottomPadding = contentBottomPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(DevicesRoute) {
            DevicesScreen(
                outputs = outputs,
                selectedId = activeOutputId,
                onSelect = playerViewModel::setOutputDevice,
                contentBottomPadding = contentBottomPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(FavoritesRoute) {
            FavoritesScreen(
                tracks = libraryTracks,
                favoriteIds = favoriteIds,
                nowPlaying = playerState,
                onTrackSelected = { tracks, startId ->
                    onPlayQueue(tracks, startId, ShuffleContext.ALL_SONGS)
                },
                onToggleFavorite = { playlistViewModel.toggleFavorite(it.id) },
                onAddToPlaylist = onAddToPlaylist,
                contentBottomPadding = contentBottomPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(RecentRoute) {
            RecentScreen(
                tracks = recentTracks,
                favoriteIds = favoriteIds,
                nowPlaying = playerState,
                onTrackSelected = { tracks, startId ->
                    onPlayQueue(tracks, startId, ShuffleContext.ALL_SONGS)
                },
                onToggleFavorite = { playlistViewModel.toggleFavorite(it.id) },
                onAddToPlaylist = onAddToPlaylist,
                contentBottomPadding = contentBottomPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(SearchRoute) {
            SearchScreen(
                tracks = libraryTracks,
                favoriteIds = favoriteIds,
                nowPlaying = playerState,
                onTrackSelected = { tracks, startId ->
                    onPlayQueue(tracks, startId, ShuffleContext.ALL_SONGS)
                },
                onToggleFavorite = { playlistViewModel.toggleFavorite(it.id) },
                onAddToPlaylist = onAddToPlaylist,
                recentSearches = recentSearches,
                onSearchCommitted = playlistViewModel::recordSearch,
                contentBottomPadding = contentBottomPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(SettingsRoute) {
            SettingsScreen(
                darkTheme = darkTheme,
                onDarkThemeChange = playlistViewModel::setDarkTheme,
                backgroundName = stringResource(backgroundTitleRes(appBackground.titleId())),
                onOpenBackgrounds = {
                    navController.navigate(BackgroundsRoute) { launchSingleTop = true }
                },
                onOpenLicenses = {
                    navController.navigate(LicensesRoute) { launchSingleTop = true }
                },
                brightnessSwipe = brightnessSwipe,
                onBrightnessSwipeChange = videoViewModel::setBrightnessSwipe,
                onOpenErrorLog = {
                    navController.navigate(ErrorLogRoute) { launchSingleTop = true }
                },
                contentBottomPadding = contentBottomPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(ErrorLogRoute) {
            ErrorLogScreen(
                onBack = { navController.popBackStack() },
                contentBottomPadding = contentBottomPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(BackgroundsRoute) {
            BackgroundPickerScreen(
                selected = appBackground,
                darkThemeOn = darkTheme,
                customColors = backgroundColors,
                onSelect = onSelectBackground,
                onColorsChange = onBackgroundColors,
                onResetColors = onResetBackgroundColors,
                onBack = { navController.popBackStack() },
                contentBottomPadding = contentBottomPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(LicensesRoute) {
            LicensesScreen(
                onBack = { navController.popBackStack() },
                contentBottomPadding = contentBottomPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(VideosRoute) {
            // The video library (and its permission request) lives only in this destination.
            // Folded, the video plays in a box above the list; unfolded, in the right pane.
            val savedProgress by videoViewModel.savedProgress.collectAsStateWithLifecycle()
            VideosScreen(
                onVideoClick = onPlayVideo,
                contentBottomPadding = contentBottomPadding,
                video = videoState,
                savedProgress = savedProgress,
                onStartOver = videoViewModel::startOver,
                videoEngine = videoEngine,
                onTogglePlayPause = videoViewModel::togglePlayPause,
                onSeek = videoViewModel::seekTo,
                onCloseVideo = onCloseVideo,
                onFullscreen = onFullscreen,
                onOpenTracks = onOpenTracks,
                holdControls = holdControls,
                showInlinePlayer = !wide,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(VideoFullscreenRoute) {
            // MusicApp draws fullscreen over the whole window; this entry only holds the route,
            // so Back pops to wherever it was opened from.
            Box(modifier = Modifier.fillMaxSize())
        }
        composable(PlayerRoute) {
            if (!wide) {
                PlayerScreen(
                    state = playerState,
                    onBack = { navController.popBackStack() },
                    onTogglePlayPause = playerViewModel::togglePlayPause,
                    onNext = playerViewModel::skipNext,
                    onPrevious = playerViewModel::skipPrevious,
                    onSeek = playerViewModel::seekTo,
                    selectedOutputId = activeOutputId,
                    onSelectOutput = playerViewModel::setOutputDevice,
                    onToggleShuffle = playerViewModel::toggleShuffle,
                    onCycleRepeat = playerViewModel::cycleRepeat,
                    // The carousel under it owns the navigation-bar inset.
                    includeNavigationBars = false,
                    contentBottomPadding = contentBottomPadding,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun NavHostController.openSection(route: String) {
    // Now Playing sits on top of a section but is not part of it. Saved with the
    // section, it would come back on restore, and two-pane draws nothing for it.
    if (currentDestination?.route == PlayerRoute) popBackStack()
    navigate(route) {
        popUpTo(SongsRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.openPlayer() {
    navigate(PlayerRoute) { launchSingleTop = true }
}
