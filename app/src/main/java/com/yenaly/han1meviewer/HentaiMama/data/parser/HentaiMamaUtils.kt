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

    fun parseSrcset(raw: String?): Map<Int, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(',')
            .mapNotNull { part ->
                val trimmed = part.trim()
                val pieces = trimmed.split(' ')
                if (pieces.size < 2) return@mapNotNull null
                val url = pieces[0]
                val width = pieces[1].removeSuffix("w").toIntOrNull() ?: return@mapNotNull null
                width to url
            }
            .toMap()
    }

    fun parseViews(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.lowercase().replace(",", "").trim()
        val number = cleaned.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return null
        return when {
            cleaned.contains('k') || cleaned.contains("k views") -> (number * 1_000).toLong()
            cleaned.contains('m') -> (number * 1_000_000).toLong()
            cleaned.contains('b') -> (number * 1_000_000_000).toLong()
            else -> number.toLong()
        }
    }

    fun parseDurationToSeconds(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.trim().split(':')
        return when (parts.size) {
            2 -> {
                val m = parts[0].toIntOrNull() ?: return null
                val s = parts[1].toIntOrNull() ?: return null
                m * 60 + s
            }
            3 -> {
                val h = parts[0].toIntOrNull() ?: return null
                val m = parts[1].toIntOrNull() ?: return null
                val s = parts[2].toIntOrNull() ?: return null
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

    fun Element.optionalText(selector: String): String? =
        selectFirst(selector)?.text()?.trim()?.takeIf { it.isNotBlank() }

    fun Element.optionalAttr(selector: String, attr: String): String? =
        selectFirst(selector)?.attr(attr)?.takeIf { it.isNotBlank() }
}
