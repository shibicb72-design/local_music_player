package com.shibi.musicplayer.domain

import java.text.CollationKey
import java.text.Collator

/** How [MusicCatalog.albums] orders the albums. */
enum class AlbumSort { Title, Artist, YearNewest }

/**
 * One artist, grouped by the library artist. [key] and [name] are null for the unknown entry (the
 * UI supplies its label). [songCount] is the artist page's song count; [albumCount] counts only
 * the artist's own albums.
 */
data class ArtistEntry(val key: String?, val name: String?, val albumCount: Int, val songCount: Int)

/** One album: the tracks that share [key]. [artistName] is the owning artist's name, null when unknown. */
data class AlbumEntry(
    val key: String,
    val title: String,
    val artistKey: String?,
    val artistName: String?,
    val year: Int?,
    val songCount: Int,
    val durationMs: Long,
    val albumArtUri: String?,
)

/** What an artist page shows: own [albums], albums it [appearsOn], and every one of its [songs]. */
data class ArtistPage(
    val artist: ArtistEntry,
    val albums: List<AlbumEntry>,
    val appearsOn: List<AlbumEntry>,
    val songs: List<Track>,
)

data class AlbumDisc(val number: Int, val tracks: List<Track>)

data class AlbumPage(val album: AlbumEntry, val discs: List<AlbumDisc>)

/**
 * The library grouped as artists and albums, with the data an artist page and an album page need.
 * Built once per library scan; every lookup afterwards reads a map and sorts at most one page.
 */
class MusicCatalog private constructor(
    /** Ordered like the letter groups of [LibrarySorter]: `#` first, then A-Z, unknown last. */
    val artists: List<ArtistEntry>,
    /** One run per letter over [artists], for the fast scroller. */
    val artistRuns: List<SortRun>,
    private val albumLists: Map<AlbumSort, List<AlbumEntry>>,
    private val artistByKey: Map<String?, ArtistEntry>,
    private val albumByKey: Map<String, AlbumEntry>,
    private val albumTracks: Map<String, List<Track>>,
    private val ownAlbums: Map<String?, List<AlbumEntry>>,
    private val appearsOnAlbums: Map<String, List<AlbumEntry>>,
    private val songsByArtist: Map<String?, List<Row>>,
    private val collator: Collator,
) {
    /** The albums in [sort] order; precomputed, so this never sorts. */
    fun albums(sort: AlbumSort): List<AlbumEntry> = albumLists.getValue(sort)

    /** The page of the artist with [key] (null: the unknown entry), or null when there is none. */
    fun artistPage(key: String?): ArtistPage? {
        val artist = artistByKey[key] ?: return null
        val songs = songsByArtist[key].orEmpty()
        if (key == null) {
            val keyed = songs.map { it.track to collator.getCollationKey(it.track.title) }
            val byTitle = compareBy<Pair<Track, CollationKey>> { it.second }.thenBy { it.first.id }
            return ArtistPage(artist, emptyList(), emptyList(), keyed.sortedWith(byTitle).map { it.first })
        }
        val own = ownAlbums[key].orEmpty()
        val guest = appearsOnAlbums[key].orEmpty()
        val rank = HashMap<String, Int>()
        (own + guest).forEachIndexed { index, album -> rank.putIfAbsent(album.key, index) }
        val none = Int.MAX_VALUE
        val ordered = songs.map { Triple(it.track, it.albumKey?.let(rank::get) ?: none, collator.getCollationKey(it.track.title)) }
            .sortedWith(
                compareBy<Triple<Track, Int, CollationKey>> { it.second }
                    .thenBy { it.first.discNumber ?: 1 }
                    .thenBy { it.first.trackNumber ?: Int.MAX_VALUE }
                    .thenBy { it.third }
                    .thenBy { it.first.id },
            )
        return ArtistPage(artist, own, guest, ordered.map { it.first })
    }

    /** The page of the album with [key], or null when there is none. */
    fun albumPage(key: String): AlbumPage? {
        val album = albumByKey[key] ?: return null
        val discs = albumTracks[key].orEmpty()
            .groupBy { it.discNumber ?: 1 }
            .toSortedMap()
            .map { (number, tracks) -> AlbumDisc(number, byTrackThenTitle(tracks)) }
        return AlbumPage(album, discs)
    }

    private fun byTrackThenTitle(tracks: List<Track>): List<Track> =
        tracks.map { it to collator.getCollationKey(it.title) }
            .sortedWith(
                compareBy<Pair<Track, CollationKey>> { it.first.trackNumber ?: Int.MAX_VALUE }
                    .thenBy { it.second }
                    .thenBy { it.first.id },
            )
            .map { it.first }

    /** A track with the names the catalog groups it by. */
    private class Row(
        val track: Track,
        val libName: String?,
        val libKey: String?,
        val perfKey: String?,
        val albumTitle: String?,
        val albumKey: String?,
    )

    /** An album with the keys it is ordered by. */
    private class AlbumSortKeys(val entry: AlbumEntry, val title: CollationKey, val artist: CollationKey?)

    companion object {
        private const val SEP = "\u0000"

        fun build(tracks: List<Track>, unknown: UnknownNames, collator: Collator): MusicCatalog {
            val keys = HashMap<String, String>()
            fun key(name: String) = keys.getOrPut(name) { ArtistNames.sortKey(name) }
            val collation = HashMap<String, CollationKey>()
            fun collate(text: String) = collation.getOrPut(text) { collator.getCollationKey(text) }

            val rows = tracks.map { track ->
                val lib = ArtistNames.libraryArtist(track, unknown)
                val libKey = lib?.let(::key)
                val perfKey = ArtistNames.performer(track, unknown)?.let(::key)
                val title = ArtistNames.album(track.album, unknown)?.takeUnless { isFolderName(track, it, unknown) }
                Row(track, lib, libKey, perfKey, title, title?.let { (libKey ?: "") + SEP + key(it) })
            }

            // Albums.
            val byAlbum = LinkedHashMap<String, MutableList<Row>>()
            rows.forEach { row -> row.albumKey?.let { byAlbum.getOrPut(it) { mutableListOf() } += row } }
            val byArtist = LinkedHashMap<String?, MutableList<Row>>()
            rows.forEach { byArtist.getOrPut(it.libKey) { mutableListOf() } += it }

            val artistNames = byArtist.mapValues { (_, group) -> mostCommon(group.mapNotNull { it.libName }, collator) }

            val albumEntries = byAlbum.map { (albumKey, group) ->
                val first = group.first()
                AlbumEntry(
                    key = albumKey,
                    title = mostCommon(group.mapNotNull { it.albumTitle }, collator)!!,
                    artistKey = first.libKey,
                    artistName = artistNames[first.libKey],
                    year = commonYear(group.mapNotNull { it.track.year }),
                    songCount = group.size,
                    durationMs = group.sumOf { it.track.durationMs },
                    albumArtUri = group.firstNotNullOfOrNull { it.track.albumArtUri },
                )
            }
            val sortKeys = albumEntries.map {
                AlbumSortKeys(it, collate(key(it.title)), it.artistKey?.let(::collate))
            }

            val titleOrder = Comparator<AlbumSortKeys> { a, b -> a.title.compareTo(b.title) }
                .thenComparator { a, b -> compareNullable(a.artist, b.artist) }
                .thenComparator { a, b -> a.entry.key.compareTo(b.entry.key) }
            val artistOrder = Comparator<AlbumSortKeys> { a, b -> compareNullable(a.artist, b.artist) }
                .thenComparator { a, b -> a.title.compareTo(b.title) }
                .thenComparator { a, b -> a.entry.key.compareTo(b.entry.key) }
            val yearNewest = Comparator<AlbumSortKeys> { a, b -> compareYear(a.entry.year, b.entry.year, newestFirst = true) }
                .thenComparator { a, b -> a.title.compareTo(b.title) }
                .thenComparator { a, b -> compareNullable(a.artist, b.artist) }
                .thenComparator { a, b -> a.entry.key.compareTo(b.entry.key) }
            val pageOrder = Comparator<AlbumSortKeys> { a, b -> compareYear(a.entry.year, b.entry.year, newestFirst = false) }
                .thenComparator { a, b -> a.title.compareTo(b.title) }
                .thenComparator { a, b -> a.entry.key.compareTo(b.entry.key) }

            val albumLists = mapOf(
                AlbumSort.Title to sortKeys.sortedWith(titleOrder).map { it.entry },
                AlbumSort.Artist to sortKeys.sortedWith(artistOrder).map { it.entry },
                AlbumSort.YearNewest to sortKeys.sortedWith(yearNewest).map { it.entry },
            )
            val pageSorted = sortKeys.sortedWith(pageOrder).map { it.entry }
            val ownAlbums = pageSorted.groupBy { it.artistKey }

            // Artists: appears-on albums and song lists.
            val guestAlbumKeys = HashMap<String, MutableSet<String>>()
            val songsByArtist = LinkedHashMap<String?, MutableList<Row>>()
            rows.forEach { row ->
                songsByArtist.getOrPut(row.libKey) { mutableListOf() } += row
                val perf = row.perfKey
                if (perf != null && perf != row.libKey) {
                    songsByArtist.getOrPut(perf) { mutableListOf() } += row
                    row.albumKey?.let { guestAlbumKeys.getOrPut(perf) { mutableSetOf() } += it }
                }
            }
            val albumByKey = albumEntries.associateBy { it.key }
            val appearsOn = guestAlbumKeys.mapValues { (_, albumKeys) -> pageSorted.filter { it.key in albumKeys } }

            val entries = byArtist.keys.map { artistKey ->
                ArtistEntry(
                    key = artistKey,
                    name = artistNames[artistKey],
                    // The unknown entry has no albums of its own, though its tracks may share one.
                    albumCount = if (artistKey == null) 0 else ownAlbums[artistKey]?.size ?: 0,
                    songCount = songsByArtist[artistKey]?.size ?: 0,
                )
            }
            val ordered = orderArtists(entries, collator)
            val runs = LibrarySorter.runs(ordered.map { SortGroup.Letter(it.key?.let(SortLetters::of)) })

            return MusicCatalog(
                artists = ordered,
                artistRuns = runs,
                albumLists = albumLists,
                artistByKey = entries.associateBy { it.key },
                albumByKey = albumByKey,
                albumTracks = byAlbum.mapValues { (_, group) -> group.map { it.track } },
                ownAlbums = ownAlbums,
                appearsOnAlbums = appearsOn,
                songsByArtist = songsByArtist,
                collator = collator,
            )
        }

        /** Hash group first, letters by the collator, then by key; the unknown entry last. */
        private fun orderArtists(entries: List<ArtistEntry>, collator: Collator): List<ArtistEntry> {
            class Keyed(val entry: ArtistEntry, val letter: String, val letterKey: CollationKey?, val key: CollationKey)
            val letterKeys = HashMap<String, CollationKey>()
            val (known, unknown) = entries.partition { it.key != null }
            val keyed = known.map {
                val letter = SortLetters.of(it.key!!)
                Keyed(
                    it, letter,
                    if (letter == SortLetters.HASH) null else letterKeys.getOrPut(letter) { collator.getCollationKey(letter) },
                    collator.getCollationKey(it.key),
                )
            }
            val comparator = Comparator<Keyed> { a, b ->
                val aHash = a.letterKey == null
                val bHash = b.letterKey == null
                when {
                    aHash != bHash -> if (aHash) -1 else 1
                    else -> compareNullable(a.letterKey, b.letterKey).takeIf { it != 0 }
                        ?: a.letter.compareTo(b.letter).takeIf { it != 0 }
                        ?: a.key.compareTo(b.key).takeIf { it != 0 }
                        ?: a.entry.key!!.compareTo(b.entry.key!!)
                }
            }
            return keyed.sortedWith(comparator).map { it.entry } + unknown
        }

        private fun compareNullable(a: CollationKey?, b: CollationKey?): Int = when {
            a == null && b == null -> 0
            a == null -> 1
            b == null -> -1
            else -> a.compareTo(b)
        }

        /** Years with a missing one last. [newestFirst] flips the known ones. */
        private fun compareYear(a: Int?, b: Int?, newestFirst: Boolean): Int = when {
            a == null && b == null -> 0
            a == null -> 1
            b == null -> -1
            newestFirst -> b.compareTo(a)
            else -> a.compareTo(b)
        }

        /** The most common spelling; on a tie the one that sorts first. Null for an empty list. */
        private fun mostCommon(names: List<String>, collator: Collator): String? {
            if (names.isEmpty()) return null
            val counts = HashMap<String, Int>()
            names.forEach { counts.merge(it, 1, Int::plus) }
            return counts.entries.minWithOrNull(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenComparator { a, b -> collator.compare(a.key, b.key) }
                    .thenBy { it.key },
            )?.key
        }

        /** The most common year, the earliest on a tie. */
        private fun commonYear(years: List<Int>): Int? {
            if (years.isEmpty()) return null
            val counts = years.groupingBy { it }.eachCount()
            return counts.entries.minWithOrNull(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })?.key
        }

        /**
         * Android fills the album of an untagged file with its folder name, so an untagged track
         * whose album is its folder's name has no real album.
         */
        private fun isFolderName(track: Track, album: String, unknown: UnknownNames): Boolean {
            if (ArtistNames.artist(track.albumArtist, unknown) != null) return false
            if (ArtistNames.artist(track.artist, unknown) != null) return false
            val last = track.folder?.trimEnd('/', '\\')?.substringAfterLast('/')?.substringAfterLast('\\') ?: return false
            return album.equals(last, ignoreCase = true)
        }
    }
}
