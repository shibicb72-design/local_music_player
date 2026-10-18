package com.shibi.musicplayer.domain

enum class MediaKind { Song, Video }

data class TrashItem(val kind: MediaKind, val id: Long, val uri: String, val title: String)

/** Pure planning helpers for moving media to the system trash. */
object TrashPlan {
    const val GROUP_WARNING_MIN = 2
    const val MAX_EXTERNAL_PRUNE = 50

    fun needsGroupWarning(count: Int, wholeList: Boolean): Boolean =
        wholeList && count >= GROUP_WARNING_MIN

    fun failed(requested: List<TrashItem>, stillVisibleUris: Set<String>): List<TrashItem> =
        requested.filter { it.uri in stillVisibleUris }

    fun queueIndicesToRemove(queueMediaIds: List<String>, trashedSongIds: Set<Long>): List<Int> =
        queueMediaIds.indices
            .filter { i -> queueMediaIds[i].toLongOrNull()?.let { it in trashedSongIds } == true }
            .sortedDescending()

    fun trashesCurrentVideo(currentVideoUri: String?, items: List<TrashItem>): Boolean =
        currentVideoUri != null && items.any { it.kind == MediaKind.Video && it.uri == currentVideoUri }

    fun missingFromLibrary(stored: Set<Long>, library: Set<Long>, libraryLoaded: Boolean): Set<Long> {
        if (!libraryLoaded || library.isEmpty()) return emptySet()
        val missing = stored.filter { it >= 0 && it !in library }.toSet()
        return if (missing.size > MAX_EXTERNAL_PRUNE) emptySet() else missing
    }
}
