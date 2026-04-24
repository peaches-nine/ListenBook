package com.tz.listenbook.di

import com.tz.listenbook.data.repository.BookRepositoryImpl
import com.tz.listenbook.data.repository.ReadingProgressRepositoryImpl
import com.tz.listenbook.domain.repository.BookRepository
import com.tz.listenbook.domain.repository.ChapterRepository
import com.tz.listenbook.domain.repository.ReadingProgressRepository
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
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    @Singleton
    abstract fun bindChapterRepository(impl: BookRepositoryImpl): ChapterRepository

    @Binds
    @Singleton
    abstract fun bindReadingProgressRepository(impl: ReadingProgressRepositoryImpl): ReadingProgressRepository
}