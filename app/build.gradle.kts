plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.shibi.musicplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shibi.mediaplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        // The licences screen names the exact libVLC build; the catalog is the single source.
        resValue("string", "libvlc_version", libs.versions.libvlc.get())
    }

    // libVLC ships about 23 MB of native code per ABI. One APK per ABI keeps the
    // Fold 7 download near 50 MB; x86_64 is for emulators only.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    // Compressed .so files: the APK is sideloaded, so download size beats the
    // extracted size on install. The 16 KB check in CI reads the ELF LOAD alignment.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // Debug-only, NOT secret, never used for release or the Play Store. Committed so every CI run
    // and local build signs debug APKs with the same certificate; a fresh ~/.android/debug.keystore
    // per CI runner made each APK's signature differ ("App not installed" on update).
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Upload key for Play. Values come from ~/.gradle/gradle.properties or env vars, never the repo.
        // Only configured when the store file is set, so CI without the key still builds.
        val uploadStoreFile = providers.gradleProperty("MEDIAPLAYER_UPLOAD_STORE_FILE")
            .orElse(providers.environmentVariable("MEDIAPLAYER_UPLOAD_STORE_FILE"))
            .orNull
        if (uploadStoreFile != null) {
            create("release") {
                storeFile = file(uploadStoreFile)
                storePassword = providers.gradleProperty("MEDIAPLAYER_UPLOAD_STORE_PASSWORD")
                    .orElse(providers.environmentVariable("MEDIAPLAYER_UPLOAD_STORE_PASSWORD")).orNull
                keyAlias = providers.gradleProperty("MEDIAPLAYER_UPLOAD_KEY_ALIAS")
                    .orElse(providers.environmentVariable("MEDIAPLAYER_UPLOAD_KEY_ALIAS")).orNull
                keyPassword = providers.gradleProperty("MEDIAPLAYER_UPLOAD_KEY_PASSWORD")
                    .orElse(providers.environmentVariable("MEDIAPLAYER_UPLOAD_KEY_PASSWORD")).orNull
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isDebuggable = false
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        resValues = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // 780+ Robolectric/Roborazzi tests share one worker; the 512 MB default ran out (OOM in VideosScreenshotTest).
                it.maxHeapSize = "2g"
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.coil.compose)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.libvlc.all)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
