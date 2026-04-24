package com.tz.audiobook.parser

object ChapterDetector {
    // Chinese numbers including simplified and formal forms
    private val chineseNumbers = "[一二三四五六七八九十百千万零壹贰叁肆伍陆柒捌玖拾佰仟]"

    private val CHAPTER_PATTERNS = listOf(
        // Chinese chapter formats: 第X章/节/回/卷/部/篇
        Regex("^第${chineseNumbers}+[章节回卷部篇].*$", RegexOption.MULTILINE),
        Regex("^第[0-9]+[章节回卷部篇].*$", RegexOption.MULTILINE),
        // Standalone Chinese numbers
        Regex("^${chineseNumbers}+[章节回卷部篇].*$", RegexOption.MULTILINE),
        // Volume format: 卷X
        Regex("^卷${chineseNumbers}+.*$", RegexOption.MULTILINE),
        // English chapter formats
        Regex("^Chapter\\s*\\d+.*$", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)),
        Regex("^CHAPTER\\s*\\d+.*$", RegexOption.MULTILINE),
        // Roman numeral chapters
        Regex("^第[IVXLCDM]+[章节回卷部篇].*$", RegexOption.MULTILINE),
        Regex("^Chapter\\s*[IVXLCDM]+.*$", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)),
        // Prologue/Epilogue and other standalone chapter words
        Regex("^Prologue.*$", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)),
        Regex("^Epilogue.*$", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)),
        Regex("^序章.*$", RegexOption.MULTILINE),
        Regex("^楔子.*$", RegexOption.MULTILINE),
        Regex("^尾声.*$", RegexOption.MULTILINE),
        Regex("^后记.*$", RegexOption.MULTILINE),
        Regex("^终章.*$", RegexOption.MULTILINE),
        // Numbered sections: 1. xxx, 2、xxx (with dot or comma)
        Regex("^\\d+[\\.、].+$", RegexOption.MULTILINE),
        // Number followed by space and title (like "5 周日决定")
        Regex("^\\d+\\s+[^\\s].+$", RegexOption.MULTILINE)
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