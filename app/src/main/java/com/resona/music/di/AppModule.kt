package com.resona.music.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * App-wide Hilt bindings live here. Empty for now — this only proves the
 * Hilt + KAPT wiring compiles end to end. Add @Provides/@Binds methods as
 * the data/ and domain/ layers grow (e.g. Retrofit, Room, repositories).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
