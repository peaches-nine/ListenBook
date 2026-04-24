package com.tz.listenbook.data.remote.edgetts

object TextChunker {

    fun chunkText(text: String, maxLength: Int = 2000): List<String> {
        val chunks = mutableListOf<String>()
        val paragraphs = text.split("\n").filter { it.isNotBlank() }

        val currentChunk = StringBuilder()

        for (paragraph in paragraphs) {
            if (currentChunk.length + paragraph.length + 1 > maxLength) {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString())
                    currentChunk.clear()
                }

                if (paragraph.length > maxLength) {
                    chunks.addAll(splitLongParagraph(paragraph, maxLength))
                } else {
                    currentChunk.append(paragraph)
                }
            } else {
                if (currentChunk.isNotEmpty()) {
                    currentChunk.append("\n")
                }
                currentChunk.append(paragraph)
            }
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString())
        }

        return chunks
    }

    private fun splitLongParagraph(text: String, maxLength: Int): List<String> {
        val sentences = text.split(Regex("(?<=[。！？.!?；;])"))
        val chunks = mutableListOf<String>()
        val currentChunk = StringBuilder()

        for (sentence in sentences) {
            if (currentChunk.length + sentence.length > maxLength) {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString())
                    currentChunk.clear()
                }

                if (sentence.length > maxLength) {
                    chunks.addAll(sentence.chunked(maxLength))
                } else {
                    currentChunk.append(sentence)
                }
            } else {
                currentChunk.append(sentence)
            }
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString())
        }

        return chunks
    }
}