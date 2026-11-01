package com.shibi.musicplayer.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.net.toUri
import com.shibi.musicplayer.R
import com.shibi.musicplayer.diagnostics.ErrorFacts
import com.shibi.musicplayer.domain.FeedbackEmail

internal fun feedbackIntent(body: String): Intent =
    Intent(Intent.ACTION_SENDTO, "mailto:${FeedbackEmail.Address}".toUri())
        .putExtra(Intent.EXTRA_SUBJECT, FeedbackEmail.Subject)
        .putExtra(Intent.EXTRA_TEXT, body)

/**
 * Opens the user's email app on a prefilled message. Starting the activity needs no `<queries>`
 * entry (only resolveActivity would); a missing email app gets a toast with the address.
 */
internal fun sendFeedback(context: Context) {
    val app = ErrorFacts.app(context)
    val body = FeedbackEmail.body(
        prompt = context.getString(R.string.feedback_email_prompt),
        versionName = app.versionName,
        versionCode = app.versionCode,
        manufacturer = Build.MANUFACTURER.orEmpty(),
        model = Build.MODEL.orEmpty(),
        androidRelease = Build.VERSION.RELEASE.orEmpty(),
        sdkInt = Build.VERSION.SDK_INT,
    )
    try {
        context.startActivity(feedbackIntent(body))
    } catch (missing: ActivityNotFoundException) {
        Toast.makeText(
            context,
            context.getString(R.string.settings_feedback_no_email, FeedbackEmail.Address),
            Toast.LENGTH_LONG,
        ).show()
    }
}
