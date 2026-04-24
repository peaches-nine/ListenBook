package com.tz.listenbook.parser

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpubParser @Inject constructor(
    @ApplicationContext private val context: Context
) : BookParser {

    override suspend fun parse(uri: Uri): ParsedBook = withContext(Dispatchers.IO) {
        val zipEntries = readZipEntries(uri)

        val containerXml = zipEntries["META-INF/container.xml"]
            ?: throw IllegalArgumentException("Invalid EPUB: no container.xml")

        val opfPath = parseContainerXml(containerXml)
        val opfContent = zipEntries[opfPath]
            ?: throw IllegalArgumentException("Invalid EPUB: no $opfPath")

        val opfDir = opfPath.substringBeforeLast("/", "")
        val metadata = parseOpfMetadata(opfContent)
        val spine = parseOpfSpine(opfContent)
        val manifest = parseOpfManifest(opfContent)

        val chapters = mutableListOf<ChapterContent>()
        spine.forEachIndexed { index, idref ->
            val href = manifest[idref] ?: return@forEachIndexed
            val chapterPath = if (opfDir.isNotEmpty()) "$opfDir/$href" else href
            val htmlContent = zipEntries[chapterPath] ?: return@forEachIndexed

            val doc = Jsoup.parse(htmlContent)
            val title = doc.select("title").text().takeIf { it.isNotBlank() }
                ?: doc.select("h1,h2,h3").firstOrNull()?.text()
                ?: "第${index + 1}章"
            val textContent = doc.body().text()

            if (textContent.isNotBlank()) {
                chapters.add(ChapterContent(chapters.size, title, textContent))
            }
        }

        if (chapters.isEmpty()) {
            chapters.add(ChapterContent(0, "正文", "内容为空"))
        }

        ParsedBook(
            title = metadata.title ?: getFileName(uri).substringBeforeLast("."),
            author = metadata.author,
            chapters = chapters,
            coverPath = null
        )
    }

    private fun readZipEntries(uri: Uri): Map<String, String> {
        val entries = mutableMapOf<String, String>()
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open file: $uri")

        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val content = zis.bufferedReader(Charsets.UTF_8).readText()
                    entries[entry.name] = content
                }
                entry = zis.nextEntry
            }
        }

        return entries
    }

    private fun parseContainerXml(xml: String): String {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        return doc.select("rootfile").firstOrNull()
            ?.attr("full-path")
            ?: throw IllegalArgumentException("No rootfile in container.xml")
    }

    private data class OpfMetadata(
        val title: String? = null,
        val author: String? = null
    )

    private fun parseOpfMetadata(xml: String): OpfMetadata {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val title = doc.select("metadata title,dc\\:title").text().takeIf { it.isNotBlank() }
        val author = doc.select("metadata creator,dc\\:creator").text().takeIf { it.isNotBlank() }
        return OpfMetadata(title, author)
    }

    private fun parseOpfManifest(xml: String): Map<String, String> {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        return doc.select("manifest item").associate {
            it.attr("id") to it.attr("href")
        }
    }

    private fun parseOpfSpine(xml: String): List<String> {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        return doc.select("spine itemref").mapNotNull {
            it.attr("idref").takeIf { ref -> ref.isNotBlank() }
        }
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