package com.tz.audiobook.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tz.audiobook.data.remote.edgetts.EdgeTtsClient
import com.tz.audiobook.domain.model.Chapter
import com.tz.audiobook.parser.Sentence
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPipeline @Inject constructor(
    private val edgeTtsClient: EdgeTtsClient,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AudioPipeline"
        private const val AUDIO_CACHE_DIR = "audio_cache"
    }

    private val audioCacheDir = File(context.filesDir, AUDIO_CACHE_DIR).apply { mkdirs() }

    // Legacy: Chapter-level audio
    suspend fun getOrCreateAudioForChapter(
        chapter: Chapter,
        voice: String,
        speed: Float
    ): Uri {
        val cacheFile = File(
            audioCacheDir,
            "chapter_${chapter.id}_${voice.replace("-", "_")}.mp3"
        )

        if (cacheFile.exists() && cacheFile.length() > 0) {
            Log.d(TAG, "Using cached audio for chapter ${chapter.id}")
            return Uri.fromFile(cacheFile)
        }

        return generateAudioForChapter(chapter, voice, cacheFile)
    }

    private suspend fun generateAudioForChapter(
        chapter: Chapter,
        voice: String,
        outputFile: File
    ): Uri {
        Log.d(TAG, "Generating audio for chapter ${chapter.id}: ${chapter.title}")

        val audioData = edgeTtsClient.synthesize(
            text = chapter.content,
            voice = voice,
            rate = "+0%"
        )

        outputFile.writeBytes(audioData)
        Log.d(TAG, "Audio generated: ${outputFile.length()} bytes for chapter ${chapter.id}")

        return Uri.fromFile(outputFile)
    }

    // Sentence-level audio - always generate at 1.0x speed, ExoPlayer handles playback speed
    // This allows seamless speed changes without regenerating audio
    suspend fun getOrCreateAudioForSentence(
        chapterId: Long,
        sentence: Sentence,
        voice: String,
        speed: Float // Only used for interface compatibility, audio is always 1.0x
    ): Uri {
        // Cache filename doesn't include speed since audio is always at 1.0x
        val cacheFile = File(
            audioCacheDir,
            "sentence_${chapterId}_${sentence.index}_${voice.replace("-", "_")}.mp3"
        )

        if (cacheFile.exists() && cacheFile.length() > 0) {
            return Uri.fromFile(cacheFile)
        }

        return generateAudioForSentence(sentence, voice, cacheFile)
    }

    private suspend fun generateAudioForSentence(
        sentence: Sentence,
        voice: String,
        outputFile: File
    ): Uri {
        // Always generate at 1.0x speed (rate="+0%"), ExoPlayer handles speed changes
        val audioData = edgeTtsClient.synthesize(
            text = sentence.text,
            voice = voice,
            rate = "+0%"
        )

        outputFile.writeBytes(audioData)
        Log.d(TAG, "Audio generated: ${outputFile.length()} bytes for sentence ${sentence.index}")

        return Uri.fromFile(outputFile)
    }

    fun isSentenceCached(chapterId: Long, sentenceIndex: Int, voice: String, speed: Float): Boolean {
        val cacheFile = File(
            audioCacheDir,
            "sentence_${chapterId}_${sentenceIndex}_${voice.replace("-", "_")}.mp3"
        )
        return cacheFile.exists() && cacheFile.length() > 0
    }

    fun getChapterCacheProgress(
        chapterId: Long,
        sentenceCount: Int,
        voice: String,
        speed: Float
    ): Float {
        if (sentenceCount == 0) return 0f
        val voiceSafe = voice.replace("-", "_")

        var cached = 0
        for (i in 0 until sentenceCount) {
            val cacheFile = File(audioCacheDir, "sentence_${chapterId}_${i}_${voiceSafe}.mp3")
            if (cacheFile.exists() && cacheFile.length() > 0) {
                cached++
            }
        }
        return cached.toFloat() / sentenceCount
    }

    fun getCachedSentenceCount(
        chapterId: Long,
        sentenceCount: Int,
        voice: String,
        speed: Float
    ): Int {
        val voiceSafe = voice.replace("-", "_")

        var cached = 0
        for (i in 0 until sentenceCount) {
            val cacheFile = File(audioCacheDir, "sentence_${chapterId}_${i}_${voiceSafe}.mp3")
            if (cacheFile.exists() && cacheFile.length() > 0) {
                cached++
            }
        }
        return cached
    }

    // Pre-generate audio for multiple sentences in parallel
    suspend fun preGenerateSentences(
        chapterId: Long,
        sentences: List<Sentence>,
        startIndex: Int,
        count: Int,
        voice: String,
        speed: Float // Only used for interface compatibility
    ) = withContext(Dispatchers.IO) {
        val end = (startIndex + count).coerceAtMost(sentences.size)
        val jobs = (startIndex until end).map { i ->
            async {
                try {
                    if (!isSentenceCached(chapterId, i, voice, speed)) {
                        getOrCreateAudioForSentence(chapterId, sentences[i], voice, speed)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to pre-generate sentence $i", e)
                }
            }
        }
        jobs.awaitAll()
    }

    fun clearCache() {
        audioCacheDir.deleteRecursively()
        audioCacheDir.mkdirs()
    }

    fun getCacheSize(): Long {
        return audioCacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    // Get cache size grouped by chapter ID
    // Returns map of chapterId -> size in bytes
    fun getCacheByChapter(): Map<Long, Long> {
        val result = mutableMapOf<Long, Long>()
        audioCacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("sentence_")) {
                // Filename format: sentence_{chapterId}_{sentenceIndex}_{voice}.mp3
                val parts = file.name.split("_")
                if (parts.size >= 3) {
                    val chapterId = parts[1].toLongOrNull()
                    if (chapterId != null) {
                        result[chapterId] = (result[chapterId] ?: 0L) + file.length()
                    }
                }
            } else if (file.isFile && file.name.startsWith("chapter_")) {
                // Legacy format: chapter_{chapterId}_{voice}.mp3
                val parts = file.name.split("_")
                if (parts.size >= 2) {
                    val chapterId = parts[1].toLongOrNull()
                    if (chapterId != null) {
                        result[chapterId] = (result[chapterId] ?: 0L) + file.length()
                    }
                }
            }
        }
        return result
    }

    // Clear cache for specific chapter IDs
    fun clearCacheForChapters(chapterIds: Set<Long>) {
        audioCacheDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                val parts = file.name.split("_")
                val chapterId = when {
                    file.name.startsWith("sentence_") && parts.size >= 2 -> parts[1].toLongOrNull()
                    file.name.startsWith("chapter_") && parts.size >= 2 -> parts[1].toLongOrNull()
                    else -> null
                }
                if (chapterId != null && chapterId in chapterIds) {
                    file.delete()
                }
            }
        }
    }
}
