package com.tz.listenbook.parser

data class Sentence(
    val index: Int,
    val text: String,
    val startIndex: Int,
    val endIndex: Int
)

object SentenceSplitter {
    private val sentenceEnders = setOf('。', '！', '？', '；', '…', '!', '?', ';')
    // Note: '.' is handled separately to avoid splitting numbered titles like "1.xxx" or "1. xxx"
    private val quotePairs = listOf('"' to '"', '「' to '」', '『' to '』', '"' to '"')
    private val openQuotes = quotePairs.map { it.first }.toSet()
    private val closeQuotes = quotePairs.map { it.second }.toSet()

    fun split(text: String): List<Sentence> {
        val sentences = mutableListOf<Sentence>()
        var globalIndex = 0

        val lines = text.split("\n")
        var offset = 0

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                offset += line.length + 1
                continue
            }

            val lineStart = offset + line.indexOf(trimmed)
            val lineSentences = splitLine(trimmed)

            for (s in lineSentences) {
                sentences.add(Sentence(
                    index = globalIndex,
                    text = s,
                    startIndex = lineStart,
                    endIndex = lineStart + s.length
                ))
                globalIndex++
            }

            offset += line.length + 1
        }

        return sentences
    }

    private fun splitLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        var inQuote = false
        var quoteChar: Char? = null

        var i = 0
        while (i < line.length) {
            val c = line[i]

            if (c in openQuotes && !inQuote) {
                inQuote = true
                quoteChar = c
                i++
                continue
            }

            if (inQuote && c in closeQuotes) {
                val expectedClose = quotePairs.find { it.first == quoteChar }?.second
                if (expectedClose == c) {
                    inQuote = false
                    quoteChar = null
                }
                i++
                continue
            }

            // Handle '.' specially - don't split if it looks like a numbered title
            // e.g., "1.xxx", "1. xxx", "2.xxx" at the start
            if (!inQuote && c == '.') {
                // Check if this looks like a numbered title: digit(s) followed by '.' at the start
                val prefix = line.substring(0, i)
                if (prefix.matches(Regex("^\\d+$")) && start == 0) {
                    // This is likely a numbered title like "1.xxx", don't split here
                    i++
                    continue
                }
                // Otherwise, treat '.' as sentence ender
                var end = i
                while (end + 1 < line.length && line[end + 1] in sentenceEnders) {
                    end++
                }
                if (end + 1 < line.length && line[end + 1] in closeQuotes && line[end + 1] !in openQuotes) {
                    end++
                }

                val sentence = line.substring(start, end + 1).trim()
                if (sentence.isNotBlank()) {
                    result.add(sentence)
                }
                start = end + 1
            }

            // Only split on sentence enders when NOT inside quotes
            if (!inQuote && c in sentenceEnders) {
                var end = i
                // Consume consecutive ending punctuation
                while (end + 1 < line.length && line[end + 1] in sentenceEnders) {
                    end++
                }
                // Consume trailing close-quotes after punctuation
                // But only if they are NOT also open-quotes (avoid eating start of new dialogue)
                if (end + 1 < line.length && line[end + 1] in closeQuotes && line[end + 1] !in openQuotes) {
                    end++
                }

                val sentence = line.substring(start, end + 1).trim()
                if (sentence.isNotBlank()) {
                    result.add(sentence)
                }
                start = end + 1
            }

            i++
        }

        // Remaining text
        if (start < line.length) {
            val remaining = line.substring(start).trim()
            if (remaining.isNotBlank()) {
                result.add(remaining)
            }
        }

        return result
    }
}