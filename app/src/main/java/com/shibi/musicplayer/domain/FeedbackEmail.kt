package com.shibi.musicplayer.domain

/**
 * The feedback email: a fixed address and subject so every report lands in one inbox.
 * The body asks first, then lists the build and device; nothing that identifies the user.
 */
object FeedbackEmail {
    const val Address = "shibicb.71@gmail.com"
    const val Subject = "Theatrum feedback"

    fun body(
        prompt: String,
        versionName: String,
        versionCode: Long,
        manufacturer: String,
        model: String,
        androidRelease: String,
        sdkInt: Int,
    ): String = buildString {
        appendLine(prompt)
        appendLine()
        appendLine()
        appendLine("---")
        appendLine("App version: $versionName ($versionCode)")
        appendLine("Device: ${"$manufacturer $model".trim()}")
        append("Android: $androidRelease (API $sdkInt)")
    }
}
