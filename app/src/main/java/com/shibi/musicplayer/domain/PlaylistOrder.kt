package com.shibi.musicplayer.domain

object PlaylistOrder {
    fun moved(ids: List<Long>, fromIndex: Int, toIndex: Int): List<Long> {
        if (fromIndex == toIndex) return ids
        if (fromIndex !in ids.indices || toIndex !in ids.indices) return ids
        val next = ids.toMutableList()
        val id = next.removeAt(fromIndex)
        next.add(toIndex, id)
        return next
    }
}
