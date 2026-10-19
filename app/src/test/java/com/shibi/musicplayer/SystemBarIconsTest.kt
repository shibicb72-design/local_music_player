package com.shibi.musicplayer

import android.app.Application
import android.content.ComponentName
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.background.BackgroundViewModel
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.playback.PlaybackService
import com.shibi.musicplayer.playlist.PlaylistViewModel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The real activity: status and navigation icons follow the app's backdrop,
 * whatever the system night mode says.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SystemBarIconsTest {

    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule { base, _ ->
            object : Statement() {
                override fun evaluate() {
                    val app = ApplicationProvider.getApplicationContext<Application>()
                    shadowOf(app).declareComponentUnbindable(ComponentName(app, PlaybackService::class.java))
                    base.evaluate()
                }
            }
        }
        .around(compose)

    @Test
    @Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
    fun appDarkOnLightSystemDrawsLightIcons() {
        start()
        setApp(darkTheme = true, background = AppBackground.Solid("blush"))
        awaitIcons(dark = false)
    }

    /** Pastel gives dark icons; turning dark theme back on flips them to light again. */
    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun appPastelOnDarkSystemDrawsDarkIcons() {
        start()
        setApp(darkTheme = false, background = AppBackground.Solid("blush"))
        awaitIcons(dark = true)
        setApp(darkTheme = true, background = AppBackground.Solid("blush"))
        awaitIcons(dark = false)
    }

    /** Dark theme off over the Default backdrop still paints dark, so icons stay light. */
    @Test
    @Config(qualifiers = "w900dp-h600dp-notnight-xhdpi")
    fun appLightThemeOnDefaultBackdropKeepsLightIconsUnfolded() {
        start()
        setApp(darkTheme = false, background = AppBackground.Default)
        awaitIcons(dark = false)
    }

    /** Leave the app defaults (dark, Default backdrop) for the screenshot tests that follow. */
    @After
    fun restoreDefaults() {
        setApp(darkTheme = true, background = AppBackground.Default)
        // Dark theme hides the backdrop from the icons, so wait on the stored values themselves.
        val restored = runCatching {
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.mainClock.advanceTimeByFrame()
                var done = false
                compose.activityRule.scenario.onActivity { activity ->
                    val provider = ViewModelProvider(activity)
                    done = provider[PlaylistViewModel::class.java].darkTheme.value &&
                        provider[BackgroundViewModel::class.java].selection.value == AppBackground.Default
                }
                done
            }
        }.isSuccess
        assertTrue("defaults were not restored for the next test", restored)
        awaitIcons(dark = false)
    }

    /** Settings live in a process-wide Room/DataStore, so every test sets both explicitly. */
    private fun setApp(darkTheme: Boolean, background: AppBackground) {
        compose.activityRule.scenario.onActivity { activity ->
            val provider = ViewModelProvider(activity)
            provider[PlaylistViewModel::class.java].setDarkTheme(darkTheme)
            provider[BackgroundViewModel::class.java].select(background)
        }
    }

    private fun start() {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    /** Room and DataStore answer on their own threads; step a frame per poll. */
    private fun awaitIcons(dark: Boolean) {
        var last = "never read"
        val matched = runCatching {
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.mainClock.advanceTimeByFrame()
                var ok = false
                compose.activityRule.scenario.onActivity { activity ->
                    val window = activity.window
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    val status = controller.isAppearanceLightStatusBars
                    val navigation = controller.isAppearanceLightNavigationBars
                    last = "lightStatus=$status lightNavigation=$navigation"
                    ok = status == dark && navigation == dark
                }
                ok
            }
        }.isSuccess
        assertTrue("expected ${if (dark) "dark" else "light"} icons, got $last", matched)
    }
}
