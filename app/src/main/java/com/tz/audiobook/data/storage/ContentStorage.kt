package com.tz.audiobook.data.storage

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ContentStorage"
        private const val CONTENT_DIR = "chapter_content"
    }

    private val contentDir = File(context.filesDir, CONTENT_DIR).apply { mkdirs() }

    fun getBookContentDir(bookId: Long): File {
        val dir = File(contentDir, "book_$bookId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun saveChapterContent(bookId: Long, chapterIndex: Int, content: String): String {
        val dir = getBookContentDir(bookId)
        val file = File(dir, "chapter_$chapterIndex.txt")
        file.writeText(content)
        Log.d(TAG, "Saved chapter content: ${file.absolutePath}, size: ${content.length}")
        return file.absolutePath
    }

    fun loadChapterContent(contentPath: String): String {
        val file = File(contentPath)
        return if (file.exists()) {
            file.readText()
        } else {
            Log.w(TAG, "Content file not found: $contentPath")
            ""
        }
    }

    fun deleteBookContent(bookId: Long) {
        val dir = getBookContentDir(bookId)
        if (dir.exists()) {
            dir.deleteRecursively()
            Log.d(TAG, "Deleted content for book $bookId")
        }
    }
}
