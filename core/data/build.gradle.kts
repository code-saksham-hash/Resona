plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.resona.music.core.data"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    // The InnerTube <-> domain mappers return domain types (Song, MusicRepository),
    // so consumers of :core:data need :core:domain on their own compile classpath too.
    api(project(":core:domain"))

    // Hilt: MusicRepositoryImpl and InnerTubeApi both use @Inject constructor,
    // which needs the annotation processor to run in this module to generate
    // their factories -- see CONTRIBUTING.md on why this module has Hilt at
    // all despite ":app is the only module that wires bindings".
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Networking (InnerTube client)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}
