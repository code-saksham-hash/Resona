plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // MusicRepository exposes Flow (observeDownloadedSongs) -- suspend fun
    // alone needs no coroutines dependency, but Flow does.
    api(libs.kotlinx.coroutines.core)
}
