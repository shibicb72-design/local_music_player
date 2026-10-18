package com.shibi.musicplayer.library

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.RequiresApi
import com.shibi.musicplayer.diagnostics.ErrorLog
import com.shibi.musicplayer.domain.ItemCheck

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
     * After the system said OK: what MediaStore says about each uri, trashed rows included.
     * Empty below API 30. A SecurityException is a permission error; any other failure counts as gone.
     */
    fun check(context: Context, uris: List<String>): Map<String, ItemCheck> {
        if (!isSupported()) return emptyMap()
        return uris.associateWith { uri -> checkOne(context, uri) }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun checkOne(context: Context, uri: String): ItemCheck = try {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.IS_TRASHED,
            MediaStore.MediaColumns.VOLUME_NAME,
        )
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
        }
        context.contentResolver.query(Uri.parse(uri), projection, args, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                ItemCheck(
                    found = true,
                    trashed = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_TRASHED)) == 1,
                    volumeName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.VOLUME_NAME)),
                )
            } else {
                ItemCheck(found = false)
            }
        } ?: ItemCheck(found = false)
    } catch (_: SecurityException) {
        ItemCheck(found = true, securityError = true)
    } catch (_: Exception) {
        ItemCheck(found = false)
    }
}
