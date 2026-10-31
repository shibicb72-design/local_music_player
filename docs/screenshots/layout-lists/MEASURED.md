# Songs and Videos list rows: dp values

Values as set in code, and, where the pixels show them, as measured from `after/songs.png` and
`after/videos.png`. The constants are in `app/src/main/java/com/shibi/musicplayer/ui/ListRows.kt`.
Row margins at six real phone sizes are in `DEVICES.md`.

**Screenshots and density.** Both PNGs are 1233 × 2673 px, recorded by `recordRoborazziDebug`
from `ScreenshotTest.list` (Songs) and `VideosScreenshotTest.compactDark` (Videos) with the
Robolectric qualifiers `w411dp-h891dp-xxhdpi`: density **3.0** (480 dpi), so 1 dp = 3 px.
`before/` holds the same two tests recorded before the change.

**How the pixels were read.** A Pillow script (kept out of the repo) scanned each PNG: art = runs
of pixels that differ from the page colour on a scan line through the art's centre (x = 36 dp),
then its left and right edges at the run's middle; the row height = the pitch between one art's
top and the next (the rows touch, so pitch = height); the pill = the bounding box of its white
pixels; the ⋮ = the column of three dots. The corner radius is a least-squares fit of a circle to
the art's top-left edge, row by row. As a check, the same script reads the before shots as 52 dp
song art at 20 dp with a 68 dp pitch, and 96 × 54 dp video frames at 20 dp with a 70 dp pitch,
which is what the old code set.

| Value | In code | Measured from the after PNGs | Source |
|---|---|---|---|
| Side margin, start: the art's left edge from the screen's left edge | 16 dp (`ListRowSideMargin`: 4 dp fill inset + 12 dp padding) | 48 px = **16.00 dp**, all 8 visible rows of each list | measured |
| Side margin, end: the ⋮ icon's right edge from the screen's right edge | 16 dp (`ListRowSideMargin`; the end padding `ListRowEndPadding` is 0 dp because the 24 dp glyph sits 12 dp inside its 48 dp target) | dots centred at x = 1149 px = 383.00 dp, **28.00 dp** from the right edge in both lists = 16 dp margin + 12 dp (half the 24 dp icon) | dot centre measured; the 24 dp icon box is from code |
| Row height | 60 dp minimum (`ListRowHeight`); larger font scales grow it | pitch 180 px = **60.00 dp**, 7 pitches in each list | measured |
| Thumbnail size | 40 × 40 dp (`ListThumbnailSize`) | 120 × 120 px = **40.00 × 40.00 dp**, 16 thumbnails | measured |
| Thumbnail corner radius | 8 dp (`ListThumbnailCorner`) | **7.4 to 7.7 dp**. The same fit reads the before shots' known 12 dp corners as 11.6 to 11.7 dp (about 0.3 dp low, from the edge threshold), so this matches 8 dp | measured (estimate) |
| Overflow (⋮) touch target | 48 × 48 dp (`ListRowButtonTouchSize`: Material 3 `IconButton` keeps its minimum interactive size) | not visible: the target is invisible and the 40 dp state layer only shows when pressed | code only; `ListRowsUiTest` asserts 48 × 48 dp touch bounds on every ⋮ |
| Play all pill height | 48 dp minimum (`PlayAllPillHeight`), at the 16 dp side margin | white pill 144 px = **48.00 dp** tall; left edge 48 px = **16.00 dp**; 118.33 dp wide | measured |
| Row fill inset (light backdrops, playing or selected row) | 4 dp each side, 16 dp corners; rows keep the list's full width | the fill is transparent on the dark backdrop of these two shots; on blush it measures 4 dp each side (`DEVICES.md`) | code; measured in `DEVICES.md` |

The rows no longer leave room at their end for the fast-scroll thumb (that gap was the width bug,
see `DEVICES.md`): the thumb shows over the row ends only while it is up, a row's heart always
ends left of it, and once it fades nothing covers a ⋮.

The semantics tree confirms the same values: `ListRowsUiTest` (Robolectric, same qualifiers)
asserts 60 dp rows (also while selecting and with a watched hairline), 40 dp art 16 dp from the
list's start and centred in the row, the ⋮ glyph 16 dp from the list's end with and without the
fast scroller, 48 × 48 dp ⋮ touch targets, hearts that end left of the fast-scroll thumb, the
video month header on the 16 dp margin, and a 48 dp Play all Button 16 dp from the list's start.
`ListRowsTextUiTest` (native graphics) checks that a 2x font grows the row past 60 dp instead of
clipping it, and `RowWidthUiTest` that Songs and Videos rows share their bounds and even margins
at six phone sizes.

Before, for comparison (same method, `before/`): Songs rows 68 dp with 52 dp art at 20 dp and
12 dp corners, the ⋮ centred 76 dp from the screen's right edge (the rows ended 48 dp short of
it); Videos rows 70 dp with 96 × 54 dp frames at 20 dp; no Play all pill.
