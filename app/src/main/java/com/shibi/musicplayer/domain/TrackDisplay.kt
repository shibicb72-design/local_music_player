package com.shibi.musicplayer.domain

object TrackDisplay {
    private val AUDIO_EXTENSIONS = setOf(
        "mp3",
        "flac",
        "m4a",
        "aac",
        "ogg",
        "opus",
        "wav",
        "wma",
    )

    fun title(raw: String): String {
        val trimmed = raw.trim()
        val dot = trimmed.lastIndexOf('.')
        val stripped = if (dot >= 0 && trimmed.substring(dot + 1).lowercase() in AUDIO_EXTENSIONS) {
            trimmed.substring(0, dot).trim()
        } else {
            trimmed
        }
        return stripped.ifBlank { trimmed }
    }

    fun artist(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase()
        if (lower == "unknown" || lower == "<unknown>") return null
        return trimmed
    }
}
