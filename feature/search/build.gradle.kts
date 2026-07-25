plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.resona.music.feature.search"
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
    // For triggering playback (SearchScreen's onSongClick callback is wired
    // to PlayerViewModel::play by :app, but this module is allowed to see
    // :core:player's types directly, unlike :core:data's).
    implementation(project(":core:player"))

    // Hilt: SearchViewModel is @HiltViewModel
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)

    // Lifecycle-aware Compose state collection (collectAsStateWithLifecycle)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Image loading (search result thumbnails)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.ui.tooling)
}
