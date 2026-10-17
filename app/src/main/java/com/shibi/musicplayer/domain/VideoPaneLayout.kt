package com.shibi.musicplayer.domain

/**
 * The two-pane video pane: the picture fits the pane width (16:9 until known), with the
 * title and file details under it when there is room. A tall picture gives up height for
 * the details only while it keeps most of the pane; otherwise the details are dropped.
 */
object VideoPaneLayout {

    /** Title plus one details line and their padding, in dp. */
    const val DetailsHeight = 96f

    /** The picture never shrinks below this share of the pane to make room for the details. */
    const val MinVideoShare = 0.6f

    data class Result(val videoHeight: Float, val showDetails: Boolean)

    /** [width] and [height] are the pane's size in dp; [aspect] is width / height of the picture. */
    fun of(width: Float, height: Float, aspect: Float?): Result {
        val ratio = aspect?.takeIf { it.isFinite() && it > 0f } ?: InlineVideoBox.DefaultAspect
        val natural = (width / ratio).coerceAtLeast(0f)
        val room = height - DetailsHeight
        return when {
            natural <= room -> Result(natural, showDetails = true)
            room >= height * MinVideoShare && room > 0f -> Result(room, showDetails = true)
            else -> Result(minOf(natural, height).coerceAtLeast(0f), showDetails = false)
        }
    }
}
