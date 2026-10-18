package com.shibi.musicplayer.domain

/** The scale applied to a TextureView around its centre, one factor per axis. */
data class FitScale(val scaleX: Float, val scaleY: Float)

/**
 * Pure picture-fit math for a TextureView.
 *
 * A TextureView stretches the decoded buffer to its own size, so the identity transform (1, 1)
 * is [AspectMode.Stretch]. Every other mode is a scale around the view centre that maps the
 * buffer display aspect onto the view aspect.
 */
object VideoFit {

    /**
     * The scale to apply around the centre of a TextureView of [viewWidth] x [viewHeight] that
     * shows a [videoWidth] x [videoHeight] buffer with pixel aspect [sarNum]/[sarDen].
     * The TextureView already stretches the buffer to fill it, so (1, 1) is Stretch.
     */
    fun scale(
        mode: AspectMode,
        viewWidth: Int,
        viewHeight: Int,
        videoWidth: Int,
        videoHeight: Int,
        sarNum: Int = 1,
        sarDen: Int = 1,
    ): FitScale {
        if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            return FitScale(1f, 1f)
        }
        val sar = if (sarNum > 0 && sarDen > 0) sarNum.toFloat() / sarDen.toFloat() else 1f
        val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
        val displayAspect = videoWidth.toFloat() * sar / videoHeight.toFloat()
        return when (mode) {
            AspectMode.Fit -> fit(displayAspect, viewAspect)
            AspectMode.Fill -> fill(displayAspect, viewAspect)
            AspectMode.Stretch -> FitScale(1f, 1f)
            AspectMode.Ratio16x9 -> fit(16f / 9f, viewAspect)
            AspectMode.Ratio4x3 -> fit(4f / 3f, viewAspect)
            AspectMode.Original -> FitScale(
                videoWidth.toFloat() * sar / viewWidth.toFloat(),
                videoHeight.toFloat() / viewHeight.toFloat(),
            )
        }
    }

    private fun fit(displayAspect: Float, viewAspect: Float): FitScale =
        if (displayAspect > viewAspect) {
            FitScale(1f, viewAspect / displayAspect)
        } else {
            FitScale(displayAspect / viewAspect, 1f)
        }

    private fun fill(displayAspect: Float, viewAspect: Float): FitScale =
        if (displayAspect > viewAspect) {
            FitScale(displayAspect / viewAspect, 1f)
        } else {
            FitScale(1f, viewAspect / displayAspect)
        }
}
