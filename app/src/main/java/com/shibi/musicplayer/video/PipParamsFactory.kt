package com.shibi.musicplayer.video

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.PipAspect

/**
 * Builds the picture-in-picture params for the video: the aspect ratio, the source rectangle
 * hint and the three controls (back 10 s, play or pause, forward 10 s). The controls are
 * broadcasts addressed to this package only, so no other app can drive the video.
 */
object PipParamsFactory {

    const val ACTION_TOGGLE = "com.shibi.musicplayer.pip.TOGGLE"
    const val ACTION_BACK = "com.shibi.musicplayer.pip.BACK_10"
    const val ACTION_FORWARD = "com.shibi.musicplayer.pip.FORWARD_10"

    const val SEEK_STEP_MS = 10_000L

    private const val REQUEST_BACK = 1
    private const val REQUEST_TOGGLE = 2
    private const val REQUEST_FORWARD = 3

    fun build(
        context: Context,
        playing: Boolean,
        aspect: Float?,
        sourceRect: Rect? = null,
    ): PictureInPictureParams {
        val ratio = PipAspect.ratioFor(0, 0, aspect)
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(ratio.numerator, ratio.denominator))
        if (sourceRect != null && !sourceRect.isEmpty) {
            builder.setSourceRectHint(sourceRect)
        }
        builder.setActions(
            listOf(
                action(
                    context = context,
                    titleRes = R.string.pip_back_10,
                    iconRes = android.R.drawable.ic_media_rew,
                    action = ACTION_BACK,
                    requestCode = REQUEST_BACK,
                ),
                action(
                    context = context,
                    titleRes = if (playing) R.string.pip_pause else R.string.pip_play,
                    iconRes = if (playing) {
                        android.R.drawable.ic_media_pause
                    } else {
                        android.R.drawable.ic_media_play
                    },
                    action = ACTION_TOGGLE,
                    requestCode = REQUEST_TOGGLE,
                ),
                action(
                    context = context,
                    titleRes = R.string.pip_forward_10,
                    iconRes = android.R.drawable.ic_media_ff,
                    action = ACTION_FORWARD,
                    requestCode = REQUEST_FORWARD,
                ),
            ),
        )
        if (Build.VERSION.SDK_INT >= 31) {
            // Auto-enter only while the video plays, so music-only use never enters PiP.
            builder.setAutoEnterEnabled(playing)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    fun supported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun action(
        context: Context,
        titleRes: Int,
        iconRes: Int,
        action: String,
        requestCode: Int,
    ): RemoteAction {
        val title = context.getString(titleRes)
        val intent = Intent(action).setPackage(context.packageName)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteAction(Icon.createWithResource(context, iconRes), title, title, pendingIntent)
    }
}
