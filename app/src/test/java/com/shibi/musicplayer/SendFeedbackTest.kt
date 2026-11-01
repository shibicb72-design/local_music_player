package com.shibi.musicplayer

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.diagnostics.ErrorFacts
import com.shibi.musicplayer.domain.FeedbackEmail
import com.shibi.musicplayer.ui.SettingsScreen
import com.shibi.musicplayer.ui.feedbackIntent
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild
import org.robolectric.shadows.ShadowToast

/** Settings > Send feedback: the email it opens, and the toast when no email app is installed. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class SendFeedbackTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun theIntentIsAMailtoEmailWithTheSubjectAndTheBody() {
        val intent = feedbackIntent(body = "What happened")

        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto:shibicb.71@gmail.com", intent.dataString)
        assertEquals("Theatrum feedback", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("What happened", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertNull(intent.type)
        assertEquals(0, intent.flags)
        assertNull(intent.clipData)
        assertEquals(setOf(Intent.EXTRA_SUBJECT, Intent.EXTRA_TEXT), intent.extras?.keySet())
    }

    @Test
    fun tappingSendFeedbackOpensTheEmailWithThisBuildAndDevice() {
        ShadowBuild.setManufacturer("samsung")
        ShadowBuild.setModel("SM-F966B")
        ShadowBuild.setVersionRelease("15")
        showSettings()
        compose.onNodeWithText("Send feedback").performScrollTo().performClick()

        val started = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_SENDTO, started.action)
        assertEquals("mailto:shibicb.71@gmail.com", started.dataString)
        assertEquals("Theatrum feedback", started.getStringExtra(Intent.EXTRA_SUBJECT))
        val build = ErrorFacts.app(app)
        assertNotEquals("?", build.versionName)
        assertTrue(build.versionCode > 0)
        val expected = FeedbackEmail.body(
            prompt = app.getString(R.string.feedback_email_prompt),
            versionName = build.versionName,
            versionCode = build.versionCode,
            manufacturer = "samsung",
            model = "SM-F966B",
            androidRelease = "15",
            sdkInt = 35,
        )
        assertEquals(expected, started.getStringExtra(Intent.EXTRA_TEXT))
        assertNull(ShadowToast.getTextOfLatestToast())
    }

    // A landscape phone: the card starts below the fold, so performScrollTo really scrolls.
    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun withoutAnEmailAppATapShowsAToastWithTheAddress() {
        // Starting an activity that nothing resolves now throws ActivityNotFoundException.
        shadowOf(app).checkActivities(true)
        showSettings()
        compose.onNodeWithText("Send feedback").performScrollTo().performClick()

        assertNull(shadowOf(app).nextStartedActivity)
        assertEquals("No email app found. Write to shibicb.71@gmail.com", ShadowToast.getTextOfLatestToast())
        assertEquals(Toast.LENGTH_LONG, ShadowToast.getLatestToast().duration)
    }

    private fun showSettings() {
        compose.setContent {
            MusicPlayerTheme {
                SettingsScreen(
                    darkTheme = true,
                    onDarkThemeChange = {},
                    backgroundName = "",
                    onOpenBackgrounds = {},
                )
            }
        }
    }
}
