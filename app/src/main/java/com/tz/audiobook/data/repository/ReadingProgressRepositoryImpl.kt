package com.tz.audiobook.data.repository

import com.tz.audiobook.data.local.database.ReadingProgressDao
import com.tz.audiobook.data.mapper.toDomain
import com.tz.audiobook.data.mapper.toEntity
import com.tz.audiobook.domain.model.ReadingProgress
import com.tz.audiobook.domain.repository.ReadingProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingProgressRepositoryImpl @Inject constructor(
    private val readingProgressDao: ReadingProgressDao
) : ReadingProgressRepository {

    override fun getProgress(bookId: Long): Flow<ReadingProgress?> {
        return readingProgressDao.getProgress(bookId).map { it?.toDomain() }
    }

    override fun getAllProgress(): Flow<List<ReadingProgress>> {
        return readingProgressDao.getAllProgress().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveProgress(progress: ReadingProgress) {
        readingProgressDao.saveProgress(progress.toEntity())
    }

    override suspend fun getAllProgressSnapshot(): List<ReadingProgress> {
        return readingProgressDao.getAllProgressList().map { it.toDomain() }
    }

    override suspend fun saveAllProgress(progressList: List<ReadingProgress>) {
        readingProgressDao.saveAllProgress(progressList.map { it.toEntity() })
    }
}