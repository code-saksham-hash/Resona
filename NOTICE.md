# Third-party attribution

`core/data/src/main/java/com/resona/music/data/extractor/` contains code
ported from **yt-dlp-android** (https://github.com/HLahwani/yt-dlp-android),
used under the MIT License:

```
MIT License

Copyright (c) 2026 Hamzeh Lahwani

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

What was ported, specifically:

- `decipher/JsFunctionExtractor.kt` -- copied essentially verbatim.
- `decipher/SignatureDecipherer.kt`, `decipher/NParamDecipherer.kt`,
  `decipher/PlayerJsRepository.kt`, `decipher/DecipherService.kt` -- ported
  and adapted to suspend functions, Resona's own exception types, and Ktor
  instead of OkHttp.
- `InnerTubeClientConfig.kt` -- the client identities (names, versions,
  user agents) and fallback ordering are copied from yt-dlp-android's
  `InnerTubeClientConfig.kt`, which itself tracks yt-dlp's own findings on
  which InnerTube clients currently work anonymously.
- `InnerTubeExtractionClient.kt`, `YouTubeStreamExtractor.kt` -- ported and
  simplified to audio-only (no video/muxed format selection). One deliberate
  deviation: yt-dlp-android only captures/sends a visitor token (`visitorData`)
  for its WEB client. Live testing here showed anonymous requests *without* a
  stable visitor token get throttled to LOGIN_REQUIRED ("Sign in to confirm
  you're not a bot") noticeably faster than ones that reuse the same token --
  so every client in Resona's chain sends it, scraped once per app session
  from the watch page (`PlayerJsRepository.currentVisitorData()`).

**Not used**: yt-dlp-android's embedded native QuickJS engine (a compiled
binary this project has no way to audit at the source level). The JS
execution surface it's used for (running an extracted transform function
against a test string, and -- for the hardest-obfuscated players -- running
the whole player JS with a discovery script) is reimplemented in
`WebViewJsEngine.kt` on top of Android's own WebView instead, which is
already present and trusted on every device. `JsEngine.kt`'s interface and
`WebViewJsEngine.kt`'s implementation are original to this project, not
ported.

yt-dlp-android's QuickJS bundling is itself built on QuickJS
(Copyright (c) 2017-2021 Fabrice Bellard, Charlie Gordon, MIT License) --
noted here for completeness even though this project doesn't bundle it.
