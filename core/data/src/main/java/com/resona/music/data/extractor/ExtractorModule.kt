package com.resona.music.data.extractor

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Binds JsEngine to WebViewJsEngine. Lives here instead of :app's Hilt
// modules since JsEngine isn't a domain concept -- just an internal detail
// of how this module resolves stream URLs. See ARCHITECTURE.md.
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ExtractorModule {

    @Binds
    internal abstract fun bindJsEngine(impl: WebViewJsEngine): JsEngine
}
