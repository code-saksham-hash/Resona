# Contributing to Resona

See `ARCHITECTURE.md` for the full module graph and the reasoning behind it.
This file is the short, practical version for day-to-day work.

## Where frontend work happens

If you're building or changing UI, you should only need to work inside:

- **`feature:*`** (`feature:home`, `feature:search`, `feature:player`,
  `feature:library`) -- screens and their ViewModels.
- **`core:ui`** -- the shared design system: theme, colors, typography,
  shapes, and shared composables like `ResonaFilledButton` and
  `ResonaPlaceholderScreenContent`. Add something here when a piece of UI is
  reused across more than one feature.

You'll consume `:core:domain` types (`Song`, `MusicRepository`) and, in
`feature:search`/`feature:player`, `:core:player` types (`PlayerViewModel`,
`PlayerUiState`) -- but as interfaces/data classes to build against, not
things you need to implement or modify.

**If a task seems to require changing something inside `core:data` or
`core:player`, stop and flag it** -- that's backend work (networking,
parsing, the database, the playback engine itself), and it's kept separate
on purpose so frontend changes can't accidentally break it. This includes:

- Changing what a repository method returns or how it fetches data
- Touching `InnerTubeApi`, the InnerTube response models, or `MusicRepositoryImpl`
- Changing `PlayerService` or the playback/`MediaSession` internals inside
  `PlayerViewModel`
- Adding a new Hilt `@Module`/`@Provides`/`@Binds` (only `:app` does this --
  see `ARCHITECTURE.md`'s note on why `core:data`/`core:player` still have
  Hilt's annotation processor even though they don't "wire" anything)

## Adding a new screen

1. New screen, new feature area? Create a new `:feature:<name>` module
   (copy an existing one's `build.gradle.kts` as a starting point, add it to
   `settings.gradle.kts`).
2. The screen composable should be **stateless where practical**: take a
   `Song`/`PlayerUiState`/etc. and callback lambdas as parameters, rather
   than reaching for `hiltViewModel()` itself, *unless* the ViewModel is
   genuinely scoped to that one screen (like `SearchViewModel`). Shared state
   that spans screens (like playback) is owned once in `:app`'s `NavGraph`
   and passed down -- see how `MiniPlayerBar` and `NowPlayingScreen` receive
   `PlayerUiState` and callbacks rather than looking up `PlayerViewModel`
   themselves.
3. Wire the new screen into `:app`'s `NavGraph.kt` and `Destinations.kt`.
   This is the one place feature modules get connected to each other and to
   navigation -- it's expected to be the only part of the change outside your
   new feature module (plus `settings.gradle.kts` and `:app`'s
   `build.gradle.kts` to add the module dependency).

## Design system rule

Every color used anywhere in the app must come from `core:ui`'s
`Color.kt` palette (black/white/gray only -- no other hues, no Material You
dynamic color, no gradients or shadows), with one exception: `ColorScheme.tertiary`/`onTertiary`,
used for active/selected state (the selected bottom-nav pill, the top bar's
voice-search button, the Search screen's accents). `ResonaNavGraph` overrides
those two roles once, at the top of the app, with whatever's playing's
album-art color (falling back to plain white/black when nothing is) -- see
`AlbumArtPalette.kt` in `:feature:player`. Don't hardcode a color for that
role anywhere else; read `MaterialTheme.colorScheme.tertiary` and it comes
along for free. If a screen needs something that isn't expressible with what's already in
`core:ui`, that's a sign the design system needs a new shared piece, not a
one-off in a feature module.

## Building and testing

```
./gradlew assembleDebug        # build the app
./gradlew testDebugUnitTest    # run unit tests across every module
```

Unit tests live next to the code they test in whichever module owns it
(e.g. `MusicRepositoryImplTest` lives in `:core:data`, since that's where
`MusicRepositoryImpl` lives).
