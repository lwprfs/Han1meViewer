package com.yenaly.han1meviewer.OnAir.common

import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetwork

object OnAirUrlUtils {

    private val KNOWN_HOSTS = OnAirConstants.ONAIR_HOSTNAME

    fun isOnAirHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val lower = host.lowercase()
        return KNOWN_HOSTS.any { lower == it || lower.endsWith(".$it") }
    }

    fun normalizeVideoUrl(input: String): String {
        if (input.isBlank()) return input
        val base = OnAirNetwork.baseUrl.trimEnd('/')

        if (input.startsWith("http://") || input.startsWith("https://")) {
            val videoId = Regex("""/watch/(\d+)""").find(input)?.groupValues?.getOrNull(1)
            return if (videoId != null) "$base/watch/$videoId" else input
        }

        val trimmed = input.trim('/')
        return when {
            trimmed.startsWith("watch/") -> "$base/$trimmed"
            trimmed.all { it.isDigit() } -> "$base/watch/$trimmed"
            else -> "$base/$trimmed"
        }
    }

    fun normalizeUserUrl(input: String): String {
        if (input.isBlank()) return input
        val base = OnAirNetwork.baseUrl.trimEnd('/')
        if (input.startsWith("http://") || input.startsWith("https://")) {
            return input
        }
        val trimmed = input.trim('/')
        return when {
            trimmed.startsWith("user/") -> "$base/$trimmed"
            else -> "$base/user/$trimmed"
        }
    }

    fun normalizeTagUrl(input: String): String {
        if (input.isBlank()) return input
        val base = OnAirNetwork.baseUrl.trimEnd('/')
        if (input.startsWith("http://") || input.startsWith("https://")) {
            return input
        }
        val trimmed = input.trim('/')
        return when {
            trimmed.startsWith("tag/") -> "$base/$trimmed"
            else -> "$base/tag/$trimmed"
        }
    }

    fun extractVideoId(input: String?): String? {
        if (input.isNullOrBlank()) return null
        return Regex("""/watch/(\d+)""").find(input)?.groupValues?.getOrNull(1)
    }

    fun extractUsername(input: String?): String? {
        if (input.isNullOrBlank()) return null
        return Regex("""/user/([^/?#]+)""").find(input)?.groupValues?.getOrNull(1)
    }

    fun extractTagName(input: String?): String? {
        if (input.isNullOrBlank()) return null
        return Regex("""/tag/([^/?#]+)""").find(input)?.groupValues?.getOrNull(1)
    }
}
