package com.shibi.musicplayer.ui.background

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.BackgroundCatalog
import com.shibi.musicplayer.domain.BackgroundTab

@Composable
fun BackgroundPickerScreen(
    selected: AppBackground,
    customColors: Map<String, List<Long>>,
    onSelect: (AppBackground) -> Unit,
    onColorsChange: (String, List<Long>) -> Unit,
    onResetColors: (String) -> Unit,
    onBack: () -> Unit,
    contentBottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
    darkThemeOn: Boolean = false,
) {
    var tab by remember {
        mutableStateOf(
            when (selected) {
                is AppBackground.Gradient -> BackgroundTab.MULTICOLOR
                is AppBackground.Animated -> BackgroundTab.ANIMATED
                else -> BackgroundTab.SOLID
            },
        )
    }
    var fineTuneId by remember { mutableStateOf<String?>(null) }
    BackgroundPickerContent(
        tab = tab,
        onTabChange = { tab = it },
        selected = selected,
        customColors = customColors,
        onSelect = { background ->
            onSelect(background)
            if (background is AppBackground.Animated) {
                fineTuneId = background.sceneId
            }
        },
        onBack = onBack,
        contentBottomPadding = contentBottomPadding,
        modifier = modifier,
        darkThemeOn = darkThemeOn,
    )
    val sceneId = fineTuneId
    val scene = sceneId?.let { BackgroundCatalog.scene(it) }
    if (scene != null) {
        val colors = customColors[scene.id] ?: scene.defaults
        BackgroundFineTuneSheet(
            scene = scene,
            colors = colors,
            onColorAt = { index, color ->
                val next = colors.toMutableList()
                if (index in next.indices) next[index] = color
                onColorsChange(scene.id, next)
            },
            onReset = { onResetColors(scene.id) },
            onDismiss = { fineTuneId = null },
        )
    }
}

@Composable
internal fun BackgroundPickerContent(
    tab: BackgroundTab,
    onTabChange: (BackgroundTab) -> Unit,
    selected: AppBackground,
    customColors: Map<String, List<Long>>,
    onSelect: (AppBackground) -> Unit,
    onBack: () -> Unit,
    contentBottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
    forceStatic: Boolean = false,
    darkThemeOn: Boolean = false,
) {
    val play = rememberBackgroundMotion(forceStatic)
    val phase = rememberScenePhase(play)
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = scheme.onBackground,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_backgrounds),
                    style = MaterialTheme.typography.headlineMedium,
                    color = scheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Dark theme covers the backdrop, so say where the pick will show.
                Text(
                    text = stringResource(
                        if (darkThemeOn) {
                            R.string.settings_backgrounds_dark_on
                        } else {
                            R.string.settings_backgrounds_body
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SecondaryTabRow(
            selectedTabIndex = tab.ordinal,
            containerColor = Color.Transparent,
            contentColor = scheme.onBackground,
            divider = {},
        ) {
            BackgroundTab.entries.forEach { item ->
                Tab(
                    selected = item == tab,
                    onClick = { onTabChange(item) },
                    text = {
                        Text(
                            text = stringResource(tabLabelRes(item)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
        val tiles = remember(tab) { tilesFor(tab) }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = contentBottomPadding + 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tiles, key = { it.id }, contentType = { "background_tile" }) { tile ->
                BackgroundPreviewTile(
                    title = stringResource(backgroundTitleRes(tile.titleId())),
                    selected = tile == selected,
                    onClick = { onSelect(tile) },
                ) {
                    AppBackgroundLayer(
                        background = tile,
                        colors = tile.previewColors(customColors),
                        phase = { phase.value },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun BackgroundPreviewTile(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val chrome = LocalBackgroundChrome.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .clip(RoundedCornerShape(22.dp))
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) scheme.onBackground else chrome.cardBorder,
                    shape = RoundedCornerShape(22.dp),
                ),
        ) {
            preview()
            if (selected) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(28.dp),
                    shape = CircleShape,
                    color = scheme.onBackground,
                    contentColor = if (chrome.isLight) {
                        Color.White
                    } else {
                        Color(0xFF0E0E12)
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.bg_selected),
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = scheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun tilesFor(tab: BackgroundTab): List<AppBackground> = when (tab) {
    BackgroundTab.SOLID -> buildList {
        add(AppBackground.Default)
        BackgroundCatalog.solids.forEach { add(AppBackground.Solid(it.id)) }
    }
    BackgroundTab.MULTICOLOR -> BackgroundCatalog.blends.map { AppBackground.Gradient(it.id) }
    BackgroundTab.ANIMATED -> BackgroundCatalog.scenes.map { AppBackground.Animated(it.id) }
}

private fun tabLabelRes(tab: BackgroundTab): Int = when (tab) {
    BackgroundTab.SOLID -> R.string.bg_tab_solid
    BackgroundTab.MULTICOLOR -> R.string.bg_tab_multicolor
    BackgroundTab.ANIMATED -> R.string.bg_tab_animated
}
