package com.shibi.musicplayer.domain

/**
 * User-chosen app backdrop. Separate from the dark/light theme flag.
 * Ids are stable persistence keys.
 */
sealed class AppBackground {
    abstract val id: String

    data object Default : AppBackground() {
        override val id: String = ID_DEFAULT
    }

    data class Solid(val swatchId: String) : AppBackground() {
        override val id: String = "$PREFIX_SOLID$swatchId"
    }

    data class Gradient(val blendId: String) : AppBackground() {
        override val id: String = "$PREFIX_GRADIENT$blendId"
    }

    data class Animated(val sceneId: String) : AppBackground() {
        override val id: String = "$PREFIX_ANIMATED$sceneId"
    }

    companion object {
        const val ID_DEFAULT = "default"
        const val PREFIX_SOLID = "solid:"
        const val PREFIX_GRADIENT = "gradient:"
        const val PREFIX_ANIMATED = "animated:"
    }
}

/**
 * The backdrop actually painted. Dark theme paints the dark default over any
 * choice; the stored choice (and its fine-tune colors) is never rewritten, so
 * turning dark off shows it again exactly.
 */
fun AppBackground.shownWith(darkTheme: Boolean): AppBackground =
    if (darkTheme) AppBackground.Default else this

enum class BackgroundTab {
    SOLID,
    MULTICOLOR,
    ANIMATED,
}

data class SolidPastel(
    val id: String,
    val argb: Long,
)

data class PastelBlend(
    val id: String,
    val colors: List<Long>,
)

data class AnimatedScene(
    val id: String,
    val defaults: List<Long>,
    val slotKeys: List<String>,
)

object BackgroundCatalog {
    val solids: List<SolidPastel> = listOf(
        SolidPastel("blush", 0xFFF8C8D4),
        SolidPastel("peach", 0xFFFFD4B8),
        SolidPastel("butter", 0xFFF7E6A1),
        SolidPastel("mint", 0xFFC8EDE0),
        SolidPastel("sage", 0xFFC5D9C3),
        SolidPastel("sky", 0xFFC5E4F7),
        SolidPastel("periwinkle", 0xFFC9C8F0),
        SolidPastel("lavender", 0xFFDCC8F0),
        SolidPastel("lilac", 0xFFE6D0F0),
        SolidPastel("rose", 0xFFF0C4D0),
        SolidPastel("coral", 0xFFF5C4B8),
        SolidPastel("apricot", 0xFFF8D4B8),
        SolidPastel("seafoam", 0xFFBFE8DC),
        SolidPastel("powder_blue", 0xFFD0E4F5),
        SolidPastel("mauve", 0xFFE0C8D8),
        SolidPastel("cream", 0xFFF5EDE0),
    )

    val blends: List<PastelBlend> = listOf(
        PastelBlend("cotton_candy", listOf(0xFFF8C8E0, 0xFFC8E4F8, 0xFFF0D0F0)),
        PastelBlend("sunrise", listOf(0xFFFFE0B8, 0xFFFFD0C8, 0xFFF8C8D8)),
        PastelBlend("mermaid", listOf(0xFFB8E8E0, 0xFFC8E0F8, 0xFFD8C8F0)),
        PastelBlend("sorbet", listOf(0xFFFFD0C0, 0xFFFFE8B8, 0xFFF0C8E0)),
        PastelBlend("twilight", listOf(0xFFD0C8F0, 0xFFF0C8E0, 0xFFC8D8F8)),
        PastelBlend("peachy_keen", listOf(0xFFFFD8C0, 0xFFFFE8D0, 0xFFF8D0C8)),
        PastelBlend("lilac_mist", listOf(0xFFE8D8F8, 0xFFF0E0F8, 0xFFD8E0F8)),
        PastelBlend("mint_cream", listOf(0xFFD0F0E0, 0xFFE8F5D8, 0xFFC8E8E8)),
    )

    val scenes: List<AnimatedScene> = listOf(
        AnimatedScene(
            id = "waves",
            defaults = listOf(0xFFB8E4FF, 0xFF7EC8E3, 0xFFE8F7FF),
            slotKeys = listOf("sky", "water", "foam"),
        ),
        AnimatedScene(
            id = "bubbles",
            defaults = listOf(0xFFBFE8F5, 0xFFE8F7FF, 0xFFF8E0F0),
            slotKeys = listOf("water", "bubble", "shine"),
        ),
        AnimatedScene(
            id = "clouds",
            defaults = listOf(0xFFC5E4F7, 0xFFFFFFF6, 0xFFF8C8D4),
            slotKeys = listOf("sky", "cloud", "blush"),
        ),
        AnimatedScene(
            id = "sparkles",
            defaults = listOf(0xFFE6D0F0, 0xFFFFFFF2, 0xFFF8C8E0),
            slotKeys = listOf("dusk", "star", "glow"),
        ),
        AnimatedScene(
            id = "petals",
            defaults = listOf(0xFFF5EDE0, 0xFFF0C4D0, 0xFFC5D9C3),
            slotKeys = listOf("sky", "petal", "stem"),
        ),
        AnimatedScene(
            id = "aurora",
            defaults = listOf(0xFFD8E0F8, 0xFFF0C8E0, 0xFFC8EDE0, 0xFFDCC8F0),
            slotKeys = listOf("night", "rose", "mint", "lilac"),
        ),
    )

    const val DEFAULT_ARGB: Long = 0xFF0E0E12

    private val solidsById = solids.associateBy { it.id }
    private val blendsById = blends.associateBy { it.id }
    private val scenesById = scenes.associateBy { it.id }

    fun solid(id: String): SolidPastel? = solidsById[id]

    fun blend(id: String): PastelBlend? = blendsById[id]

    fun scene(id: String): AnimatedScene? = scenesById[id]

    fun colorsFor(background: AppBackground, custom: Map<String, List<Long>>): List<Long> {
        return when (background) {
            AppBackground.Default -> listOf(DEFAULT_ARGB)
            is AppBackground.Solid -> listOf(solid(background.swatchId)?.argb ?: DEFAULT_ARGB)
            is AppBackground.Gradient -> blend(background.blendId)?.colors ?: listOf(DEFAULT_ARGB)
            is AppBackground.Animated -> {
                val scene = scene(background.sceneId) ?: return listOf(DEFAULT_ARGB)
                val chosen = custom[scene.id]
                if (chosen == null || chosen.isEmpty()) {
                    scene.defaults
                } else {
                    scene.defaults.indices.map { index ->
                        chosen.getOrElse(index) { scene.defaults[index] }
                    }
                }
            }
        }
    }

    fun sampleArgb(background: AppBackground, custom: Map<String, List<Long>>): Long {
        return colorsFor(background, custom).firstOrNull() ?: DEFAULT_ARGB
    }
}
