package com.shibi.musicplayer

import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.BackgroundCatalog
import com.shibi.musicplayer.domain.BackgroundPersistence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundPersistenceTest {
    @Test
    fun defaultIsTheDarkBackdrop() {
        assertEquals(AppBackground.Default, BackgroundPersistence.decode(null))
        assertEquals(AppBackground.Default, BackgroundPersistence.decode(""))
        assertEquals(AppBackground.Default, BackgroundPersistence.decode("default"))
        assertEquals("default", BackgroundPersistence.encode(AppBackground.Default))
    }

    @Test
    fun everyCatalogEntryRoundTrips() {
        BackgroundCatalog.solids.forEach { swatch ->
            val encoded = BackgroundPersistence.encode(AppBackground.Solid(swatch.id))
            assertEquals(AppBackground.Solid(swatch.id), BackgroundPersistence.decode(encoded))
        }
        BackgroundCatalog.blends.forEach { blend ->
            val encoded = BackgroundPersistence.encode(AppBackground.Gradient(blend.id))
            assertEquals(AppBackground.Gradient(blend.id), BackgroundPersistence.decode(encoded))
        }
        BackgroundCatalog.scenes.forEach { scene ->
            val encoded = BackgroundPersistence.encode(AppBackground.Animated(scene.id))
            assertEquals(AppBackground.Animated(scene.id), BackgroundPersistence.decode(encoded))
        }
    }

    @Test
    fun unknownIdsFallBackToDefault() {
        assertEquals(AppBackground.Default, BackgroundPersistence.decode("solid:neon"))
        assertEquals(AppBackground.Default, BackgroundPersistence.decode("gradient:lava"))
        assertEquals(AppBackground.Default, BackgroundPersistence.decode("animated:disco"))
        assertEquals(AppBackground.Default, BackgroundPersistence.decode("theme:dark"))
    }

    @Test
    fun customColorsRoundTripAndPadToSlotCount() {
        val defaults = listOf(0xFFF8C8D4, 0xFFC5E4F7, 0xFFFFFFF6)
        val encoded = BackgroundPersistence.encodeColors(listOf(0xFFFFD4B8, 0xFFC8EDE0))
        val decoded = BackgroundPersistence.decodeColors(encoded, defaults)
        assertEquals(3, decoded.size)
        assertEquals(0xFFFFD4B8, decoded[0])
        assertEquals(0xFFC8EDE0, decoded[1])
        assertEquals(defaults[2], decoded[2])
    }

    @Test
    fun blankOrJunkColorsUseDefaults() {
        val defaults = BackgroundCatalog.scene("waves")!!.defaults
        assertEquals(defaults, BackgroundPersistence.decodeColors(null, defaults))
        assertEquals(defaults, BackgroundPersistence.decodeColors("   ", defaults))
        assertEquals(defaults, BackgroundPersistence.decodeColors("zzzz", defaults))
    }

    @Test
    fun colorsKeyIsPerAnimation() {
        assertEquals("anim_colors_waves", BackgroundPersistence.colorsKey("waves"))
        assertTrue(BackgroundPersistence.colorsKey("bubbles").startsWith("anim_colors_"))
    }

    @Test
    fun sixteenSolidsEightBlendsAndSixScenes() {
        assertEquals(16, BackgroundCatalog.solids.size)
        assertEquals(8, BackgroundCatalog.blends.size)
        assertEquals(6, BackgroundCatalog.scenes.size)
        assertEquals(
            listOf("waves", "bubbles", "clouds", "sparkles", "petals", "aurora"),
            BackgroundCatalog.scenes.map { it.id },
        )
    }

    @Test
    fun savedAnimalPrefsMigrateToDefault() {
        listOf("unicorn", "cat", "puppy", "bunnies", "whale", "panda").forEach { id ->
            assertEquals(
                id,
                AppBackground.Default,
                BackgroundPersistence.decode("${AppBackground.PREFIX_ANIMATED}$id"),
            )
        }
    }
}
