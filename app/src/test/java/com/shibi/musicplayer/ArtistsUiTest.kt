package com.shibi.musicplayer

import android.Manifest
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.domain.MusicView
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.library.InMemorySortPrefs
import com.shibi.musicplayer.library.LibraryViewModel
import com.shibi.musicplayer.library.MusicRepository
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.ui.AppBackHandler
import com.shibi.musicplayer.ui.LibraryScreen
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Songs / Artists / Albums chips, the Artists list and an artist page on the real screen:
 * the real view model over a fake MediaStore with The Beatles (two albums), Daft Punk (one album,
 * and a guest on Various Artists' Club Mix), Zedd (one album, and a guest there too) and two untagged songs.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ArtistsUiTest {

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
        provider.rows += song(4L, "One A", "Daft Punk", "Album One", "Daft Punk", 1, 2001)
        provider.rows += song(5L, "Club DP", "Daft Punk", "Club Mix", "Various Artists", 1, 2010)
        provider.rows += song(6L, "Club Zedd", "Zedd", "Club Mix", "Various Artists", 2, 2010)
        provider.rows += song(9L, "Clarity", "Zedd", "Clarity", "Zedd", 1, 2012)
        provider.rows += song(10L, "Stay", "Zedd", "Clarity", "Zedd", 2, 2012)
        provider.rows += song(7L, "Zeta", null, "TestSongs", null, null, null, "zeta.mp3")
        provider.rows += song(8L, "Alpha", null, "TestSongs", null, null, null, "alpha.mp3")
        prefs = InMemorySortPrefs()
        viewModel = newViewModel()
    }

    @Test
    fun songsIsUnchangedUnderTheChips() {
        show()
        chip("Songs").assertIsSelected()
        chip("Artists").assertIsDisplayed()
        chip("Albums").assertIsDisplayed()
        compose.onNodeWithText("Play all").assertIsDisplayed()
        val order = viewModel.uiState.value.songs!!.items.map { it.title }
        assertEquals(10, order.size)
        // The list is lazy: the rows on screen are the first ones, in the same order.
        val shown = shownTitles(order)
        assertTrue("rows on screen: $shown", shown.size >= 6)
        assertEquals(order.take(shown.size), shown)
        compose.onNodeWithText("Revolver Two").performClick()
        settle()
        assertEquals(viewModel.uiState.value.songs!!.items.map { it.id } to 2L, played.single())
    }

    @Test
    fun artistsShowsOneRowPerArtistInOrderWithTheirCounts() {
        show()
        chip("Artists").performClick()
        settle()
        chip("Artists").assertIsSelected()
        // "The Beatles" sits under B, before Daft Punk; the unknown artist is last.
        assertEquals(
            listOf("The Beatles", "Daft Punk", "Various Artists", "Zedd", "Unknown artist"),
            shownTitles(listOf("Zedd", "Unknown artist", "Daft Punk", "The Beatles", "Various Artists")),
        )
        compose.onNodeWithText("4 artists").assertIsDisplayed()
        compose.onNodeWithText("2 albums · 3 songs").assertIsDisplayed()
        // Daft Punk and Various Artists.
        compose.onAllNodesWithText("1 album · 2 songs").assertCountEquals(2)
        compose.onNodeWithText("1 album · 3 songs").assertIsDisplayed()
        // The unknown artist never has an album: the count is its songs alone.
        compose.onNodeWithText("2 songs").assertIsDisplayed()
        compose.onNodeWithText("Play all").assertDoesNotExist()
        compose.onNodeWithText("Revolver One").assertDoesNotExist()
    }

    @Test
    fun anArtistPageShowsTheCountsTheAlbumsAndTheSongsInPageOrder() {
        show()
        openArtist("The Beatles")
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithText("The Beatles").assertIsDisplayed()
        compose.onNodeWithText("2 albums · 3 songs").assertIsDisplayed()
        compose.onNodeWithText("1966 · 2 songs").assertIsDisplayed()
        compose.onNodeWithText("1969 · 1 song").assertIsDisplayed()
        compose.onNodeWithText("Appears on").assertDoesNotExist()
        // The chips give way to the page.
        chip("Artists").assertDoesNotExist()
        assertEquals(
            listOf("Revolver One", "Revolver Two", "Abbey One"),
            shownTitles(listOf("Abbey One", "Revolver Two", "Revolver One")),
        )
    }

    @Test
    fun anArtistPagesSongsShowTheirKeptAlbumAndNeverAFolderName() {
        show()
        openArtist("The Beatles")
        // The album card and the two Revolver songs' detail lines; the artist is not repeated.
        compose.onAllNodesWithText("Revolver").assertCountEquals(3)
        compose.onAllNodesWithText("The Beatles").assertCountEquals(1)
        compose.onNodeWithText("The Beatles · Revolver").assertDoesNotExist()

        pressBack()
        compose.onNodeWithText("Unknown artist").performClick()
        settle()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        assertEquals(listOf("Alpha", "Zeta"), shownTitles(listOf("Zeta", "Alpha")))
        // Their album is only their folder's name: the rows show just the length.
        compose.onAllNodesWithText("TestSongs", substring = true).assertCountEquals(0)
    }

    @Test
    fun switchingFromAScrolledSongsListShowsArtistsFromTheTop() {
        // Thirty more artists with one song each, so both lists scroll.
        for (n in 1..30) {
            val name = "Artist %02d".format(n)
            provider.rows += song(100L + n, "Filler $n", name, "Single $n", name, 1, 2020)
        }
        show()
        repeat(3) { verticalList().performTouchInput { swipeUp() } }
        settle()
        chip("Artists").performClick()
        settle()
        compose.onNodeWithText("Artist 01").assertIsDisplayed()
        compose.onNodeWithText("34 artists").assertIsDisplayed()
    }

    @Test
    fun aGuestsPageShowsTheAlbumItAppearsOn() {
        show()
        openArtist("Daft Punk")
        compose.onNodeWithText("1 album · 2 songs").assertIsDisplayed()
        compose.onNodeWithText("Albums").assertIsDisplayed()
        compose.onNodeWithText("2001 · 1 song").assertIsDisplayed()
        compose.onNodeWithText("Appears on").assertIsDisplayed()
        compose.onNodeWithText("2010 · 2 songs").assertIsDisplayed()
        assertEquals(listOf("One A", "Club DP"), shownTitles(listOf("Club DP", "One A")))
    }

    @Test
    fun aSongOnThePagePlaysTheArtistsSongsAndTheButtonsTakeThemToo() {
        show()
        openArtist("The Beatles")
        compose.onNodeWithText("Revolver Two").performClick()
        settle()
        assertEquals(listOf(1L, 2L, 3L) to 2L, played.last())

        compose.onNodeWithText("Play all").performClick()
        settle()
        assertEquals(listOf(1L, 2L, 3L) to 1L, played.last())

        compose.onNodeWithText("Shuffle").performClick()
        settle()
        assertEquals(listOf(listOf(1L, 2L, 3L)), shuffled)
    }

    @Test
    fun backClosesThePageThenLeavesTheScreen() {
        show()
        openArtist("Zedd")
        pressBack()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        compose.onNodeWithText("4 artists").assertIsDisplayed()
        chip("Artists").assertIsSelected()
        assertEquals(0, outerBacks)
        pressBack()
        assertEquals(1, outerBacks)
    }

    @Test
    fun changingTheViewClosesThePageAndANewViewModelStartsOnTheStoredView() {
        show()
        openArtist("The Beatles")
        compose.runOnUiThread { viewModel.setView(MusicView.Songs) }
        settle()
        assertNull(viewModel.uiState.value.openArtistKey)
        chip("Songs").assertIsSelected()
        chip("Artists").performClick()
        settle()
        compose.onNodeWithText("4 artists").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()

        val next = newViewModel()
        settle()
        assertEquals(MusicView.Artists, next.uiState.value.view)
    }

    @Test
    fun theChipsHideWhileSelectingAndComeBackAfter() {
        show()
        compose.onNodeWithText("Alpha").performTouchInput { longClick() }
        settle()
        chip("Artists").assertDoesNotExist()
        pressBack()
        chip("Artists").assertIsDisplayed()
        chip("Songs").assertIsSelected()
        assertEquals(0, outerBacks)
    }

    @Test
    fun albumsShowsThePlaceholder() {
        show()
        chip("Albums").performClick()
        settle()
        chip("Albums").assertIsSelected()
        compose.onNodeWithText("Albums are coming in the next update.").assertIsDisplayed()
        compose.onNodeWithText("Revolver One").assertDoesNotExist()
    }

    private fun openArtist(name: String) {
        chip("Artists").performClick()
        settle()
        compose.onNodeWithText(name).performClick()
        settle()
    }

    private fun chip(label: String) = compose.onNodeWithContentDescription(label)

    /** The chips scroll sideways: the list is the vertical one. */
    private fun verticalList() =
        compose.onNode(hasScrollAction() and !SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))

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
}
