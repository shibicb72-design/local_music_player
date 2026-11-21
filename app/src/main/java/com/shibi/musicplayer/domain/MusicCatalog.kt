package com.shibi.musicplayer.domain

import java.text.CollationKey
import java.text.Collator

/** Which part of the music library the Songs screen shows. [key] is what the store keeps. */
enum class MusicView(val key: String) {
    Songs("songs"),
    Artists("artists"),
    Albums("albums"),
    ;

    companion object {
        /** An unknown or missing value (an old build, a corrupt file) reads as [Songs]. */
        fun fromKey(raw: String?): MusicView = entries.firstOrNull { it.key == raw } ?: Songs
    }
}

/** How [MusicCatalog.albums] orders the albums. [key] is what the store keeps. */
enum class AlbumSort(val key: String) {
    Title("title"),
    Artist("artist"),
    YearNewest("year_newest"),
    ;

    companion object {
        /** An unknown or missing value (an old build, a corrupt file) reads as [Title]. */
        fun fromKey(raw: String?): AlbumSort = entries.firstOrNull { it.key == raw } ?: Title
    }
}

/**
 * The artist an album page names on [track]'s row: its own artist only when that is not the
 * album's ("Zedd feat. Guest Singer" on Zedd's album), else null.
 */
fun albumTrackArtist(track: Track, album: AlbumEntry): String? =
    TrackDisplay.artist(track.artist)?.takeIf { ArtistNames.sortKey(it) != album.artistKey }

/**
 * One artist, grouped by the library artist. [key] and [name] are null for the unknown entry (the
 * UI supplies its label). [songCount] is the artist page's song count; [albumCount] counts only
 * the artist's own albums.
 */
data class ArtistEntry(
    val key: String?,
    val name: String?,
    val albumCount: Int,
    val songCount: Int,
    /** The art of the artist's first own album (in page order) that has one; null if none. */
    val artUri: String? = null,
)

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
 * Built once per library scan; every lookup afterwards reads a map and sorts one page by plain numbers, with no collation.
 */
class MusicCatalog private constructor(
    /** Ordered like the letter groups of [LibrarySorter]: `#` first, then A-Z, unknown last. */
    val artists: List<ArtistEntry>,
    /** One run per letter over [artists], for the fast scroller. */
    val artistRuns: List<SortRun>,
    private val albumLists: Map<AlbumSort, List<AlbumEntry>>,
    private val albumRunLists: Map<AlbumSort, List<SortRun>>,
    private val artistByKey: Map<String?, ArtistEntry>,
    private val albumByKey: Map<String, AlbumEntry>,
    private val albumTracks: Map<String, List<Row>>,
    private val ownAlbums: Map<String?, List<AlbumEntry>>,
    private val appearsOnAlbums: Map<String, List<AlbumEntry>>,
    private val songsByArtist: Map<String?, List<Row>>,
    private val albumKeyByTrack: Map<Long, String>,
) {
    /** The albums in [sort] order; precomputed, so this never sorts. */
    fun albums(sort: AlbumSort): List<AlbumEntry> = albumLists.getValue(sort)

    /** One run per letter over [albums] in [sort] order, for the fast scroller; none for [AlbumSort.YearNewest]. */
    fun albumRuns(sort: AlbumSort): List<SortRun> = albumRunLists.getValue(sort)

    /**
     * The album the catalog keeps [track] under, or null: no album tag, or only its folder's
     * name (which the catalog leaves out of the albums).
     */
    fun albumOf(track: Track): AlbumEntry? = albumKeyByTrack[track.id]?.let(albumByKey::get)

    /** Whether the catalog has an artist with [key] (null: the unknown entry). A map lookup. */
    fun hasArtist(key: String?): Boolean = artistByKey.containsKey(key)

    /** Whether the catalog has an album with [key]. A map lookup. */
    fun hasAlbum(key: String): Boolean = albumByKey.containsKey(key)

    /** The page of the artist with [key] (null: the unknown entry), or null when there is none. */
    fun artistPage(key: String?): ArtistPage? {
        val artist = artistByKey[key] ?: return null
        val songs = songsByArtist[key].orEmpty()
        if (key == null) {
            return ArtistPage(artist, emptyList(), emptyList(), songs.sortedBy { it.titleRank }.map { it.track })
        }
        val own = ownAlbums[key].orEmpty()
        val guest = appearsOnAlbums[key].orEmpty()
        val rank = HashMap<String, Int>()
        (own + guest).forEachIndexed { index, album -> rank.putIfAbsent(album.key, index) }
        val ordered = songs.sortedWith(
            compareBy<Row> { it.albumKey?.let(rank::get) ?: Int.MAX_VALUE }
                .thenBy { it.track.discNumber ?: 1 }
                .thenBy { it.track.trackNumber ?: Int.MAX_VALUE }
                .thenBy { it.titleRank },
        )
        return ArtistPage(artist, own, guest, ordered.map { it.track })
    }

    /** The page of the album with [key], or null when there is none. */
    fun albumPage(key: String): AlbumPage? {
        val album = albumByKey[key] ?: return null
        val discs = albumTracks[key].orEmpty()
            .groupBy { it.track.discNumber ?: 1 }
            .toSortedMap()
            .map { (number, rows) ->
                val ordered = rows.sortedWith(compareBy<Row> { it.track.trackNumber ?: Int.MAX_VALUE }.thenBy { it.titleRank })
                AlbumDisc(number, ordered.map { it.track })
            }
        return AlbumPage(album, discs)
    }

    /** A track with the names the catalog groups it by. */
    private class Row(
        val track: Track,
        val libName: String?,
        val libKey: String?,
        val perfKey: String?,
        val albumTitle: String?,
        val albumKey: String?,
    ) {
        /** Position of the track's title in the library's title order; unique, so it breaks every tie. */
        var titleRank: Int = 0
    }

    /** An album with the keys it is ordered by. */
    private class AlbumSortKeys(
        val entry: AlbumEntry,
        val title: CollationKey,
        val artist: CollationKey?,
        val titleLetter: GroupLetter,
        /** Null when the album has no artist. */
        val artistLetter: GroupLetter?,
    )

    /** A group letter of [SortLetters] and its collation [key]; null for [SortLetters.HASH]. */
    private class GroupLetter(val letter: String, val key: CollationKey?)

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

            // One collation of all titles, so pages compare plain numbers and never touch the collator.
            val titleKeys = rows.map { collator.getCollationKey(it.track.title) }
            rows.indices.sortedWith(compareBy<Int> { titleKeys[it] }.thenBy { rows[it].track.id })
                .forEachIndexed { rank, index -> rows[index].titleRank = rank }

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
            val letters = HashMap<String, GroupLetter>()
            fun letterOf(sortKey: String) = SortLetters.of(sortKey).let { letter ->
                letters.getOrPut(letter) { GroupLetter(letter, if (letter == SortLetters.HASH) null else collate(letter)) }
            }
            val sortKeys = albumEntries.map {
                val titleKey = key(it.title)
                AlbumSortKeys(it, collate(titleKey), it.artistKey?.let(::collate), letterOf(titleKey), it.artistKey?.let(::letterOf))
            }

            // Letter group first, as the artists are, so each letter is one run for the fast scroller.
            val titleOrder = Comparator<AlbumSortKeys> { a, b -> compareLetters(a.titleLetter, b.titleLetter) }
                .thenComparator { a, b -> a.title.compareTo(b.title) }
                .thenComparator { a, b -> compareNullable(a.artist, b.artist) }
                .thenComparator { a, b -> a.entry.key.compareTo(b.entry.key) }
            // Albums with no artist stay last.
            val artistOrder = Comparator<AlbumSortKeys> { a, b -> (a.artistLetter == null).compareTo(b.artistLetter == null) }
                .thenComparator { a, b ->
                    val aLetter = a.artistLetter
                    val bLetter = b.artistLetter
                    if (aLetter == null || bLetter == null) 0 else compareLetters(aLetter, bLetter)
                }
                .thenComparator { a, b -> compareNullable(a.artist, b.artist) }
                .thenComparator { a, b -> a.title.compareTo(b.title) }
                .thenComparator { a, b -> a.entry.key.compareTo(b.entry.key) }
            val yearNewest = Comparator<AlbumSortKeys> { a, b -> compareYear(a.entry.year, b.entry.year, newestFirst = true) }
                .thenComparator { a, b -> a.title.compareTo(b.title) }
                .thenComparator { a, b -> compareNullable(a.artist, b.artist) }
                .thenComparator { a, b -> a.entry.key.compareTo(b.entry.key) }
            val pageOrder = Comparator<AlbumSortKeys> { a, b -> compareYear(a.entry.year, b.entry.year, newestFirst = false) }
                .thenComparator { a, b -> a.title.compareTo(b.title) }
                .thenComparator { a, b -> a.entry.key.compareTo(b.entry.key) }

            val byTitle = sortKeys.sortedWith(titleOrder)
            val byArtistName = sortKeys.sortedWith(artistOrder)
            val albumLists = mapOf(
                AlbumSort.Title to byTitle.map { it.entry },
                AlbumSort.Artist to byArtistName.map { it.entry },
                AlbumSort.YearNewest to sortKeys.sortedWith(yearNewest).map { it.entry },
            )
            val albumRuns = mapOf(
                AlbumSort.Title to LibrarySorter.runs(byTitle.map { SortGroup.Letter(it.titleLetter.letter) }),
                AlbumSort.Artist to LibrarySorter.runs(byArtistName.map { SortGroup.Letter(it.artistLetter?.letter) }),
                AlbumSort.YearNewest to emptyList(),
            )
            val pageSorted = sortKeys.sortedWith(pageOrder).map { it.entry }
            val ownAlbums = pageSorted.groupBy { it.artistKey }

            // Artists: appears-on albums and song lists.
            val albumPosition = HashMap<String, Int>()
            pageSorted.forEachIndexed { index, album -> albumPosition[album.key] = index }
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
            val appearsOn = guestAlbumKeys.mapValues { (_, albumKeys) ->
                albumKeys.sortedBy { albumPosition.getValue(it) }.map { albumByKey.getValue(it) }
            }

            val entries = byArtist.keys.map { artistKey ->
                ArtistEntry(
                    key = artistKey,
                    name = artistNames[artistKey],
                    // The unknown entry has no albums of its own, though its tracks may share one.
                    albumCount = if (artistKey == null) 0 else ownAlbums[artistKey]?.size ?: 0,
                    songCount = songsByArtist[artistKey]?.size ?: 0,
                    artUri = if (artistKey == null) null else ownAlbums[artistKey]?.firstNotNullOfOrNull { it.albumArtUri },
                )
            }
            val ordered = orderArtists(entries, collator)
            val runs = LibrarySorter.runs(ordered.map { SortGroup.Letter(it.key?.let(SortLetters::of)) })

            return MusicCatalog(
                artists = ordered,
                artistRuns = runs,
                albumLists = albumLists,
                albumRunLists = albumRuns,
                artistByKey = entries.associateBy { it.key },
                albumByKey = albumByKey,
                albumTracks = byAlbum,
                ownAlbums = ownAlbums,
                appearsOnAlbums = appearsOn,
                songsByArtist = songsByArtist,
                albumKeyByTrack = rows.mapNotNull { row -> row.albumKey?.let { row.track.id to it } }.toMap(),
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

        /** `#` first, then letters by the collator; two letters it calls equal still differ. */
        private fun compareLetters(a: GroupLetter, b: GroupLetter): Int {
            if (a === b) return 0
            val aHash = a.key == null
            val bHash = b.key == null
            if (aHash != bHash) return if (aHash) -1 else 1
            return compareNullable(a.key, b.key).takeIf { it != 0 } ?: a.letter.compareTo(b.letter)
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
