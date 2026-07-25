plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.resona.music.feature.player"
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

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Domain interfaces only -- never :core:data directly.
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
    // NowPlayingScreen takes PlayerUiState directly as a parameter, and
    // MiniPlayerBar/NowPlayingScreen are both stateless (no hiltViewModel()
    // calls of their own -- :app owns and passes down the shared
    // PlayerViewModel), so no Hilt dependency is needed in this module.
    implementation(project(":core:player"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)

    // Image loading (album art / thumbnails)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.ui.tooling)
}
