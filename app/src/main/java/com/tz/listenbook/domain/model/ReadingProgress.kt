package com.tz.listenbook.domain.model

data class ReadingProgress(
    val bookId: Long,
    val currentChapterIndex: Int,
    val currentPosition: Long,
    val playbackSpeed: Float = 1.0f,
    val voiceName: String = "zh-CN-XiaoxiaoNeural",
    val lastUpdated: Long
)