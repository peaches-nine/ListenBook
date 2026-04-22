package com.tz.audiobook.data.remote.edgetts

object SsmlBuilder {

    fun build(
        text: String,
        voice: String = EdgeTtsConstants.DEFAULT_VOICE.name,
        rate: String = "+0%",
        pitch: String = "+0Hz",
        volume: String = "+0%"
    ): String {
        return """
            <speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='zh-CN'>
                <voice name='$voice'>
                    <prosody pitch='$pitch' rate='$rate' volume='$volume'>
                        ${escapeXml(text)}
                    </prosody>
                </voice>
            </speak>
        """.trimIndent()
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("'", "&apos;")
            .replace("\"", "&quot;")
    }

    fun rateFromSpeed(speed: Float): String {
        val percent = ((speed - 1.0f) * 100).toInt()
        return if (percent >= 0) "+${percent}%" else "${percent}%"
    }

    fun pitchFromValue(pitch: Float): String {
        val hz = ((pitch - 1.0f) * 50).toInt()
        return if (hz >= 0) "+${hz}Hz" else "${hz}Hz"
    }
}