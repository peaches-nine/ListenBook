package com.tz.audiobook.data.remote.edgetts

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EdgeTtsClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "EdgeTtsClient"
        private const val SYNTHESIS_TIMEOUT = 120_000L
    }

    suspend fun synthesize(
        text: String,
        voice: String = EdgeTtsConstants.DEFAULT_VOICE.name,
        rate: String = "+0%",
        pitch: String = "+0Hz"
    ): ByteArray = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting synthesis for text length=${text.length}, voice=$voice")

        val audioChunks = mutableListOf<ByteArray>()
        val synthesisComplete = AtomicBoolean(false)
        val connectionLatch = CountDownLatch(1)
        var webSocket: WebSocket? = null
        var connectionError: Throwable? = null

        val connectionId = UUID.randomUUID().toString().replace("-", "")
        val secMsGec = EdgeTtsConstants.generateSecMsGec()

        val url = "${EdgeTtsConstants.WSS_URL}&ConnectionId=$connectionId" +
                "&Sec-MS-GEC=$secMsGec" +
                "&Sec-MS-GEC-Version=${EdgeTtsConstants.SEC_MS_GEC_VERSION}"

        Log.d(TAG, "WebSocket URL: ${url.take(100)}...")

        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${EdgeTtsConstants.CHROMIUM_VERSION} Safari/537.36 Edg/${EdgeTtsConstants.CHROMIUM_VERSION}")
            .addHeader("Accept", "*/*")
            .addHeader("Accept-Encoding", "gzip, deflate, br")
            .addHeader("Accept-Language", "en-US,en;q=0.9")
            .addHeader("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .addHeader("Pragma", "no-cache")
            .addHeader("Cache-Control", "no-cache")
            .addHeader("Sec-WebSocket-Version", "13")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected, response code: ${response.code}")
                connectionLatch.countDown()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                Log.v(TAG, "Text message: ${text.take(100)}...")
                when {
                    text.contains("Path:turn.end") -> {
                        synthesisComplete.set(true)
                        Log.d(TAG, "Synthesis complete signal received")
                    }
                    text.contains("Path:turn.start") -> {
                        Log.d(TAG, "Turn start received")
                    }
                }
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                val parsedAudio = parseBinaryMessage(data)
                if (parsedAudio != null) {
                    audioChunks.add(parsedAudio)
                    Log.v(TAG, "Audio chunk: ${parsedAudio.size} bytes, total: ${audioChunks.size} chunks")
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                connectionError = t
                connectionLatch.countDown()
                synthesisComplete.set(true)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: code=$code, reason=$reason")
            }
        })

        // Wait for connection
        if (!connectionLatch.await(15, TimeUnit.SECONDS)) {
            webSocket.cancel()
            throw RuntimeException("WebSocket connection timeout")
        }

        if (connectionError != null) {
            throw RuntimeException("WebSocket connection failed", connectionError)
        }

        try {
            // Send config
            sendConfig(webSocket!!)

            // Split text and synthesize
            val textChunks = TextChunker.chunkText(text)
            Log.d(TAG, "Text split into ${textChunks.size} chunks")

            for ((index, chunk) in textChunks.withIndex()) {
                synthesisComplete.set(false)

                val ssml = SsmlBuilder.build(chunk, voice, rate)
                Log.d(TAG, "Sending SSML for chunk ${index + 1}/${textChunks.size}, length=${chunk.length}")
                sendSsml(webSocket!!, ssml)

                // Wait for synthesis to complete
                withTimeout(SYNTHESIS_TIMEOUT) {
                    while (!synthesisComplete.get()) {
                        Thread.sleep(50)
                    }
                }
                Log.d(TAG, "Chunk ${index + 1} done, audio chunks: ${audioChunks.size}")
            }

            if (audioChunks.isEmpty()) {
                throw RuntimeException("No audio data received from TTS service")
            }

            val totalAudio = audioChunks.reduce { acc, bytes -> acc + bytes }
            Log.d(TAG, "Total audio: ${totalAudio.size} bytes")
            return@withContext totalAudio

        } finally {
            webSocket?.close(1000, "Synthesis complete")
        }
    }

    private fun sendConfig(webSocket: WebSocket) {
        val timestamp = java.time.Instant.now().toString() + "Z"
        val message = "X-Timestamp:$timestamp\r\n" +
                "Content-Type:application/json; charset=utf-8\r\n" +
                "Path:speech.config\r\n\r\n" +
                "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"${EdgeTtsConstants.OUTPUT_FORMAT}\"}}}}"

        Log.d(TAG, "Sending config message")
        webSocket.send(message)
    }

    private fun sendSsml(webSocket: WebSocket, ssml: String) {
        val requestId = UUID.randomUUID().toString().replace("-", "")
        val timestamp = java.time.Instant.now().toString() + "Z"

        val message = "X-RequestId:$requestId\r\n" +
                "Content-Type:application/ssml+xml\r\n" +
                "X-Timestamp:$timestamp\r\n" +
                "Path:ssml\r\n\r\n" +
                ssml

        webSocket.send(message)
    }

    private fun parseBinaryMessage(data: ByteArray): ByteArray? {
        if (data.size < 2) {
            return null
        }

        val headerLength = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)

        if (data.size < 2 + headerLength) {
            return null
        }

        if (headerLength == 0) {
            return data.copyOfRange(2, data.size)
        }

        // Parse header as text key:value pairs separated by \r\n
        val headerText = String(data, 2, headerLength, Charsets.UTF_8)
        val parameters = mutableMapOf<String, String>()

        headerText.split("\r\n").forEach { line ->
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim()
                val value = line.substring(colonIndex + 1).trim()
                parameters[key] = value
            }
        }

        val path = parameters["Path"] ?: ""
        val contentType = parameters["Content-Type"] ?: ""

        // Check if this is an audio message
        if (path == "audio" || contentType == "audio/mpeg") {
            val audioStart = 2 + headerLength
            if (audioStart < data.size) {
                return data.copyOfRange(audioStart, data.size)
            }
        }

        return null
    }
}