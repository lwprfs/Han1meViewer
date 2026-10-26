package com.yenaly.han1meviewer.OnAir.common

import android.util.Base64

internal object OnAirEndpointCodec {

    private val ONION_SUFFIX: String = charArrayOf(
        '\u002e', '\u006f', '\u006e', '\u0069', '\u006f', '\u006e'
    ).concatToString()

    private val HOST_REGEX: Regex =
        Regex("^[a-z2-7]{16,56}${Regex.escape(ONION_SUFFIX)}$", RegexOption.IGNORE_CASE)

    fun decodeHost(encoded: String): String {
        val decoded = decode(encoded)
        require(HOST_REGEX.matches(decoded)) { "Invalid host after decoding" }
        return decoded
    }

    fun decodeUrl(encoded: String): String {
        val decoded = decode(encoded)
        require(decoded.startsWith("http://") || decoded.startsWith("https://")) {
            "Invalid url after decoding"
        }
        return decoded
    }

    private fun decode(encoded: String): String {
        if (encoded.isBlank()) error("Empty encoded endpoint")
        val bytes = Base64.decode(encoded.trim(), Base64.NO_WRAP)
        return String(bytes, Charsets.UTF_8).trim()
    }
}
