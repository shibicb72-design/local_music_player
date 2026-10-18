package com.shibi.musicplayer.library

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.RequiresApi
import com.shibi.musicplayer.diagnostics.ErrorLog

/** The system trash for MediaStore files (API 30+): one request, one system popup, undoable. */
internal object MediaTrash {
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** A request that moves [uris] to the trash ([trash] true) or back (false); null if unsupported or it failed. */
    fun request(context: Context, uris: List<String>, trash: Boolean): IntentSenderRequest? {
        if (!isSupported() || uris.isEmpty()) return null
        return try {
            createRequest(context, uris, trash)
        } catch (error: Exception) {
            ErrorLog.get(context).log(
                step = "Trash request",
                error = error,
                shown = null,
                media = null,
            )
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun createRequest(context: Context, uris: List<String>, trash: Boolean): IntentSenderRequest {
        val pending = MediaStore.createTrashRequest(
            context.contentResolver,
            uris.map(Uri::parse),
            trash,
        )
        return IntentSenderRequest.Builder(pending.intentSender).build()
    }

    /**
     * After the system said OK: the uris a plain query still returns (trashed rows are excluded by
     * default). A query that throws counts as not visible.
     */
    fun stillVisible(context: Context, uris: List<String>): Set<String> =
        uris.filterTo(mutableSetOf()) { uri ->
            try {
                context.contentResolver.query(
                    Uri.parse(uri),
                    arrayOf(MediaStore.MediaColumns._ID),
                    null,
                    null,
                    null,
                )?.use { it.moveToFirst() } ?: false
            } catch (_: Exception) {
                false
            }
        }
}
