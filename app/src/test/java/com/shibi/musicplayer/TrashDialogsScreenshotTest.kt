package com.shibi.musicplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.MediaKind
import com.shibi.musicplayer.domain.TrashFailReason
import com.shibi.musicplayer.domain.TrashFailure
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

    private fun video(id: Long) =
        TrashItem(MediaKind.Video, id, "content://media/external/video/media/$id", "Clip number $id")

    @Test
    fun trashPartial() {
        val failures = listOf(
            TrashFailure(TrashFailReason.Removable, listOf(video(4))),
            TrashFailure(TrashFailReason.NoPermission, listOf(video(5))),
        )
        captureRoboImage("build/outputs/roborazzi/cv20_trash_partial.png") {
            MusicPlayerTheme {
                AppBackgroundHost(AppBackground.Default, emptyMap(), forceStatic = true) {
                    TrashFailedDialog(moved = 3, requested = 5, failures = failures, onDismiss = {})
                }
            }
        }
    }

    @Test
    fun trashNone() {
        val failures = listOf(TrashFailure(TrashFailReason.NotTrashed, listOf(video(1), video(2))))
        captureRoboImage("build/outputs/roborazzi/cv20_trash_none.png") {
            MusicPlayerTheme {
                AppBackgroundHost(AppBackground.Default, emptyMap(), forceStatic = true) {
                    TrashFailedDialog(moved = 0, requested = 2, failures = failures, onDismiss = {})
                }
            }
        }
    }
}
