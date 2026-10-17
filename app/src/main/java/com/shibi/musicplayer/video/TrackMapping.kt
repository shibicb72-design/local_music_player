package com.shibi.musicplayer.video

import java.util.Locale
import kotlin.math.abs

/** One entry of libVLC's track list (`TrackDescription`): its ES id and name as given. */
data class RawTrackDescription(val id: Int, val name: String?)

enum class MediaTrackType { Audio, Video, Text, Unknown }

/** What libVLC's media knows about an elementary stream, matched to a description by id. */
data class RawMediaTrack(
    val id: Int,
    val language: String? = null,
    val codec: String? = null,
    val description: String? = null,
    val type: MediaTrackType = MediaTrackType.Unknown,
    /** `1920x1080` for video, `6ch 48000 Hz` for audio; for the error log only. */
    val info: String? = null,
)

/** Everything the engine reports about tracks; plain values, so the mapping is pure. */
data class TrackSnapshot(
    val audio: List<RawTrackDescription> = emptyList(),
    val subtitles: List<RawTrackDescription> = emptyList(),
    val mediaTracks: List<RawMediaTrack> = emptyList(),
    val selectedAudioId: Int = TrackMapping.DisabledId,
    val selectedSubtitleId: Int = TrackMapping.DisabledId,
    val audioDelayMs: Long = 0L,
    val subtitleDelayMs: Long = 0L,
    /** Tracks the user loaded from a file, by ES id: the file name shows as the label. */
    val externalNames: Map<Int, String> = emptyMap(),
)

/**
 * A row of the tracks sheet. [label] is libVLC's name as given (or the loaded file's name);
 * null means "no name", and the sheet shows "Track N". [detail] is the language and codec
 * that are not already in the label.
 */
data class TrackOption(
    val id: Int,
    val label: String?,
    val detail: String?,
    val selected: Boolean,
    val external: Boolean = false,
    /** The subtitle "Off" row (libVLC's "Disable" entry, id -1). */
    val isOff: Boolean = false,
)

/** libVLC track descriptions to the sheet's rows. */
object TrackMapping {
    /** libVLC's id for "no track" (its "Disable" entry). */
    const val DisabledId = -1

    /**
     * Audio rows: every real track; libVLC's "Disable" entry is dropped (muting is not an
     * audio choice here). Nothing is selected when libVLC reports no current track.
     */
    fun audioOptions(snapshot: TrackSnapshot): List<TrackOption> = snapshot.audio
        .filterNot(::isDisable)
        .distinctBy { it.id }
        .map { option(it, snapshot, snapshot.selectedAudioId) }

    /**
     * Subtitle rows: "Off" first (libVLC's "Disable" entry, added if libVLC left it out),
     * then every track. Off is selected when no real track is.
     */
    fun subtitleOptions(snapshot: TrackSnapshot): List<TrackOption> {
        val tracks = snapshot.subtitles
            .filterNot(::isDisable)
            .distinctBy { it.id }
            .map { option(it, snapshot, snapshot.selectedSubtitleId) }
        val anySelected = tracks.any { it.selected }
        val off = TrackOption(
            id = DisabledId,
            label = null,
            detail = null,
            selected = !anySelected,
            isOff = true,
        )
        return listOf(off) + tracks
    }

    /** The "Disable" entry: id -1, or libVLC's own "Disable" name on any id. */
    fun isDisable(track: RawTrackDescription): Boolean =
        track.id == DisabledId || track.name?.trim()?.equals("Disable", ignoreCase = true) == true

    private fun option(track: RawTrackDescription, snapshot: TrackSnapshot, selectedId: Int): TrackOption {
        val external = snapshot.externalNames[track.id]
        val media = snapshot.mediaTracks.firstOrNull { it.id == track.id }
        val label = external ?: track.name?.trim()?.takeIf { it.isNotEmpty() }
        return TrackOption(
            id = track.id,
            label = label,
            detail = detail(label, media),
            selected = track.id == selectedId,
            external = external != null,
        )
    }

    /** `eng · AC3`: the language and codec, leaving out anything the label already says. */
    internal fun detail(label: String?, media: RawMediaTrack?): String? {
        if (media == null) return null
        val known = label.orEmpty().lowercase(Locale.ROOT)
        return listOfNotNull(
            media.language?.trim()?.takeIf { it.isNotEmpty() && !known.contains(it.lowercase(Locale.ROOT)) },
            media.codec?.trim()?.takeIf { it.isNotEmpty() }?.let(::codecName)
                ?.takeIf { !known.contains(it.lowercase(Locale.ROOT)) },
        ).joinToString(" · ").takeIf { it.isNotEmpty() }
    }

    /** `video h264 1920x1080`, `audio a52 6ch 48000 Hz eng`: the error log's codec line. */
    fun codecSummary(tracks: List<RawMediaTrack>): List<String> = tracks.map { track ->
        listOfNotNull(
            track.type.name.lowercase(Locale.ROOT),
            track.codec?.trim()?.takeIf { it.isNotEmpty() },
            track.info?.takeIf { it.isNotBlank() },
            track.language?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(" ")
    }

    /** libVLC reports four-character codes; the common ones get their usual names. */
    internal fun codecName(fourcc: String): String = when (fourcc.trim().lowercase(Locale.ROOT)) {
        "a52", "ac3" -> "AC3"
        "eac3" -> "E-AC3"
        "dts" -> "DTS"
        "mp4a" -> "AAC"
        "mpga", "mp3" -> "MP3"
        "opus" -> "Opus"
        "vorb" -> "Vorbis"
        "flac" -> "FLAC"
        "trhd", "mlp" -> "TrueHD"
        "subt" -> "Text"
        "ssa" -> "ASS"
        "wvtt" -> "WebVTT"
        "ttml" -> "TTML"
        "tx3g" -> "Timed text"
        "spu", "spu " -> "VobSub"
        "pgs" -> "PGS"
        "dvbs" -> "DVB"
        else -> fourcc.trim()
    }
}

/** Which kind of file the user loads into the playing video. */
enum class ExternalTrackKind { Audio, Subtitle }

/** The subtitle file picker shows every file type; only these extensions are loaded. */
object SubtitleFiles {
    val Extensions = listOf("srt", "ass", "ssa", "vtt", "sub", "idx", "ttml")

    fun isSupported(fileName: String?): Boolean {
        val extension = fileName?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase(Locale.ROOT)
            ?: return false
        return extension in Extensions
    }

    /** `.srt .ass .ssa .vtt .sub .idx .ttml`, for the short message when a file is refused. */
    fun extensionsLabel(): String = Extensions.joinToString(" ") { ".$it" }
}

/** The subtitle and audio delay steppers: whole steps, clamped, labelled in seconds. */
enum class TrackDelay(val stepMs: Long, private val decimals: Int) {
    /** ±0.1 s per tap; downloaded subtitles are often a few seconds off. */
    Subtitle(stepMs = 100L, decimals = 1),

    /** ±0.05 s per tap; lip sync needs finer steps. */
    Audio(stepMs = 50L, decimals = 2),
    ;

    /**
     * The delay after one tap in [direction] (+1 later, -1 earlier). The result snaps to a
     * whole step, so taps never drift, and stays within ±[LimitMs].
     */
    fun step(currentMs: Long, direction: Int): Long {
        val snapped = Math.round(currentMs.toDouble() / stepMs) * stepMs
        val next = snapped + Integer.signum(direction) * stepMs
        return next.coerceIn(-LimitMs, LimitMs)
    }

    fun canStep(currentMs: Long, direction: Int): Boolean = step(currentMs, direction) != currentMs

    /** `+0.1 s`, `-0.05 s`, `0.0 s` / `0.00 s`. */
    fun label(delayMs: Long): String {
        val sign = when {
            delayMs > 0L -> "+"
            delayMs < 0L -> "-"
            else -> ""
        }
        val seconds = abs(delayMs) / 1000.0
        return sign + String.format(Locale.ROOT, "%.${decimals}f s", seconds)
    }

    companion object {
        /** libVLC accepts more, but a minute either way is already far beyond any fix. */
        const val LimitMs = 60_000L
    }
}

/**
 * Subtitle text size. libVLC's text renderer (SRT, VTT) sizes text as the picture height
 * divided by [relativeFontSize], so a smaller number is bigger text; ASS keeps its own
 * styling. Medium passes nothing and keeps libVLC's default.
 */
enum class SubtitleTextSize(val relativeFontSize: Int?) {
    Small(20),
    Medium(null),
    Large(12),
    ;

    fun encode(): String = name

    companion object {
        val Default = Medium

        fun decode(stored: String?): SubtitleTextSize = entries.firstOrNull { it.name == stored } ?: Default
    }
}

/** A short message in the sheet after a load failed; the details go to the error log. */
enum class TrackMessage { NotASubtitle, SubtitleFailed, AudioFailed }

/** Everything the tracks sheet shows. */
data class TrackSheetState(
    val audio: List<TrackOption> = emptyList(),
    val subtitles: List<TrackOption> = TrackMapping.subtitleOptions(TrackSnapshot()),
    val audioDelayMs: Long = 0L,
    val subtitleDelayMs: Long = 0L,
    val textSize: SubtitleTextSize = SubtitleTextSize.Default,
    val hardwareDecoding: Boolean = true,
    /** A file is being read or added. */
    val loading: ExternalTrackKind? = null,
    val message: TrackMessage? = null,
) {
    companion object {
        fun of(
            snapshot: TrackSnapshot,
            options: EngineOptions,
            loading: ExternalTrackKind? = null,
            message: TrackMessage? = null,
        ): TrackSheetState = TrackSheetState(
            audio = TrackMapping.audioOptions(snapshot),
            subtitles = TrackMapping.subtitleOptions(snapshot),
            audioDelayMs = snapshot.audioDelayMs,
            subtitleDelayMs = snapshot.subtitleDelayMs,
            textSize = options.subtitleTextSize,
            hardwareDecoding = options.hardwareDecoding,
            loading = loading,
            message = message,
        )
    }
}
