package com.shibi.musicplayer.domain

/** Multi-select state for a list of media ids. Inactive means no selection mode. */
data class Selection(val ids: Set<Long> = emptySet(), val active: Boolean = false) {
    val count: Int get() = ids.size

    fun start(id: Long): Selection = Selection(setOf(id), true)

    fun toggle(id: Long): Selection {
        val next = if (id in ids) ids - id else ids + id
        return if (next.isEmpty()) Selection() else Selection(next, true)
    }

    fun selectAll(all: Collection<Long>): Selection =
        if (all.isEmpty()) Selection() else Selection(all.toSet(), true)

    fun clear(): Selection = Selection()

    fun retain(existing: Set<Long>): Selection {
        if (!active) return this
        val kept = ids.intersect(existing)
        return if (kept.isEmpty()) Selection() else Selection(kept, true)
    }

    fun isWholeList(all: Collection<Long>): Boolean =
        active && all.isNotEmpty() && ids == all.toSet()
}
