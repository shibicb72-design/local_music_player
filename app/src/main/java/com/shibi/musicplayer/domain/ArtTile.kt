package com.shibi.musicplayer.domain

data class ArtTileSpec(
    val startArgb: Long,
    val endArgb: Long,
    val glyph: String,
)

object ArtTile {
    val GRADIENTS: List<Pair<Long, Long>> = listOf(
        0xFF7C5CFF to 0xFFFF5C8A,
        0xFF00C2A8 to 0xFF3D7BFF,
        0xFFFF8A3D to 0xFFFF3D6E,
        0xFF5C6CFF to 0xFF00D4FF,
        0xFFB45CFF to 0xFF5C2EFF,
        0xFF2ED47A to 0xFF0A8F6B,
        0xFFFFC23D to 0xFFFF6B3D,
        0xFFFF5CC8 to 0xFF7C3DFF,
    )

    fun specFor(title: String): ArtTileSpec {
        val cleaned = TrackDisplay.title(title).trim()
        val index = (cleaned.hashCode() and 0x7fffffff) % GRADIENTS.size
        val gradient = GRADIENTS[index]
        val glyph = if (cleaned.isBlank()) {
            "?"
        } else {
            val cp = cleaned.codePointAt(0)
            if (Character.isLetterOrDigit(cp)) {
                String(Character.toChars(cp)).uppercase()
            } else {
                "?"
            }
        }
        return ArtTileSpec(gradient.first, gradient.second, glyph)
    }
}
