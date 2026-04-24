package com.tz.listenbook.parser

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TxtParser @Inject constructor(
    @ApplicationContext private val context: Context
) : BookParser {

    override suspend fun parse(uri: Uri): ParsedBook = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open file: $uri")

        val bytes = inputStream.use { it.readBytes() }
        val text = tryDecodeText(bytes)
        inputStream.close()

        val fileName = getFileName(uri)
        val title = fileName.substringBeforeLast(".")

        val chapters = ChapterDetector.splitByChapters(text)

        ParsedBook(
            title = title,
            author = null,
            chapters = chapters,
            coverPath = null
        )
    }

    private fun tryDecodeText(bytes: ByteArray): String {
        val utf8Text = String(bytes, Charsets.UTF_8)

        if (!hasGarbledCharacters(utf8Text)) {
            return utf8Text
        }

        val encodings = listOf(
            Charset.forName("GBK"),
            Charset.forName("GB18030"),
            Charset.forName("GB2312"),
            Charset.forName("BIG5")
        )

        for (charset in encodings) {
            try {
                val decoded = String(bytes, charset)
                if (!hasGarbledCharacters(decoded)) {
                    return decoded
                }
            } catch (e: Exception) {
                continue
            }
        }

        return utf8Text
    }

    private fun hasGarbledCharacters(text: String): Boolean {
        val garbledCount = text.count { it == '�' || it.code > 0xFFFD }
        val ratio = garbledCount.toDouble() / text.length.coerceAtLeast(1)
        return ratio > 0.01
    }

    private fun getFileName(uri: Uri): String {
        var name = "Unknown"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}