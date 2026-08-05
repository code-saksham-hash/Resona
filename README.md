# Resona

Open-source, anonymous Android music streaming app. Plays audio from YouTube Music by talking to its InnerTube API directly; no YouTube account, no OAuth, no ads.

Resona is built around a strict module boundary: a pure-Kotlin domain layer, a data layer that reverse-engineers YouTube Music's streaming pipeline, and a monochrome Compose UI that never touches networking code.

---

## Features

| Area | Status | Notes |
|---|---|---|
| Search | Complete | Debounced input, InnerTube results filtered to songs, tap-to-play, loading/empty/error states |
| Home feed | Complete | Pull-to-refresh, curated sections (recommended/trending/new), artist spotlight carousel, quick-pick genre chips |
| Now Playing | Complete | High-resolution album art, drag seekbar, transport controls, like/download, queue and lyrics panels, overflow menu |
| Mini Player | Complete | Pill-shaped bar above the bottom nav, tinted by the playing track's album colors, with marquee title and transport controls |
| Library | Complete | Liked songs, downloaded songs, featured playlists, pull-to-refresh |
| Downloads | Complete | Offline audio saved to app storage with atomic temp-file writes |
| Likes | Complete | JSON-persisted, consistent across every screen |
| Play history | Complete | Capped at 30 entries; feeds the Home "Recommended For You" section |
| Lyrics | Complete | Timed (karaoke-style) lyrics from LRCLIB with InnerTube plain-text fallback |
| Queue & skip | Complete | Queue-aware next/previous on both the mini player and Now Playing screen |
| Artist / Playlist detail | Complete | Artist pages from Home cards; playlist pages from Library |
| Stats / History tabs | Placeholder | UI present with sample data; not yet wired to real metrics |

## How it works

Resona does not use the official YouTube API. Instead it:

1. **Talks to InnerTube** — the private JSON API the YouTube Music web client uses (`youtubei/v1/search`, `/player`, `/browse`, `/next`).
2. **Falls back across six client identities** (`ANDROID_VR`, `ANDROID`, `TVHTML5_SIMPLY_EMBEDDED_PLAYER`, `IOS`, `MWEB`, `WEB`) because anonymous access is progressively gated — no single client works reliably.
3. **Deciphers protected streams** — signature (`s`) and CDN throttling (`n`) parameters are transformed by running YouTube's own player JavaScript in an embedded WebView, so no native JS engine is bundled (the pipeline is ported from yt-dlp-android; see `NOTICE.md`).
4. **Stays anonymous** — a single visitor token, no login, no cookies. All local state (likes, downloads, history) lives on-device as JSON.

## Tech stack

| Category | Choice |
|---|---|
| Language | Kotlin 2.0.21 (100% Kotlin) |
| UI | Jetpack Compose (Material 3) — strict monochrome design system |
| DI | Hilt 2.52 |
| Networking | Ktor client (OkHttp engine) + kotlinx.serialization |
| Playback | Media3 / ExoPlayer foreground service + MediaSession |
| Images | Coil |
| Build | Gradle 8.9 (Kotlin DSL), AGP 8.5.2, min SDK 26 / target 34 |

## Architecture

Nine Gradle modules enforce the boundaries:

- `:core:domain` — pure Kotlin/JVM, zero Android dependencies. Domain models and the `MusicRepository` interface.
- `:core:data` — InnerTube networking, stream extraction/deciphering, downloads, likes, and history stores.
- `:core:player` — ExoPlayer foreground service, shared `PlayerViewModel`, playback state.
- `:core:ui` — the monochrome design system (theme, typography, shapes, shared composables).
- `:feature:home`, `:feature:search`, `:feature:player`, `:feature:library` — screen modules. They only see the `MusicRepository` interface — never networking or implementation classes.
- `:app` — the thin shell that wires everything together (navigation, Hilt bindings).

Feature modules cannot depend on `:core:data`, and only `:app` maps domain interfaces to implementations. See `ARCHITECTURE.md` for the full module graph and dependency rules.

## Getting started

```bash
./gradlew assembleDebug   # build the app
./gradlew testDebugUnitTest  # run unit tests (MockEngine, no network needed)
```

Open the resulting APK in `app/build/outputs/apk/debug/` on an Android 8.0+ device.

## Design philosophy

Resona's UI is deliberately minimal: a fixed black/white/gray palette with no dynamic color, weight-driven typography, and flat controls. Recent screens add subtle glass and gradient styling, but the core tokens remain strictly monochrome.

## Disclaimer

Resona interfaces with YouTube Music's private InnerTube API, which is undocumented and subject to change at any time. The app is for personal, non-commercial use. YouTube is a trademark of Google LLC; this project is not affiliated with or endorsed by Google or YouTube Music.

## License

[MIT](LICENSE) © 2026 Code.Saksham. Portions derived from yt-dlp-android are attributed in [NOTICE.md](NOTICE.md).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for where to start — including the rule that UI work lives in `:feature:*` modules and backend work in `:core:data`.
