package com.tz.listenbook.data.remote.edgetts

import java.security.MessageDigest

object EdgeTtsConstants {
    const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    const val BASE_URL = "speech.platform.bing.com/consumer/speech/synthesize/readaloud"
    val WSS_URL = "wss://$BASE_URL/edge/v1?TrustedClientToken=$TRUSTED_CLIENT_TOKEN"
    const val OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3"
    const val CHROMIUM_VERSION = "143.0.3650.75"
    val SEC_MS_GEC_VERSION = "1-$CHROMIUM_VERSION"

    val CHINESE_VOICES = listOf(
        VoiceInfo("zh-CN-XiaoxiaoNeural", "晓晓", "女", "活泼、自然"),
        VoiceInfo("zh-CN-YunxiNeural", "云希", "男", "阳光、活力"),
        VoiceInfo("zh-CN-XiaoyiNeural", "晓伊", "女", "温柔、甜美"),
        VoiceInfo("zh-CN-YunjianNeural", "云健", "男", "沉稳、大气"),
        VoiceInfo("zh-CN-XiaoxuanNeural", "晓萱", "女", "温柔、亲和"),
        VoiceInfo("zh-CN-YunyangNeural", "云扬", "男", "新闻主播"),
        VoiceInfo("zh-CN-YunxiaNeural", "云夏", "男", "儿童、故事"),
        VoiceInfo("zh-CN-liaoning-XiaobeiNeural", "晓北", "女", "东北话"),
        VoiceInfo("zh-CN-shaanxi-XiaoniNeural", "晓妮", "女", "陕西话")
    )

    val DEFAULT_VOICE = CHINESE_VOICES[0]

    fun generateSecMsGec(): String {
        val winEpoch = 11644473600.0
        val sToNs = 1e9

        var ticks = System.currentTimeMillis() / 1000.0
        ticks += winEpoch
        ticks -= ticks % 300
        ticks *= sToNs / 100

        val strToHash = "${"%.0f".format(ticks)}$TRUSTED_CLIENT_TOKEN"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(strToHash.toByteArray(Charsets.US_ASCII))
        return digest.joinToString("") { "%02X".format(it) }
    }
}

data class VoiceInfo(
    val name: String,
    val displayName: String,
    val gender: String,
    val style: String
)