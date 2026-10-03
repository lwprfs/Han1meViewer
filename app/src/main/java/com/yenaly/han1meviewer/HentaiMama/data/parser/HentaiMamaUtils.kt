package com.yenaly.han1meviewer.HentaiMama.data.parser

import com.yenaly.han1meviewer.EMPTY_STRING
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Element

internal object HentaiMamaHtmlUtils {

    fun resolveUrl(raw: String?, baseUrl: String): String {
        if (raw.isNullOrBlank()) return EMPTY_STRING
        return when {
            raw.startsWith("http") -> raw
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("/") -> baseUrl.trimEnd('/') + raw
            else -> baseUrl.trimEnd('/') + "/" + raw
        }
    }

    fun resolveUrlOrEmpty(raw: String?, baseUrl: String): String =
        resolveUrl(raw, baseUrl).takeIf { it.isNotBlank() }.orEmpty()

    fun unescapeHtml(raw: String?): String {
        if (raw.isNullOrBlank()) return EMPTY_STRING
        return raw
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
    }

    fun parseSrcset(raw: String?): Map<Int, String> = parseSrcsetMap(raw)

    fun parseSrcsetMap(raw: String?): Map<Int, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out: LinkedHashMap<Int, String> = LinkedHashMap()
        raw.split(',').forEach { part: String ->
            val trimmed: String = part.trim()
            if (trimmed.isEmpty()) return@forEach
            val pieces: List<String> = trimmed.split(' ').filter { it.isNotBlank() }
            if (pieces.size < 2) return@forEach
            val url: String = pieces[0]
            val widthToken: String = pieces[1].trim()
            if (!widthToken.endsWith("w", ignoreCase = true)) return@forEach
            val width: Int = widthToken.dropLast(1).toIntOrNull() ?: return@forEach
            if (url.isNotBlank() && width > 0) {
                out[width] = url
            }
        }
        return out
    }

    fun parseViews(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val cleaned: String = raw.lowercase()
            .replace(",", "")
            .replace("views", "")
            .replace("view", "")
            .trim()
        if (cleaned.isEmpty()) return null
        val number: Double = cleaned
            .filter { it.isDigit() || it == '.' }
            .toDoubleOrNull()
            ?: return null
        return when {
            cleaned.contains('k') -> (number * 1_000).toLong()
            cleaned.contains('m') -> (number * 1_000_000).toLong()
            cleaned.contains('b') -> (number * 1_000_000_000).toLong()
            else -> number.toLong()
        }
    }

    fun parseEpisodeCount(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        return raw.trim()
            .substringBefore(' ')
            .filter { it.isDigit() }
            .toIntOrNull()
    }

    fun parseDurationToSeconds(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val parts: List<String> = raw.trim().split(':')
        return when (parts.size) {
            2 -> {
                val m: Int = parts[0].toIntOrNull() ?: return null
                val s: Int = parts[1].toIntOrNull() ?: return null
                m * 60 + s
            }
            3 -> {
                val h: Int = parts[0].toIntOrNull() ?: return null
                val m: Int = parts[1].toIntOrNull() ?: return null
                val s: Int = parts[2].toIntOrNull() ?: return null
                h * 3600 + m * 60 + s
            }
            else -> null
        }
    }

    fun slugFromUrl(url: String?): String {
        if (url.isNullOrBlank()) return EMPTY_STRING
        return url.trimEnd('/').substringAfterLast('/')
    }

    fun slugFromStudioUrl(url: String?): String {
        val http = url?.toHttpUrlOrNull() ?: return EMPTY_STRING
        return http.pathSegments.lastOrNull { it.isNotBlank() }.orEmpty()
    }

    fun genreSlugFromUrl(url: String?): String {
        if (url.isNullOrBlank()) return EMPTY_STRING
        val path: String = url.substringAfter("/genre/", "")
        return path.trimEnd('/')
            .substringBefore('/')
            .takeIf { it.isNotBlank() }
            .orEmpty()
    }

    fun isGenreUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return url.contains("/genre/", ignoreCase = true)
    }

    fun Element.optionalText(selector: String): String? =
        selectFirst(selector)?.text()?.trim()?.takeIf { it.isNotBlank() }

    fun Element.optionalAttr(selector: String, attr: String): String? =
        selectFirst(selector)?.attr(attr)?.takeIf { it.isNotBlank() }
}
