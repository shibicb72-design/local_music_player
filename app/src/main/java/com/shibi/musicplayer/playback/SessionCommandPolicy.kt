package com.shibi.musicplayer.playback

/**
 * Which session controllers may send Theatrum's custom commands (SET_OUTPUT).
 * Only this app's own process qualifies: the UID comes from Binder, so a
 * foreign app cannot pass by claiming our package name. Media3's notification
 * controller is created inside this process, so it is covered too. Every other
 * controller (other apps, Auto, Assistant) keeps the default player commands.
 */
object SessionCommandPolicy {
    fun allowsCustomCommands(
        controllerUid: Int,
        controllerPackage: String,
        appUid: Int,
        appPackage: String,
    ): Boolean = controllerUid == appUid && controllerPackage == appPackage
}
