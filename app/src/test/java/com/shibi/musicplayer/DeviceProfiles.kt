package com.shibi.musicplayer

/*
 * Robolectric qualifiers for real phone sizes (width and height in dp, density in dpi). Sources
 * are in docs/screenshots/layout-lists/DEVICES.md.
 */

/** Galaxy S24 Ultra at its default FHD+ display size: 1080 x 2340 px, DPR 2.8125. */
internal const val S24_ULTRA = "w384dp-h832dp-450dpi"

/** Galaxy S24 Ultra with Display size "smaller": DPR 2.625, the widest case. */
internal const val S24_ULTRA_SMALL = "w412dp-h892dp-420dpi"

/** Galaxy S25: DPR 3. */
internal const val S25 = "w360dp-h780dp-480dpi"

/** Galaxy S25 Ultra: DPR 2.8125, the same as the S24 Ultra's default. */
internal const val S25_ULTRA = "w384dp-h832dp-450dpi"

/** A generic narrow phone. */
internal const val NARROW = "w360dp-h640dp-480dpi"

/** A 412 x 915 dp phone at 500 dpi. */
internal const val WIDE412 = "w412dp-h915dp-500dpi"
