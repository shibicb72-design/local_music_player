# AGENTS.md - DeepSeek playbook

## 1. Project map
- Single module `:app`, package `com.shibi.musicplayer` (minSdk 26, targetSdk 36). Kotlin + Jetpack Compose (Material 3) + Media3.
- `MainActivity.kt`: entry point, `setContent { MusicPlayerTheme { MusicApp() } }`.
- `domain/`: pure Kotlin logic (`PlaybackQueue`, `PlaybackTime`, `Track`), unit-tested.
- `library/`: `MusicRepository` (MediaStore) and `LibraryViewModel`.
- `playback/`: `PlaybackService` (MediaSessionService), `PlayerViewModel`, `PlayerUiState`, `TrackMediaItems`, `OutputSwitcher` (audio output picker).
- `ui/`: `MusicApp` (navigation), `LibraryScreen`, `PlayerScreen`, `AlbumArtwork`, `AudioPermission`, `theme/Theme.kt`.
- Tests: `app/src/test/java/com/shibi/musicplayer/` (plain JUnit 4, no Robolectric).
- Libraries (gradle/libs.versions.toml): AGP 9.4.0, Kotlin 2.4.10, core-ktx 1.18.0, appcompat 1.7.1, activity-compose 1.13.0, lifecycle 2.10.0, Compose BOM 2026.06.01, navigation 2.9.8, media3 1.11.1, mediarouter 1.8.1, coil 3.5.0, coroutines 1.11.0, junit 4.13.2.
- Style: 4-space indent, trailing commas, one composable per concern, private helper composables, user-facing text in `res/values/strings.xml` via `stringResource`, dependencies only through the version catalog (`libs.*`).

## 2. Definition of done
- It compiles and `./gradlew assembleDebug test` passes (run it locally if `java -version` works and ANDROID_HOME is set; otherwise CI is the compiler, so check every import, dependency and type by hand).
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

## 6. Lessons learned
- androidx.mediarouter's `MediaRouteChooserDialog` extends `androidx.appcompat.app.AppCompatDialog`, and mediarouter does not expose appcompat at compile time. Using mediarouter dialog classes requires `implementation(libs.androidx.appcompat)` in app/build.gradle.kts (catalog alias `androidx-appcompat`).
- No JDK on the laptop yet: you cannot run Gradle locally, so do not claim a build passed.
