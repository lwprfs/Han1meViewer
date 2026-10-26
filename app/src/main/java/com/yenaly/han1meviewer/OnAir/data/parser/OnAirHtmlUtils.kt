package com.yenaly.han1meviewer.OnAir.data.parser

import org.jsoup.nodes.Element

internal object OnAirHtmlUtils {

    private val viewsRegex = Regex("""([\d.,]+)\s*(views?)""", RegexOption.IGNORE_CASE)
    private val durationRegex = Regex("""^(\d+):(\d{2})(?::(\d{2}))?$""")
    private val countRegex = Regex("""[\d,]+""")

    fun parseViews(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.replace(",", "").trim()
        val match = viewsRegex.find(cleaned) ?: return cleaned.toLongOrNull()
        val number = match.groupValues[1].toDoubleOrNull() ?: return null
        return number.toLong()
    }

    fun parseViewsCount(raw: String?): Int? = parseViews(raw)?.toInt()

    fun parseDurationSeconds(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val match = durationRegex.matchEntire(raw.trim()) ?: return null
        val a = match.groupValues[1].toLongOrNull() ?: return null
        val b = match.groupValues[2].toLongOrNull() ?: return null
        val c = match.groupValues.getOrNull(3)?.toLongOrNull()
        return if (c != null) a * 3600 + b * 60 + c else a * 60 + b
    }

    fun parseTrailingNumber(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val match = countRegex.find(raw) ?: return null
        return match.value.replace(",", "").toIntOrNull()
    }

    fun parseTrailingLong(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val match = countRegex.find(raw) ?: return null
        return match.value.replace(",", "").toLongOrNull()
    }

    fun extractVideoId(href: String?): String? {
        if (href.isNullOrBlank()) return null
        val path = href.substringBefore('?').substringBefore('#')
        val segment = path.trimEnd('/').substringAfterLast('/')
        return segment.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
    }

    fun extractPathSegment(href: String?): String? {
        if (href.isNullOrBlank()) return null
        val path = href.substringBefore('?').substringBefore('#')
        return path.trimEnd('/').substringAfterLast('/').takeIf { it.isNotEmpty() }
    }

    fun Element.optionalText(selector: String): String? =
        selectFirst(selector)?.text()?.trim()?.takeIf { it.isNotBlank() }

    fun Element.optionalAttr(selector: String, attr: String): String? =
        selectFirst(selector)?.attr(attr)?.takeIf { it.isNotBlank() }

    fun Element.absAttr(selector: String, attr: String): String? =
        selectFirst(selector)?.absUrl(attr)?.takeIf { it.isNotBlank() }

    fun Element.findDataAttr(attr: String): String? =
        attr(attr).takeIf { it.isNotBlank() }

    fun cleanText(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
