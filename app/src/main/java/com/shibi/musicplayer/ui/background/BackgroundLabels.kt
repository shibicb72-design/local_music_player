package com.shibi.musicplayer.ui.background

import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.BackgroundCatalog

internal fun backgroundTitleRes(id: String): Int = when (id) {
    "blush" -> R.string.bg_blush
    "peach" -> R.string.bg_peach
    "butter" -> R.string.bg_butter
    "mint" -> R.string.bg_mint
    "sage" -> R.string.bg_sage
    "sky" -> R.string.bg_sky
    "periwinkle" -> R.string.bg_periwinkle
    "lavender" -> R.string.bg_lavender
    "lilac" -> R.string.bg_lilac
    "rose" -> R.string.bg_rose
    "coral" -> R.string.bg_coral
    "apricot" -> R.string.bg_apricot
    "seafoam" -> R.string.bg_seafoam
    "powder_blue" -> R.string.bg_powder_blue
    "mauve" -> R.string.bg_mauve
    "cream" -> R.string.bg_cream
    "cotton_candy" -> R.string.bg_cotton_candy
    "sunrise" -> R.string.bg_sunrise
    "mermaid" -> R.string.bg_mermaid
    "sorbet" -> R.string.bg_sorbet
    "twilight" -> R.string.bg_twilight
    "peachy_keen" -> R.string.bg_peachy_keen
    "lilac_mist" -> R.string.bg_lilac_mist
    "mint_cream" -> R.string.bg_mint_cream
    "waves" -> R.string.bg_waves
    "bubbles" -> R.string.bg_bubbles
    "clouds" -> R.string.bg_clouds
    "sparkles" -> R.string.bg_sparkles
    "petals" -> R.string.bg_petals
    "aurora" -> R.string.bg_aurora
    else -> R.string.bg_default
}

internal fun slotLabelRes(key: String): Int = when (key) {
    "sky" -> R.string.bg_slot_sky
    "water" -> R.string.bg_slot_water
    "foam" -> R.string.bg_slot_foam
    "bubble" -> R.string.bg_slot_bubble
    "shine" -> R.string.bg_slot_shine
    "cloud" -> R.string.bg_slot_cloud
    "blush" -> R.string.bg_slot_blush
    "dusk" -> R.string.bg_slot_dusk
    "star" -> R.string.bg_slot_star
    "glow" -> R.string.bg_slot_glow
    "petal" -> R.string.bg_slot_petal
    "stem" -> R.string.bg_slot_stem
    "night" -> R.string.bg_slot_night
    "rose" -> R.string.bg_slot_rose
    "mint" -> R.string.bg_slot_mint
    "lilac" -> R.string.bg_slot_lilac
    else -> R.string.bg_slot_color
}

internal fun AppBackground.titleId(): String = when (this) {
    AppBackground.Default -> AppBackground.ID_DEFAULT
    is AppBackground.Solid -> swatchId
    is AppBackground.Gradient -> blendId
    is AppBackground.Animated -> sceneId
}

internal fun AppBackground.previewColors(
    custom: Map<String, List<Long>>,
): List<Long> = BackgroundCatalog.colorsFor(this, custom)
