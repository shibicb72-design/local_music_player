# AGENTS.md - Theatrum (local_music_player)

Primary tool: Claude Code (config in `.claude/`). The free OpenCode models are handled by Marcus, not by this file.
Past mistakes, API gotchas and design history: `docs/lessons.md` (moved there verbatim; read only the part your ticket touches).

## Ground rules
- **Phone:** never `adb install`, uninstall, or clear/change app data on Shibi's phone or devices. Install only after Shibi's explicit yes for that specific install. When a build is ready, stop and report the CI APK artifact. Read-only adb (logcat, dumpsys) only when truly needed; prefer the app's Debugging / Error log.
- **PRs:** one branch per ticket (`opus/<topic>`), based on `main` or on the branch it stacks on; say which in the PR. The PR body starts with "Do not merge without Shibi's OK." Never merge, never push to `main`, never force-push.
- **Work in progress:** check `git status` first. If the tree is dirty or on a feature branch with local commits, use `git worktree add` instead of switching branches.
- Never kill `claude.exe` or `opencode.exe`. Never commit secrets.
- **Debug keystore:** `app/debug.keystore` (from `opus/fix-debug-signing`, PR #25) is a DEBUG-ONLY, NON-SECRET key (android / androiddebugkey), committed so every CI APK has the same signature and updates in place. Never point a release signingConfig at it. CI versionCode = `GITHUB_RUN_NUMBER`.

## Project map
- Single module `:app`, package `com.shibi.musicplayer` (minSdk 26, targetSdk 36). Kotlin, Compose Material 3, Media3, Room, Coil 3, libVLC.
- `domain/` pure Kotlin, unit-tested, no Android imports. `library/` MediaStore repositories and view models. `playlist/` Room (`playlists.db`, migrations tested). `playback/` `PlaybackService` + `PlayerViewModel`. `ui/` `MusicApp` (routes in `SectionRoutes`), `SectionScaffold`, `NavCarousel`, screens, `theme/`.
- Tests: `app/src/test/java/com/shibi/musicplayer/` (JUnit 4; Robolectric for Room and Roborazzi). Screenshots are written to `app/build/outputs/roborazzi/` by `recordRoborazziDebug` only.
- Dependencies only through `gradle/libs.versions.toml` (`libs.*`). User-facing text in `res/values/strings.xml`.
- Style: 4-space indent, trailing commas, one composable per concern, private helper composables.

## Build and test (always via the wrapper)
```
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gradle-brief.ps1 assembleDebug testDebugUnitTest
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gradle-brief.ps1 recordRoborazziDebug
```
- Prints `BUILD OK` plus up to 5 `w:` lines, or the first failure lines with context. Full log: `%TEMP%\gradle-last.log` (Grep it; never read it whole). Exit code = Gradle's.
- Zero-warning bar: after UI/API changes run `clean` first, then the build, and check for `w:` lines.
- CI ("Android CI") runs `assembleDebug test` and `recordRoborazziDebug` on every PR.

## Definition of done
- Tickets arrive as Goal / Files to touch / Acceptance criteria. Do exactly that.
- Build and unit tests green locally; report the actual result. No new `w:` lines.
- Only the files the ticket names. Logic changes come with tests, written first.
- After each ticket, append one line to `docs/lessons.md`: `- [task] what went wrong -> new rule`.

## Design rules
- No dark-on-dark text: every painted surface takes its content colour from its own container (`ContrastSurface` / `rolesForContainer`), never from the window. Transparent containers pass `contentColor` explicitly.
- Works folded and unfolded: compact and two-pane layouts both work; check both in screenshots.
- No visible volume sliders.
- The 3D carousel nav (`NavCarousel`) stays, on every compact route. The mini player and carousel live in `SectionScaffold`'s chrome slot, never overlaid on the list.
- Animated values are read only in layout, draw or `graphicsLayer` lambdas. Animator scale 0 (reduce motion) snaps.

## Delegation (token budget)
- Builds and tests -> `gradle-runner` (Haiku).
- Screenshot review -> `screenshot-reviewer` (Haiku, failing `*_compare.png` only).
- Code search -> `Explore` (Haiku, read-only).
- Planned implementation -> `android-implementer` (Sonnet). Planning stays in the main conversation (`opusplan`).
- The main conversation never reads PNGs, full Gradle logs or files under `build/`.
- `/clear` between tickets. How to measure: `docs/token-budget.md`.
