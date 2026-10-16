# Agent notes

Kotlin + Jetpack Compose + Media3 Android app (com.shibi.musicplayer, minSdk 26, targetSdk 36). CI runs `./gradlew assembleDebug test`. There is no local JDK on the dev laptop, so CI is the only compiler: double-check imports, dependencies and types before finishing.

## Lessons learned

- androidx.mediarouter's `MediaRouteChooserDialog` extends `androidx.appcompat.app.AppCompatDialog`, and mediarouter does not expose appcompat on the compile classpath. Any code that uses mediarouter dialog classes needs `implementation(libs.androidx.appcompat)` in app/build.gradle.kts (catalog alias `androidx-appcompat`).
