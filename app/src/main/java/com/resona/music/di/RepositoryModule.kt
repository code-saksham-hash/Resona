package com.resona.music.di

import com.resona.music.data.github.GitHubContributorsRepository
import com.resona.music.data.repository.MusicRepositoryImpl
import com.resona.music.data.update.GitHubAppUpdateRepository
import com.resona.music.domain.repository.AppUpdateRepository
import com.resona.music.domain.repository.ContributorsRepository
import com.resona.music.domain.repository.MusicRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMusicRepository(impl: MusicRepositoryImpl): MusicRepository

    @Binds
    @Singleton
    abstract fun bindAppUpdateRepository(impl: GitHubAppUpdateRepository): AppUpdateRepository

    @Binds
    @Singleton
    abstract fun bindContributorsRepository(impl: GitHubContributorsRepository): ContributorsRepository
}
