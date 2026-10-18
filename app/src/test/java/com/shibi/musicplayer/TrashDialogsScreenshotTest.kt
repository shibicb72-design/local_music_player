package com.shibi.musicplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.MediaKind
import com.shibi.musicplayer.domain.TrashItem
import com.shibi.musicplayer.ui.GroupTrashWarning
import com.shibi.musicplayer.ui.TrashFailedDialog
import com.shibi.musicplayer.ui.background.AppBackgroundHost
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** V19 trash dialogs: the whole-list warning and the "couldn't move" list. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class TrashDialogsScreenshotTest {

    @Test
    fun groupWarning() {
        captureRoboImage("build/outputs/roborazzi/cv19_group_warning.png") {
            MusicPlayerTheme {
                AppBackgroundHost(AppBackground.Default, emptyMap(), forceStatic = true) {
                    GroupTrashWarning(count = 42, kind = MediaKind.Song, onConfirm = {}, onDismiss = {})
                }
            }
        }
    }

    @Test
    fun trashFailed() {
        val items = (1L..23L).map {
            TrashItem(MediaKind.Song, it, "content://media/external/audio/media/$it", "Track number $it")
        }
        captureRoboImage("build/outputs/roborazzi/cv19_trash_failed.png") {
            MusicPlayerTheme {
                AppBackgroundHost(AppBackground.Default, emptyMap(), forceStatic = true) {
                    TrashFailedDialog(items = items, onDismiss = {})
                }
            }
        }
    }
}
