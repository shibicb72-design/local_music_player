package com.shibi.musicplayer

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.background.BackgroundRepository
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.BackgroundCatalog
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
