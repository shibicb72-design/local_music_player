package com.shibi.musicplayer.domain

/** How long the fast-scroll thumb stays visible after the last scroll or drag activity. */
const val FAST_SCROLL_HIDE_DELAY_MS = 2_000L

/** True while the thumb is being used, or for [FAST_SCROLL_HIDE_DELAY_MS] after the last activity. */
fun fastScrollVisible(active: Boolean, lastActiveAtMs: Long?, nowMs: Long): Boolean =
    active || (lastActiveAtMs != null && nowMs - lastActiveAtMs < FAST_SCROLL_HIDE_DELAY_MS)
