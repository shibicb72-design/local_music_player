package com.shibi.musicplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/**
 * The app's single Back hook, on the navigation-event API that predictive back uses. Same
 * contract as the old BackHandler: while [enabled], the newest composed handler wins.
 */
@Composable
internal fun AppBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val state = rememberNavigationEventState(NavigationEventInfo.None)
    val latest by rememberUpdatedState(onBack)
    NavigationBackHandler(
        state = state,
        isBackEnabled = enabled,
        onBackCompleted = { latest() },
    )
}
