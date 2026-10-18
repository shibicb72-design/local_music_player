package com.shibi.musicplayer.domain

/**
 * Turns crash material into text worth storing: readable runs pulled out of a binary
 * tombstone, and the exit-info timestamps that have not been recorded yet. No Android here.
 */
object CrashText {

    /** ASCII 0x20..0x7E runs of at least [minRun] chars, joined with newlines, cut at [maxChars]. */
    fun printableRuns(bytes: ByteArray, minRun: Int = 6, maxChars: Int = 16_000): String {
        val out = StringBuilder()
        val run = StringBuilder()
        var started = false

        fun flush() {
            if (run.length >= minRun) {
                if (started) out.append('\n')
                out.append(run)
                started = true
            }
            run.setLength(0)
        }

        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            if (value in 0x20..0x7E) run.append(value.toChar()) else flush()
        }
        flush()
        return out.toString().take(maxChars)
    }

    /**
     * The timestamps to record, oldest first: newer than [seenMs], or when nothing has ever
     * been seen, newer than [nowMs] minus [firstRunWindowMs].
     */
    fun newSince(
        timestamps: List<Long>,
        seenMs: Long?,
        nowMs: Long,
        firstRunWindowMs: Long = 86_400_000L,
    ): List<Long> {
        val floor = seenMs ?: (nowMs - firstRunWindowMs)
        return timestamps.filter { it > floor }.sorted()
    }
}
