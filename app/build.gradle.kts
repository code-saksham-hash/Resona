plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.resona.music"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.resona.music"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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
    // :app is the thin shell -- it depends on every module so it can wire
    // them together (NavGraph, Hilt bindings), but contains no business
    // logic or feature screens of its own. See ARCHITECTURE.md.
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:player"))
    implementation(project(":core:ui"))
    implementation(project(":feature:home"))
    implementation(project(":feature:search"))
    implementation(project(":feature:player"))
    implementation(project(":feature:library"))

    // Core / lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM keeps all Compose artifacts on matching versions
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    // Hilt -- the only module allowed to declare @Module/@Provides/@Binds
    // (see RepositoryModule/NetworkModule/AppModule). Individual feature/core
    // modules may still use @Inject constructor / @HiltViewModel themselves;
    // that needs the processor locally too, but it's not "wiring" in the
    // sense this rule means -- see CONTRIBUTING.md.
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Lifecycle-aware Compose state collection (collectAsStateWithLifecycle, hiltViewModel)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Networking -- NetworkModule provides the concrete HttpClient here;
    // :core:data only ever sees the already-built instance via injection.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.kotlinx.serialization.json)

    // Testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
