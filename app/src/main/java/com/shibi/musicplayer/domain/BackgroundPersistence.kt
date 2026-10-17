package com.shibi.musicplayer.domain

/**
 * String encoding for DataStore. No Android types so tests stay on JUnit.
 */
object BackgroundPersistence {
    const val KEY_BACKGROUND = "background_id"
    const val KEY_COLORS_PREFIX = "anim_colors_"

    fun encode(background: AppBackground): String = background.id

    fun decode(raw: String?): AppBackground {
        if (raw.isNullOrBlank() || raw == AppBackground.ID_DEFAULT) {
            return AppBackground.Default
        }
        return when {
            raw.startsWith(AppBackground.PREFIX_SOLID) -> {
                val id = raw.removePrefix(AppBackground.PREFIX_SOLID)
                if (BackgroundCatalog.solid(id) == null) {
                    AppBackground.Default
                } else {
                    AppBackground.Solid(id)
                }
            }
            raw.startsWith(AppBackground.PREFIX_GRADIENT) -> {
                val id = raw.removePrefix(AppBackground.PREFIX_GRADIENT)
                if (BackgroundCatalog.blend(id) == null) {
                    AppBackground.Default
                } else {
                    AppBackground.Gradient(id)
                }
            }
            raw.startsWith(AppBackground.PREFIX_ANIMATED) -> {
                val id = raw.removePrefix(AppBackground.PREFIX_ANIMATED)
                if (BackgroundCatalog.scene(id) == null) {
                    AppBackground.Default
                } else {
                    AppBackground.Animated(id)
                }
            }
            else -> AppBackground.Default
        }
    }

    fun colorsKey(sceneId: String): String = "$KEY_COLORS_PREFIX$sceneId"

    fun encodeColors(colors: List<Long>): String {
        return colors.joinToString(",") { color ->
            (color and 0xFFFFFFFFL).toString(16).padStart(8, '0')
        }
    }

    fun decodeColors(raw: String?, defaults: List<Long>): List<Long> {
        if (raw.isNullOrBlank() || defaults.isEmpty()) return defaults
        val parsed = raw.split(',').mapNotNull { token ->
            val trimmed = token.trim()
            if (trimmed.isEmpty()) {
                null
            } else {
                val value = trimmed.toLongOrNull(16) ?: return@mapNotNull null
                if (value <= 0xFFFFFFL) value or 0xFF000000L else value
            }
        }
        if (parsed.isEmpty()) return defaults
        return defaults.indices.map { index ->
            parsed.getOrElse(index) { defaults[index] }
        }
    }
}
