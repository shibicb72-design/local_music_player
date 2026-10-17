package com.shibi.musicplayer

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.background.BackgroundRepository
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.BackgroundCatalog
import com.shibi.musicplayer.domain.shownWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackgroundRepositoryTest {
    private lateinit var repository: BackgroundRepository

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.cacheDir, "backgrounds-test.preferences_pb")
        if (file.exists()) file.delete()
        val store = PreferenceDataStoreFactory.create(produceFile = { file })
        repository = BackgroundRepository(store)
    }

    @Test
    fun defaultSelectionIsTheDarkBackdrop() = runBlocking {
        assertEquals(AppBackground.Default, repository.selection.first())
        assertTrue(repository.customColors.first().isEmpty())
    }

    @Test
    fun persistsSolidGradientAndAnimatedChoices() = runBlocking {
        repository.setBackground(AppBackground.Solid("mint"))
        assertEquals(AppBackground.Solid("mint"), repository.selection.first())

        repository.setBackground(AppBackground.Gradient("cotton_candy"))
        assertEquals(AppBackground.Gradient("cotton_candy"), repository.selection.first())

        repository.setBackground(AppBackground.Animated("waves"))
        assertEquals(AppBackground.Animated("waves"), repository.selection.first())

        repository.setBackground(AppBackground.Default)
        assertEquals(AppBackground.Default, repository.selection.first())
    }

    /**
     * Dark theme paints the dark default over the chosen backdrop but never
     * writes it. Turning dark off shows the stored choice again, fine-tune too.
     */
    @Test
    fun darkThemeOnThenOffRestoresTheLastChosenBackground() = runBlocking {
        val scene = BackgroundCatalog.scene("waves")!!
        val tuned = listOf(0xFFF8C8D4, 0xFFC8EDE0, 0xFFDCC8F0)
        repository.setBackground(AppBackground.Solid("mint"))
        repository.setBackground(AppBackground.Animated(scene.id))
        repository.setColors(scene.id, tuned)

        val chosen = repository.selection.first()
        assertEquals(AppBackground.Default, chosen.shownWith(darkTheme = true))
        assertEquals(AppBackground.Animated(scene.id), repository.selection.first())
        assertEquals(tuned, repository.customColors.first()[scene.id])

        val restored = repository.selection.first().shownWith(darkTheme = false)
        assertEquals(AppBackground.Animated(scene.id), restored)
        assertEquals(tuned, repository.customColors.first()[scene.id])
    }

    @Test
    fun darkThemeOffShowsEveryChoiceAsIs() {
        listOf(
            AppBackground.Default,
            AppBackground.Solid("blush"),
            AppBackground.Gradient("cotton_candy"),
            AppBackground.Animated("waves"),
        ).forEach { choice ->
            assertEquals(choice, choice.shownWith(darkTheme = false))
            assertEquals(AppBackground.Default, choice.shownWith(darkTheme = true))
        }
    }

    @Test
    fun customAnimationColorsPersistUntilReset() = runBlocking {
        val scene = BackgroundCatalog.scene("waves")!!
        val custom = listOf(0xFFF8C8D4, 0xFFC8EDE0, 0xFFDCC8F0)
        repository.setColors(scene.id, custom)
        assertEquals(custom, repository.customColors.first()[scene.id])

        repository.resetColors(scene.id)
        assertTrue(repository.customColors.first().isEmpty())
    }
}
