package com.tz.audiobook.parser

object ChapterDetector {
    private val CHAPTER_PATTERNS = listOf(
        Regex("^第[一二三四五六七八九十百千万零\\d]+[章节回卷部篇].*$", RegexOption.MULTILINE),
        Regex("^第[0-9]+[章节回卷部篇].*$", RegexOption.MULTILINE),
        Regex("^[零一二三四五六七八九十百千万]+[章节回卷部篇].*$", RegexOption.MULTILINE),
        Regex("^Chapter\\s*\\d+.*$", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)),
        Regex("^CHAPTER\\s*\\d+.*$", RegexOption.MULTILINE),
        Regex("^Prologue.*$", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)),
        Regex("^Epilogue.*$", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)),
        Regex("^\\d+[\\.、].+$", RegexOption.MULTILINE),
        Regex("^卷[一二三四五六七八九十]+.*$", RegexOption.MULTILINE)
    )

    fun detectChapters(text: String): List<Pair<Int, String>> {
        val chapters = mutableListOf<Pair<Int, String>>()

        for (pattern in CHAPTER_PATTERNS) {
            val matches = pattern.findAll(text)
            matches.forEach { match ->
                val title = text.substring(match.range).trim()
                if (title.isNotBlank()) {
                    chapters.add(match.range.first to title)
                }
            }
        }

        return chapters.sortedBy { it.first }.distinctBy { it.first }
    }

    fun splitByChapters(text: String): List<ChapterContent> {
        val chapterPositions = detectChapters(text)

        if (chapterPositions.isEmpty()) {
            return listOf(ChapterContent(0, "正文", text.trim()))
        }

        val chapters = mutableListOf<ChapterContent>()
        val usedPositions = mutableSetOf<Int>()

        for (i in chapterPositions.indices) {
            val startPos = chapterPositions[i].first
            if (startPos in usedPositions) continue

            val endPos = if (i < chapterPositions.size - 1) {
                val nextPos = chapterPositions[i + 1].first
                if (nextPos in usedPositions) {
                    chapterPositions.drop(i + 1).firstOrNull { it.first !in usedPositions }?.first ?: text.length
                } else nextPos
            } else {
                text.length
            }

            usedPositions.add(startPos)

            val title = chapterPositions[i].second.trim()
            val content = text.substring(startPos, endPos).trim()

            if (content.isNotBlank()) {
                chapters.add(ChapterContent(chapters.size, title, content))
            }
        }

        return if (chapters.isEmpty()) {
            listOf(ChapterContent(0, "正文", text.trim()))
        } else chapters
    }
}