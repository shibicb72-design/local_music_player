# Favorites, Recent and Search rows at six phone sizes

#46 gave the Songs and Videos lists their list rows (`ListRow` in
`app/src/main/java/com/shibi/musicplayer/ui/ListRows.kt`): a fill 4 dp in from each edge,
60 dp rows, 40 dp art with 8 dp corners, and content 16 dp in. This branch draws Favorites,
Recent and Search with the same rows. This page measures their row margins at the six phone
sizes of `../layout-lists/DEVICES.md`, before and after.

**These are Robolectric / Roborazzi renders, not emulator or device captures.** Each image is
the app's Compose UI drawn on the JVM by Robolectric 4.17 (native graphics, SDK 35) at the
profile's width, height and density. They do not show the phone's own status bar, navigation
bar, punch-hole cutout or One UI font settings.

## How the shots were made

`DeviceWidthScreenshotTest` draws each screen at the six sizes (`DeviceProfiles.kt`; table in
`../layout-lists/DEVICES.md`), in the app's compact chrome (the mini player and the carousel) on
the **blush** backdrop. On blush every row draws a translucent white fill, so a row's width is
visible.

- **Favorites:** `FavoritesScreen`, six liked songs of the twelve-song library, in the
  library's order. Paper Planes is playing.
- **Recent:** `RecentHistoryScreen`, nine plays over Today, Yesterday and Wednesday, newest
  first: six songs and three videos (two load a frame, one shows the film tile). The clock is
  fixed at Sat 2026-10-17 21:30 and the zone is pinned to New York.
- **Search:** `SearchContent` with the query "o": eight songs by title or artist, in the
  library's order.

- **Before:** `feat/layout-lists` (#46) at `1307efb`. The test files, and nothing else, were
  copied into a temporary `git worktree` of `1307efb` and recorded there. The worktree was
  then removed.
- **After:** this branch at `04203b0`, with the change. Recorded again there, the 18 renders
  match the measured ones pixel for pixel.

The composites are `<profile>_before.png` and `<profile>_after.png`: Favorites, Recent and
Search left to right, at full resolution, under a label strip.

**How the margins were read.** A Pillow script (kept out of the repo) worked on the original
renders, not the composites. It used the method of `../layout-lists/DEVICES.md`:
- It found each row on a vertical scan at x = 12 dp, which is inside every row's fill and left
  of its art. The fill's 16 dp corners leave a short gap there between touching rows.
- It kept runs longer than 30 dp where x = 1 px is the backdrop. That drops the mini player
  (its shadow tints x = 1 px) and the carousel.
- It read each row's fill edges at the row's middle, where the fill's sides are straight. An
  edge pixel's coverage gives the sub-pixel position.
- It also read the art's left edge and size on that line, and each row's height as the pitch
  to the next touching row. The last row can sit in the list's bottom edge fade or be cut off
  by the list's end: its margins count, its art does not.

Every visible row was measured: 5 to 9 per shot, 120 rows before and 124 after. In every shot
all rows read the same margins.

Layout snaps each padding to whole pixels. So 4 dp draws as 11 px at 2.8125 (3.91 dp) and at
2.625 (4.19 dp), and as 13 px at 3.125 (4.16 dp). 8 dp at 2.8125 is 23 px (8.18 dp).

## Row margins (dp from the screen's edges to the row's fill, left / right; rows measured)

| Profile | Favorites before | Recent before | Search before | Favorites after | Recent after | Search after |
|---|---|---|---|---|---|---|
| `s24ultra` | 8.18 / 8.18 (6) | 8.18 / 8.18 (7) | 8.18 / 8.18 (7) | **3.91 / 3.91 (6)** | **3.91 / 3.91 (7)** | **3.91 / 3.91 (8)** |
| `s24ultra_small` | 8.00 / 8.00 (6) | 8.00 / 8.00 (8) | 8.00 / 8.00 (8) | **4.19 / 4.19 (6)** | **4.19 / 4.19 (8)** | **4.19 / 4.19 (8)** |
| `s25` | 8.00 / 8.00 (6) | 8.00 / 8.00 (6) | 8.00 / 8.00 (7) | **4.00 / 4.00 (6)** | **4.00 / 4.00 (7)** | **4.00 / 4.00 (8)** |
| `s25ultra` | 8.18 / 8.18 (6) | 8.18 / 8.18 (7) | 8.18 / 8.18 (7) | **3.91 / 3.91 (6)** | **3.91 / 3.91 (7)** | **3.91 / 3.91 (8)** |
| `narrow` | 8.00 / 8.00 (6) | 8.00 / 8.00 (5) | 8.00 / 8.00 (5) | **4.00 / 4.00 (6)** | **4.00 / 4.00 (5)** | **4.00 / 4.00 (5)** |
| `wide412` | 8.00 / 8.00 (6) | 8.00 / 8.00 (9) | 8.00 / 8.00 (8) | **4.16 / 4.16 (6)** | **4.16 / 4.16 (9)** | **4.16 / 4.16 (8)** |

The after values are the Songs and Videos rows' at every size (`../layout-lists/DEVICES.md`,
"After, Songs" and "After, Videos").

- **Before:** the three screens already had even margins (they never had the Songs list's
  fast-scroll gutter), but at 8 dp: the old rows' inset, twice the list rows'.
- **After:** all three screens, every row, 4 dp from each edge, the same as Songs and Videos.

## Art and row height (same renders)

| Value | Before | After |
|---|---|---|
| Art's left edge | 20 dp (20.27 at 2.8125, 20.19 at 2.625, 20.16 at 3.125) | **16 dp** (16.00 at 2.8125, 16.38 at 2.625, 16.32 at 3.125) |
| Art size | Favorites and Search 52 × 52 dp; Recent 48 × 48 dp (songs) and 64 × 36 dp (video frames) | **40 × 40 dp** on all three, video frames cropped square (40.18 at 2.8125) |
| Row height | Favorites and Search 68 dp; Recent 64 dp (songs) and 52 dp (videos) | **60 dp** on all three, songs and videos alike |

## The semantics tree agrees

- `RowWidthUiTest` (Robolectric, the six sizes): the first Favorites, Recent and Search row
  has the Songs row's bounds, with even margins and the 4 dp inset. At `1307efb` all 18 of
  these cases fail, for example "Favorites and Songs rows start together expected:<11.0> but
  was:<23.0>" at the S24 Ultra (4 dp against 8 dp). The 6 Songs-against-Videos cases pass at
  both.
- `ListRowsUiTest` (411 dp, xxhdpi): on Favorites, Search and Recent, 60 dp rows with 40 dp
  art 16 dp in and centred. On Favorites and Search the ⋮ glyph ends 16 dp from the list's end
  with a 48 × 48 dp touch target; on Recent the time ends 16 dp from it. At `1307efb` those
  four cases fail: 68 dp and 64 dp rows, and the time 20 dp from the end. The rows keep their
  order (Favorites as given, Search in LibrarySearch's order, Recent newest first). They also
  keep their behaviour: a tap plays the list from that song, or the history entry. The ⋮ only
  adds to a playlist, with no trash. A long press adds to a playlist, and Recent has no ⋮. Those
  cases pass before and after.
