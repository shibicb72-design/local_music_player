# AGENTS.md - DeepSeek playbook

## 1. Project map
- Single module `:app`, package `com.shibi.musicplayer` (minSdk 26, targetSdk 36). Kotlin + Jetpack Compose (Material 3) + Media3.
- `MainActivity.kt`: entry point, `setContent { MusicPlayerTheme { MusicApp() } }`.
- `domain/`: pure Kotlin logic (`PlaybackQueue`, `PlaybackTime`, `Track`, `AudioOutputs`), unit-tested.
- `library/`: `MusicRepository` (MediaStore) and `LibraryViewModel`.
- `playback/`: `PlaybackService` (MediaSessionService), `PlayerViewModel`, `PlayerUiState`, `TrackMediaItems`, `OutputCommands`.
- `ui/`: `MusicApp` (navigation), `LibraryScreen`, `PlayerScreen`, `AlbumArtwork`, `AudioPermission`, `OutputPickerSheet` (audio output picker), `theme/Theme.kt`.
- Tests: `app/src/test/java/com/shibi/musicplayer/` (plain JUnit 4, no Robolectric).
- Libraries (gradle/libs.versions.toml): AGP 9.4.0, Kotlin 2.4.10, core-ktx 1.18.0, activity-compose 1.13.0, lifecycle 2.10.0, Compose BOM 2026.06.01, navigation 2.9.8, media3 1.11.1, coil 3.5.0, coroutines 1.11.0, junit 4.13.2.
- Style: 4-space indent, trailing commas, one composable per concern, private helper composables, user-facing text in `res/values/strings.xml` via `stringResource`, dependencies only through the version catalog (`libs.*`).

## 2. Definition of done
- Run `gradlew.bat assembleDebug test` locally (JDK 17 and Android SDK are installed; if JAVA_HOME/ANDROID_HOME are missing in your shell, load them from the user environment). It must be green before you say you are done. Report the actual result.
- No new compiler warnings.
- Only the files named in the ticket are touched.
- Logic changes come with new or updated unit tests.

## 3. Ticket format
Every task arrives as: **Goal**, **Files to touch**, **Acceptance criteria**. Do exactly that; ask nothing, invent nothing extra.

## 4. Test-first
For logic changes, write or update the failing unit test first, then the code. Keep Android framework calls out of pure logic so it stays JUnit-testable.

## 5. Retro
After every task, append an entry below: `- [task] what went wrong -> new rule`.

- [output-device button] Used `MediaRouteChooserDialog` without appcompat on the classpath; CI failed to compile -> check the supertypes of every library class you use are on the classpath.

- [ticket A, ticket B] Tried to unzip media3 jars from the Gradle cache to check APIs; permission denied ended the run with no code written -> use the compiler, not the cache, to confirm APIs.

- [ticket B] Was unsure whether `AudioRecordingCallback.onRecordingConfigChanged` takes `List` or `MutableList`; guessed `MutableList` for the platform-type override and it compiled on the first try -> for platform-type overrides, pick the idiomatic Kotlin type and let the compiler confirm, never inspect SDK caches.

- [ticket D2] Used the one-arg `AcceptedResultBuilder(MediaSession)`, which emitted a new deprecation warning and would have failed the "no new warnings" bar -> build `AcceptedResultBuilder(session, controller)` to start from per-controller defaults; after any media3 API call, re-run the build and grep the compiler output for `w:`.

- [ticket D3] Assumed the callback was the nested `AudioManager.AudioDeviceCallback`, but compileSdk 36 exposes `android.media.AudioDeviceCallback` as a top-level class and the first build failed -> resolve the exact FQN of any framework class with `javap -classpath $ANDROID_HOME/platforms/<sdk>/android.jar` before referencing it.

- [ticket D3] Wrote `AudioManager.AudioDeviceCallback`; first local build failed -> `AudioDeviceCallback` is top-level `android.media.AudioDeviceCallback`.

- [ticket D4] Removed the mediarouter/appcompat catalog entries only after `rg` confirmed no remaining `androidx.mediarouter`/`androidx.appcompat` imports in app/src; `assembleDebug test` stayed green with zero `w:` -> delete a dependency only after an import sweep plus a full build, never on the assumption the last user was the obvious one.

- [ticket F] Redirected build output to $env:TEMP and then could not read it (external directory denied) -> keep build logs inside the repo's build/ folder or read the console output directly.

- [ticket E2] First `assembleDebug test` printed no `w:` because Kotlin compile was partly UP-TO-DATE, so an incremental run cannot prove the warning bar -> after UI/API changes run `clean assembleDebug test` and grep the console for `^w:` before reporting "zero warnings".

- [ticket E3] Wrote `palette.vibrant ?: palette.dominant` straight from the ticket wording; the Android `androidx.palette.graphics.Palette` API exposes the getters `vibrantSwatch`/`dominantSwatch`, so the first clean build failed with "Unresolved reference 'vibrant'" -> when a ticket paraphrases a library API, resolve the real accessor names (`vibrantSwatch`, `dominantSwatch`, `swatch.rgb`) before writing the call.

- [ticket E3] Adding `TopAppBarDefaults.centerAlignedTopAppBarColors(...)` produced the only `w:` line of the clean build ("deprecated. Use topAppBarColors instead") -> in material3 1.4.0 use `TopAppBarDefaults.topAppBarColors(containerColor = ...)` for `CenterAlignedTopAppBar`.

- [ticket E3] The value-based `Slider(value, ..., track = { SliderDefaults.Track(sliderState, modifier, enabled) })` overload is experimental; opted in defensively with `@OptIn(ExperimentalMaterial3Api::class)` on the helper and it compiled first try with no redundant-`@OptIn` warning -> Kotlin does not warn on a redundant `@OptIn`, so opting in up-front is a safe one-build strategy.

- [ticket E4] The ticket said to apply the new `modifier` parameter to each screen's `Scaffold`, but `PlayerScreen` wraps its `Scaffold` in a `Box`; a `RowScope.weight` applied to the inner `Scaffold` (a child of `Box`, not the `Row`) would be ignored -> apply layout-role modifiers like `weight` to the screen's outermost composable (`Box` for `PlayerScreen`, `Scaffold` for `LibraryScreen`), not an inner container.

- [ticket E4] `rg` is not on PATH in this PowerShell shell, so the `rg '^w:'` warning sweep failed with CommandNotFound -> use `Select-String -Path build\e4-build.log -Pattern '^w:'` for the zero-warning check.

- [ticket V0] `captureRoboImage(path) { composable }` writes relative to the module dir with the default `relativePathFromCurrentDirectory` strategy, so passing just `list.png` would land in `app/list.png`, not the artifact dir -> pass the full `build/outputs/roborazzi/<name>.png` path so `recordRoborazziDebug` drops the PNGs where `upload-artifact` looks.

- [ticket V0] `captureRoboImage(content)` only rasterizes the composable content root, so a `ModalBottomSheet` (its own window) captures empty; the ticket anticipated this -> extract an `internal OutputPickerContent(outputs, selectedId, onSelect)` and capture that, keeping the sheet thin.

- [ticket V0] `captureRoboImage` is a no-op outside record/compare tasks, so plain `test` stays green without PNGs and `finalizeTestRoborazziDebug` shows SKIPPED -> the acceptance needs the separate `recordRoborazziDebug` run to actually produce and verify the images.

- [ticket V1] `PlayerScreen`'s title/icons rendered black on the dark gradient because `Scaffold(containerColor = Color.Transparent)` derives its contentColor from `contentColorFor(Color.Transparent)`, which is not a theme role and falls back to `LocalContentColor`, whose default is black, and nothing above the app provided a root Surface -> provide a root `Surface(color = background, contentColor = onBackground)` in `MusicPlayerTheme` and pass `contentColor = onBackground` explicitly whenever a Scaffold/container is transparent, plus `titleContentColor`/`navigationIconContentColor` on the transparent TopAppBar.

## 6. Lessons learned
- androidx.mediarouter's `MediaRouteChooserDialog` extends `androidx.appcompat.app.AppCompatDialog`, and mediarouter does not expose appcompat at compile time. Using mediarouter dialog classes requires `implementation(libs.androidx.appcompat)` in app/build.gradle.kts (catalog alias `androidx-appcompat`).
- You cannot read the Gradle cache (~/.gradle/caches): OpenCode denies external directories and the run aborts. Do not try. Verify an API by compiling (gradlew.bat assembleDebug) instead.
- `android.media.AudioDeviceCallback` is a top-level class (not nested in AudioManager). `AudioManager.AudioRecordingCallback` IS nested.
- Only the repo directory is readable/writable for you. Never write or read files in %TEMP% or other outside paths; pipe Gradle output to the console or to build/.
- Scaffold applies insets only to its content padding. Custom composables in bottomBar/topBar must handle navigation/status bar insets themselves (navigationBarsPadding()).
