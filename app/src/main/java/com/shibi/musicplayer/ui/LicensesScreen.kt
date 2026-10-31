package com.shibi.musicplayer.ui

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.OpenSourceNotices
import com.shibi.musicplayer.ui.background.ContrastSurface

private const val NoticeType = "notice"
private const val LicenseTextType = "license_text"

/**
 * Settings > About > Open-source licences. libVLC's LGPL-2.1 notice with its
 * source links, the font credits, and the full licence texts. Every card is a
 * [ContrastSurface], so text and links follow the card, never the backdrop.
 */
@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    contentBottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val libVlcVersion = stringResource(R.string.libvlc_version)
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
                    text = stringResource(R.string.licenses_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = scheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.licenses_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = contentBottomPadding + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "libvlc", contentType = NoticeType) {
                NoticeCard(
                    title = stringResource(R.string.licenses_libvlc_title, libVlcVersion),
                    body = stringResource(R.string.licenses_libvlc_body),
                ) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.licenses_source_heading),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    LinkLine(
                        label = stringResource(R.string.licenses_source_jar, libVlcVersion),
                        url = OpenSourceNotices.libVlcSourcesJarUrl(libVlcVersion),
                    )
                    LinkLine(
                        label = stringResource(R.string.licenses_source_libvlcjni),
                        url = OpenSourceNotices.LibVlcJniUrl,
                    )
                    LinkLine(
                        label = stringResource(R.string.licenses_source_vlc),
                        url = OpenSourceNotices.VlcUrl,
                    )
                }
            }
            item(key = "fonts", contentType = NoticeType) {
                NoticeCard(
                    title = stringResource(R.string.licenses_fonts_title),
                    body = stringResource(R.string.licenses_fonts_body),
                )
            }
            item(key = "libraries", contentType = NoticeType) {
                NoticeCard(
                    title = stringResource(R.string.licenses_libraries_title),
                    body = stringResource(R.string.licenses_libraries_body),
                ) {
                    LinkLine(
                        label = stringResource(R.string.licenses_apache_link),
                        url = OpenSourceNotices.ApacheUrl,
                    )
                }
            }
            item(key = "lgpl", contentType = LicenseTextType) {
                LicenseTextCard(
                    title = stringResource(R.string.licenses_lgpl_title),
                    textRes = R.raw.lgpl_2_1,
                )
            }
            item(key = "ofl", contentType = LicenseTextType) {
                LicenseTextCard(
                    title = stringResource(R.string.licenses_ofl_title),
                    textRes = R.raw.ofl_1_1,
                )
            }
        }
    }
}

@Composable
private fun NoticeCard(
    title: String,
    body: String,
    extra: @Composable () -> Unit = {},
) {
    ContrastSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            extra()
        }
    }
}

/** An underlined link in the card's own content colour, so it keeps the card's contrast. */
@Composable
private fun LinkLine(label: String, url: String) {
    val styles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.onSurface,
            textDecoration = TextDecoration.Underline,
        ),
    )
    Text(
        text = buildAnnotatedString {
            withLink(LinkAnnotation.Url(url, styles)) { append(label) }
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun LicenseTextCard(title: String, @RawRes textRes: Int) {
    val resources = LocalResources.current
    val paragraphs = remember(resources, textRes) {
        val text = resources.openRawResource(textRes).bufferedReader().use { it.readText() }
        OpenSourceNotices.paragraphs(text)
    }
    ContrastSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            paragraphs.forEach { paragraph ->
                Text(
                    text = paragraph,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
