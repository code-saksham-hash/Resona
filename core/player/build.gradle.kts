plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.resona.music.core.player"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // PlayerViewModel.play(song: Song) and PlayerUiState.currentTrack expose
    // Song directly, so consumers of :core:player need :core:domain too.
    api(project(":core:domain"))

    // Hilt: PlayerViewModel is @HiltViewModel, needing the processor here to
    // generate its factory (see :core:data's build.gradle.kts for why this
    // doesn't conflict with ":app wires bindings").
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Lifecycle (ViewModel + viewModelScope) -- no Compose dependency here,
    // PlayerService and PlayerViewModel are both plain (non-UI) Android/Kotlin.
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Playback (ExoPlayer + MediaSession foreground service)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    // Image URI handling in MediaMetadata (Song.thumbnailUrl -> Uri)
    implementation(libs.androidx.core.ktx)
}
