# Songs and Videos rows at six phone sizes (the width bug)

Shibi saw it on a Galaxy S24 Ultra running the Play build (`main`, `39fa00e`). On the Songs tab
the rows stopped well short of the right edge, so the list looked shifted left. The Videos tab's
rows filled the width with even margins. This page measures both lists at six phone sizes,
before and after the fix.

**These are Robolectric / Roborazzi renders, not emulator or device captures.** Each image is
the app's Compose UI drawn on the JVM by Robolectric 4.17 (native graphics, SDK 35) at the
profile's width, height and density. They do not show the phone's own status bar, navigation
bar, punch-hole cutout or One UI font settings.

## Profiles

The numbers and sources are as given in the request; they were not re-checked here. A render's
pixel size is the dp size times the density, rounded down.

| Profile | Phone | Robolectric qualifiers | Density (DPR) | Render (px) | Source |
|---|---|---|---|---|---|
| `s24ultra` | Galaxy S24 Ultra, default FHD+ display size | `w384dp-h832dp-450dpi` | 2.8125 | 1080 × 2340 | DeviceAtlas, webmobilefirst.com |
| `s24ultra_small` | Galaxy S24 Ultra, Display size "smaller" (the wide case) | `w412dp-h892dp-420dpi` | 2.625 | 1081 × 2341 | DeviceAtlas |
| `s25` | Galaxy S25 | `w360dp-h780dp-480dpi` | 3.0 | 1080 × 2340 | yesviz.com |
| `s25ultra` | Galaxy S25 Ultra | `w384dp-h832dp-450dpi` | 2.8125 (2.813) | 1080 × 2340 | breakpane.com, DeviceAtlas |
| `narrow` | a generic narrow phone | `w360dp-h640dp-480dpi` | 3.0 | 1080 × 1920 | generic profile |
| `wide412` | a 412 × 915 dp phone | `w412dp-h915dp-500dpi` | 3.125 | 1287 × 2859 | requested profile |

The qualifiers are constants in `app/src/test/java/com/shibi/musicplayer/DeviceProfiles.kt`.

## How the shots were made

`DeviceWidthScreenshotTest` draws each list as shipped: Songs sorted A–Z, so with its fast
scroller; the fake Videos library from `VideosScreenshotTest`. Each sits in the app's compact
chrome (the mini player and the carousel) on the **blush** backdrop. On blush every row draws a
translucent white fill, so a row's width is visible.

- **Before:** `main` at `39fa00e`. The test file (and `DeviceProfiles.kt`, nothing else) was
  copied into a temporary `git worktree` of `39fa00e` and recorded there. The worktree was
  then removed.
- **After:** this branch at `4fd5861`, with the fix.

The composites are in `devices/<profile>_before.png` and `devices/<profile>_after.png`. Songs
is on the left and Videos on the right, at full resolution, under a label strip.

**How the margins were read.** A Pillow script (kept out of the repo) worked on the original
renders, not the composites.
- It found each row on a vertical scan at x = 12 dp, which is inside every row's fill and left
  of its art.
- It kept only rows at the list's regular pitch. That drops the mini player and the carousel.
- It read each row's fill edges at the row's middle, where the fill's sides are straight. An
  edge pixel's coverage gives the sub-pixel position.
- It found the ⋮ as three equal dots 6 dp apart, near the row's end.

Every visible row was measured: 4 to 9 per shot. In every shot all rows read the same value.

Layout snaps each padding to whole pixels. So at 2.8125, 8 dp (22.5 px) draws as 23 px, which
reads as 8.18 dp, and 4 dp (11.25 px) draws as 11 px, which reads as 3.91 dp.

## Row margins (dp from the screen's edges to the row's fill)

| Profile | Before, Songs (left / right) | Before, Videos | After, Songs | After, Videos | ⋮ centre to the right edge (Songs before → both after) |
|---|---|---|---|---|---|
| `s24ultra` | 8.18 / **48.36** (8 rows) | 8.18 / 8.18 (7) | 3.91 / 3.91 (8) | 3.91 / 3.91 (7) | 76.09 → 27.73 |
| `s24ultra_small` | 8.00 / **48.00** (9) | 8.00 / 8.00 (8) | 4.19 / 4.19 (9) | 4.19 / 4.19 (8) | 76.00 → 28.00 |
| `s25` | 8.00 / **48.00** (7) | 8.00 / 8.00 (7) | 4.00 / 4.00 (7) | 4.00 / 4.00 (7) | 76.00 → 28.00 |
| `s25ultra` | 8.18 / **48.36** (8) | 8.18 / 8.18 (7) | 3.91 / 3.91 (8) | 3.91 / 3.91 (7) | 76.09 → 27.73 |
| `narrow` | 8.00 / **48.00** (5) | 8.00 / 8.00 (5) | 4.00 / 4.00 (5) | 4.00 / 4.00 (4) | 76.00 → 28.00 |
| `wide412` | 8.00 / **48.00** (9) | 8.00 / 8.00 (8) | 4.16 / 4.16 (9) | 4.16 / 4.16 (9) | 76.00 → 28.00 |

Before, video rows had no ⋮.

- **Before:** at every size, Songs rows ended 40 dp further from the right edge than from the
  left (8 / 48 dp), while Videos rows were even (8 / 8 dp).
- **After:** Songs and Videos rows are the same, at every size: 4 dp from each edge (the rows'
  fill inset). The content sits 16 dp in on both sides: the art's start, and the ⋮ icon's end,
  12 dp past its 28 dp centre.

## Root cause and fix

**Root cause.** The Songs list always takes the fast-scroll branch of `SongColumn`:
`LibraryScreen.kt:255` passes `sorted = songs`, and `:406` forwards it as `fastScroll`. That
branch pads the list's end by `SongScrollerGutter` (`LibraryScreen.kt:480`), defined at `:519` as
`FastScrollTouchWidth - 8.dp`, which is 40 dp. The gutter kept the fast-scroll thumb off a row's
heart and ⋮. With the rows' own 8 dp inset (`TrackRow`, `LibraryScreen.kt:677`), Songs rows
spanned 8 dp to 48 dp from the edges. The Videos list has no such gutter (`VideosScreen.kt:410`;
rows inset 8 dp each side at `:519`).

There is no width cap or breakpoint in play. The only breakpoint is the 600 dp two-pane one
(`MusicApp.kt:108`), and every profile here is below it.

**This branch before the fix.** The list-row restyle had kept the gutter, as
`ListScrollerGutter` (48 dp), and given it to Videos too.

**The fix** (`4fd5861`). Neither list leaves the gutter any more, so rows keep the full width with
even margins.
- As on Videos before, the fast-scroll thumb shows over the row ends while it is up: during a
  scroll or drag and for 2 s after.
- A row's heart always ends left of it, and once it fades nothing covers a ⋮.
- `RowWidthUiTest` checks at all six sizes that Songs and Videos rows share their bounds with
  even margins. On this branch before the fix it failed at every size: for example 11 px on
  the left against 146 px on the right at the S24 Ultra, which is 4 dp against the 48 dp gutter
  plus 4 dp. It passes after the fix.
