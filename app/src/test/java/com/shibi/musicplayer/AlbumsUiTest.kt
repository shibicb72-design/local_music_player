package com.shibi.musicplayer

import android.Manifest
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex
import com.shibi.musicplayer.ui.albumMinutes
import org.junit.Assert.assertFalse
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.domain.AlbumSort
import com.shibi.musicplayer.domain.SortField
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.library.InMemorySortPrefs
import com.shibi.musicplayer.library.LibraryViewModel
import com.shibi.musicplayer.library.MusicRepository
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.ui.AppBackHandler
import com.shibi.musicplayer.ui.LibraryScreen
import com.shibi.musicplayer.ui.SortButtonTag
import com.shibi.musicplayer.ui.sortOptionTag
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Albums grid and an album page on the real screen: the real view model over a fake
 * MediaStore with The Beatles (Revolver, Abbey Road), ABBA's two-disc Greatest Hits, Queen's
 * Greatest Hits, Zedd's Clarity (one song featuring a guest), Daft Punk's Homework (no year, no
 * track number) and two untagged songs. Tall enough that the whole grid and a page fit.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w600dp-h1400dp-xxhdpi")
class AlbumsUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var provider: FakeAudioProvider
    private lateinit var prefs: InMemorySortPrefs
    private lateinit var viewModel: LibraryViewModel
    private val played = mutableListOf<Pair<List<Long>, Long>>()
    private val shuffled = mutableListOf<List<Long>>()
    private var outerBacks = 0

    @Before
    fun setUp() {
        val application = compose.activity.application
        shadowOf(application).grantPermissions(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        provider = Robolectric.buildContentProvider(FakeAudioProvider::class.java)
            .create(MediaStore.AUTHORITY)
            .get()
        provider.rows += song(1L, "Revolver One", "The Beatles", "Revolver", "The Beatles", 1, 1966)
        provider.rows += song(2L, "Revolver Two", "The Beatles", "Revolver", "The Beatles", 2, 1966)
        provider.rows += song(3L, "Abbey One", "The Beatles", "Abbey Road", "The Beatles", 1, 1969)
        // MediaStore keeps the disc in the thousands: 2001 is disc 2, track 1.
        provider.rows += song(24L, "Fernando", "ABBA", "Greatest Hits", "ABBA", 2002, 1976)
        provider.rows += song(22L, "Waterloo", "ABBA", "Greatest Hits", "ABBA", 1002, 1976)
        provider.rows += song(23L, "Dancing Queen", "ABBA", "Greatest Hits", "ABBA", 2001, 1976)
        provider.rows += song(21L, "Mamma Mia", "ABBA", "Greatest Hits", "ABBA", 1001, 1976)
        provider.rows += song(31L, "Somebody", "Queen", "Greatest Hits", "Queen", 1, 1981)
        provider.rows += song(41L, "Clarity", "Zedd", "Clarity", "Zedd", 1, 2012)
        provider.rows += song(42L, "Stay", "Zedd feat. Guest Singer", "Clarity", "Zedd", 2, 2012)
        provider.rows += song(51L, "Da Funk", "Daft Punk", "Homework", "Daft Punk", null, null)
        provider.rows += song(7L, "Zeta", null, "TestSongs", null, null, null, "zeta.mp3")
        provider.rows += song(8L, "Alpha", null, "TestSongs", null, null, null, "alpha.mp3")
        prefs = InMemorySortPrefs()
        viewModel = newViewModel()
    }

    @Test
    fun theGridShowsEveryAlbumWithItsArtistAndYearInTitleOrder() {
        showAlbums()
        chip("Albums").assertIsSelected()
        compose.onNodeWithText("6 albums · Album A–Z").assertIsDisplayed()
        assertEquals(
            listOf(AbbeyRoad, Clarity, AbbaHits, QueenHits, Homework, Revolver),
            shownCards(AllCards),
        )
        // No year: the meta line is the artist alone.
        compose.onNodeWithContentDescription(Homework).assertIsDisplayed()
        // Neither Play all nor a song row.
        compose.onNodeWithText("Play all").assertDoesNotExist()
        compose.onNodeWithText("Revolver One").assertDoesNotExist()
    }

    @Test
    fun eachSortReordersTheGridAndANewViewModelStartsOnTheStoredSort() {
        showAlbums()
        pickSort(SortSpec(SortField.Artist, true))
        val byArtist = shownCards(AllCards)
        assertEquals(cardsOf(AlbumSort.Artist), byArtist)
        assertEquals(AbbaHits, byArtist.first())
        compose.onNodeWithText("6 albums · Artist A–Z").assertIsDisplayed()

        pickSort(SortSpec(SortField.Date, false))
        val byYear = shownCards(AllCards)
        assertEquals(cardsOf(AlbumSort.YearNewest), byYear)
        assertEquals(listOf(Clarity, QueenHits, AbbaHits, AbbeyRoad, Revolver), byYear.take(5))
        assertNotEquals(byArtist, byYear)
        compose.onNodeWithText("6 albums · Newest first").assertIsDisplayed()

        val next = newViewModel()
        settle()
        assertEquals(AlbumSort.YearNewest, next.uiState.value.albumSort)
    }

    @Test
    fun twoAlbumsWithTheSameNameByDifferentArtistsAreTwoCards() {
        showAlbums()
        compose.onNodeWithContentDescription(AbbaHits).assertIsDisplayed()
        compose.onNodeWithContentDescription(QueenHits).assertIsDisplayed()
    }

    @Test
    fun anUntaggedSongsFolderNameIsNoAlbumCard() {
        showAlbums()
        compose.onAllNodes(hasContentDescription("TestSongs", substring = true)).assertCountEquals(0)
    }

    @Test
    fun tappingACardOpensThePageWithTheMetaLinesAndTheTracksInOrder() {
        showAlbums()
        compose.onNodeWithContentDescription(Revolver).performClick()
        settle()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithText("Revolver").assertIsDisplayed()
        compose.onNodeWithText("The Beatles · 1966").assertIsDisplayed()
        compose.onNodeWithText("2 songs · 6 min").assertIsDisplayed()
        chip("Albums").assertDoesNotExist()
        assertEquals(listOf("Revolver One", "Revolver Two"), shownTitles(listOf("Revolver Two", "Revolver One")))
        compose.onNode(hasText("Revolver One") and hasText("1")).assertIsDisplayed()
        compose.onNode(hasText("Revolver Two") and hasText("2")).assertIsDisplayed()
        // One disc: no heading.
        compose.onAllNodesWithText("Disc", substring = true).assertCountEquals(0)
    }

    @Test
    fun aTwoDiscAlbumHasADiscHeadingAboveEachDisc() {
        openAlbum(AbbaHits)
        compose.onNodeWithText("ABBA · 1976").assertIsDisplayed()
        compose.onNodeWithText("4 songs · 12 min").assertIsDisplayed()
        assertEquals(
            listOf("Disc 1", "Mamma Mia", "Waterloo", "Disc 2", "Dancing Queen", "Fernando"),
            shownTitles(listOf("Fernando", "Disc 2", "Waterloo", "Dancing Queen", "Disc 1", "Mamma Mia")),
        )
        compose.onNode(hasText("Dancing Queen") and hasText("1")).assertIsDisplayed()
    }

    @Test
    fun aTrackWithNoNumberShowsADash() {
        openAlbum(Homework)
        compose.onNodeWithText("Daft Punk").assertIsDisplayed()
        compose.onNode(hasText("Da Funk") and hasText("—")).assertIsDisplayed()
    }

    @Test
    fun onlyAGuestArtistShowsInTheDetailLine() {
        openAlbum(Clarity)
        compose.onNode(hasText("Stay") and hasText("Zedd feat. Guest Singer")).assertIsDisplayed()
        // The album's own artist is not repeated on its songs.
        compose.onAllNodesWithText("Zedd").assertCountEquals(0)
    }

    @Test
    fun playShuffleAndARowTapTakeTheAlbumsTracksInPageOrder() {
        openAlbum(AbbaHits)
        val order = listOf(21L, 22L, 23L, 24L)
        compose.onNodeWithText("Fernando").performClick()
        settle()
        assertEquals(order to 24L, played.last())

        compose.onNodeWithText("Play all").performClick()
        settle()
        assertEquals(order to 21L, played.last())

        compose.onNodeWithText("Shuffle").performClick()
        settle()
        assertEquals(listOf(order), shuffled)
    }

    @Test
    fun backClosesTheAlbumPageThenLeavesTheScreen() {
        openAlbum(Clarity)
        pressBack()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        compose.onNodeWithContentDescription(Clarity).assertIsDisplayed()
        chip("Albums").assertIsSelected()
        assertEquals(0, outerBacks)
        pressBack()
        assertEquals(1, outerBacks)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun onAPhoneTheGridKeepsItsPlaceAcrossAPageASortJumpsToTheTopAndEachPageStartsAtItsTop() {
        // Eight more one-song albums (14 in all), so the grid scrolls; Clarity gets 15 more songs, so its page does.
        for (n in 1..8) {
            val band = "Band %02d".format(n)
            provider.rows += song(100L + n, "Filler $n", band, "Zz Single %02d".format(n), band, 1, 2000 + n)
        }
        for (n in 3..17) provider.rows += song(200L + n, "Clarity Extra $n", "Zedd", "Clarity", "Zedd", n, 2012)
        val lastCard = "Zz Single 08, Band 08 · 2008"
        showAlbums()
        assertFalse(shown(compose.onNodeWithContentDescription(lastCard)))
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(13)
        settle()
        compose.onNodeWithContentDescription(lastCard).assertIsDisplayed()

        // An album page and Back: the grid is where it was.
        compose.onNodeWithContentDescription(lastCard).performClick()
        settle()
        compose.onNodeWithText("Band 08 · 2008").assertIsDisplayed()
        pressBack()
        compose.onNodeWithContentDescription(lastCard).assertIsDisplayed()

        // A new sort starts at the top of its order: Clarity (2012) is the newest.
        pickSort(SortSpec(SortField.Date, false))
        compose.onNodeWithContentDescription(Clarity).assertIsDisplayed()

        // Each album page starts at its own top, even after another was scrolled.
        compose.onNodeWithContentDescription(Clarity).performClick()
        settle()
        compose.onNodeWithText("Zedd · 2012").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(17)
        settle()
        assertFalse(shown(compose.onNodeWithText("Zedd · 2012")))
        pressBack()
        compose.onNodeWithContentDescription(lastCard).performClick()
        settle()
        compose.onNodeWithText("Band 08 · 2008").assertIsDisplayed()
        pressBack()
        compose.onNodeWithContentDescription(Clarity).performClick()
        settle()
        compose.onNodeWithText("Zedd · 2012").assertIsDisplayed()
    }

    @Test
    fun anAlbumsMinutesRoundToTheNearestAndAreNeverUnderOne() {
        assertEquals(1, albumMinutes(89_000L))
        assertEquals(2, albumMinutes(90_000L))
        assertEquals(1, albumMinutes(0L))
        assertEquals(60, albumMinutes((59 * 60 + 31) * 1_000L))
    }

    private fun shown(node: SemanticsNodeInteraction): Boolean = runCatching { node.assertIsDisplayed() }.isSuccess

    private fun cardsOf(sort: AlbumSort): List<String> =
        viewModel.uiState.value.catalog!!.albums(sort).map { album ->
            listOfNotNull(album.artistName, album.year?.toString()).joinToString(" · ").let { "${album.title}, $it" }
        }

    private fun pickSort(spec: SortSpec) {
        compose.onNodeWithTag(SortButtonTag).performClick()
        settle()
        compose.onNodeWithTag(sortOptionTag(spec)).performClick()
        settle()
    }

    private fun openAlbum(description: String) {
        showAlbums()
        compose.onNodeWithContentDescription(description).performClick()
        settle()
    }

    private fun showAlbums() {
        show()
        chip("Albums").performClick()
        settle()
    }

    private fun chip(label: String) = compose.onNodeWithContentDescription(label)

    private fun newViewModel() = LibraryViewModel(
        application = compose.activity.application,
        repository = MusicRepository(compose.activity.application),
        ioDispatcher = Dispatchers.Unconfined,
        sortPrefs = prefs,
    )

    private fun show() {
        compose.setContent {
            MusicPlayerTheme {
                // Composed first, so the screen's own handlers are newer and win while enabled.
                AppBackHandler { outerBacks++ }
                Box(Modifier.fillMaxSize()) {
                    LibraryScreen(
                        viewModel = viewModel,
                        nowPlaying = PlayerUiState(),
                        onTrackSelected = { tracks: List<Track>, id: Long -> played += tracks.map { it.id } to id },
                        onOpenPlayer = {},
                        onTogglePlayPause = {},
                        onShuffle = { tracks -> shuffled += tracks.map { it.id } },
                        contentBottomPadding = 0.dp,
                    )
                }
            }
        }
        settle()
    }

    /** The cards on screen, row by row, each row left to right. */
    private fun shownCards(descriptions: List<String>): List<String> =
        descriptions.mapNotNull { description ->
            compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().firstOrNull()
                ?.let { description to it.boundsInRoot }
        }.sortedWith(compareBy({ it.second.top }, { it.second.left })).map { it.first }

    /** The titles on screen, top to bottom. */
    private fun shownTitles(titles: List<String>): List<String> =
        titles.mapNotNull { title ->
            compose.onAllNodesWithText(title).fetchSemanticsNodes().firstOrNull()?.let { title to it.boundsInRoot.top }
        }.sortedBy { it.second }.map { it.first }

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun settle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    /** A MediaStore audio row; see MusicRepositoryTest.row for the column order. */
    private fun song(
        id: Long,
        title: String,
        artist: String?,
        album: String,
        albumArtist: String?,
        track: Int?,
        year: Int?,
        fileName: String = "$title.mp3",
    ): Array<Any?> {
        val folder = if (artist == null) "Music/TestSongs/" else "Music/$album/"
        return arrayOf(
            id, title, artist, album, 180_000L, 0L, null, 4_000_000L, albumArtist, track, year,
            fileName, null, folder, "/storage/emulated/0/$folder$fileName",
        )
    }

    private companion object {
        const val Revolver = "Revolver, The Beatles · 1966"
        const val AbbeyRoad = "Abbey Road, The Beatles · 1969"
        const val AbbaHits = "Greatest Hits, ABBA · 1976"
        const val QueenHits = "Greatest Hits, Queen · 1981"
        const val Clarity = "Clarity, Zedd · 2012"
        const val Homework = "Homework, Daft Punk"
        val AllCards = listOf(Revolver, AbbeyRoad, AbbaHits, QueenHits, Clarity, Homework)
    }
}
