package com.yenaly.han1meviewer.FapFolder.data.parser

import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.common.FapFolderUrlUtils
import org.jsoup.nodes.Element

internal object FapFolderHtmlUtils {

    private val viewsRegex =
        Regex("""([\d.,]+)\s*(views?|plays?)""", RegexOption.IGNORE_CASE)

    private val durationRegex =
        Regex("""^(\d+):(\d{2})(?::(\d{2}))?$""")

    private val compactNumberRegex =
        Regex("""([\d.]+)\s*([KMB])\b""", RegexOption.IGNORE_CASE)

    private val whitespaceRegex =
        Regex("""\s+""")

    fun parseViews(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.replace(",", "").trim()
        val match = viewsRegex.find(cleaned)
        if (match != null) {
            val number = match.groupValues[1].toDoubleOrNull() ?: return null
            return number.toLong()
        }
        val compact = compactNumberRegex.find(cleaned)
        if (compact != null) {
            val base = compact.groupValues[1].toDoubleOrNull() ?: return null
            return when (compact.groupValues[2].uppercase()) {
                "K" -> (base * 1_000).toLong()
                "M" -> (base * 1_000_000).toLong()
                "B" -> (base * 1_000_000_000).toLong()
                else -> base.toLong()
            }
        }
        return cleaned.toLongOrNull()
    }

    fun parseViewsCount(raw: String?): Int? = parseViews(raw)?.toInt()

    fun parseLeadingInt(raw: String?): Int? = FapFolderUrlUtils.parseLeadingInt(raw)

    fun parseLeadingLong(raw: String?): Long? = FapFolderUrlUtils.parseLeadingLong(raw)

    fun parseDurationSeconds(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val match = durationRegex.matchEntire(raw.trim()) ?: run {
            return raw.trim().toDoubleOrNull()?.toLong()
        }
        val a = match.groupValues[1].toLongOrNull() ?: return null
        val b = match.groupValues[2].toLongOrNull() ?: return null
        val c = match.groupValues.getOrNull(3)?.toLongOrNull()
        return if (c != null) a * 3600 + b * 60 + c else a * 60 + b
    }

    fun cleanText(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.replace('\u00A0', ' ')
            .replace(whitespaceRegex, " ")
            .trim()
    }

    fun extractBackgroundImageUrl(style: String?): String? =
        FapFolderUrlUtils.extractBackgroundImageUrl(style)

    fun extractVideoId(rawId: String?): String? {
        if (rawId.isNullOrBlank()) return null
        return rawId.removePrefix("video-").takeIf { it.isNotBlank() && it.all(Char::isDigit) }
    }

    fun extractIframeSrc(iframe: Element?): String? {
        if (iframe == null) return null
        val raw = iframe.absUrl("src").takeIf { it.isNotBlank() }
            ?: iframe.attr("src").takeIf { it.isNotBlank() }
        return raw?.let(FapFolderUrlUtils::absoluteUrl)?.takeIf { it.isNotBlank() }
    }

    fun extractIframeProvider(iframe: Element?): String? {
        val src = extractIframeSrc(iframe) ?: return null
        val host = FapFolderUrlUtils.hostOf(src) ?: return null
        return when {
            host.contains("youtube", ignoreCase = true) -> "YouTube"
            host.contains("youtu.be", ignoreCase = true) -> "YouTube"
            host.contains("vimeo", ignoreCase = true) -> "Vimeo"
            host.contains("facebook", ignoreCase = true) -> "Facebook"
            host.contains("twitter", ignoreCase = true) -> "Twitter"
            host.contains("x.com", ignoreCase = true) -> "X"
            host.contains("dailymotion", ignoreCase = true) -> "Dailymotion"
            host.contains("tiktok", ignoreCase = true) -> "TikTok"
            host.contains("instagram", ignoreCase = true) -> "Instagram"
            host.contains("streamable", ignoreCase = true) -> "Streamable"
            host.contains("fluidplayer", ignoreCase = true) -> "FluidPlayer"
            else -> host
        }
    }

    fun Element.optionalText(selector: String): String? =
        selectFirst(selector)?.text()?.let(::cleanText)?.takeIf { it.isNotBlank() }

    fun Element.optionalOwnText(selector: String): String? =
        selectFirst(selector)?.ownText()?.let(::cleanText)?.takeIf { it.isNotBlank() }

    fun Element.optionalAttr(selector: String, attr: String): String? =
        selectFirst(selector)?.attr(attr)?.takeIf { it.isNotBlank() }

    fun Element.optionalAbsUrl(selector: String, attr: String): String? =
        selectFirst(selector)?.absUrl(attr)?.takeIf { it.isNotBlank() }

    fun isAdUrl(url: String?): Boolean = FapFolderUrlUtils.isAdHost(url)

    fun parseDataTimeIso(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        return if (trimmed.length >= 19) trimmed.substring(0, 19) else trimmed
    }

    fun extractPostIdFromPermalink(href: String?): String? {
        return FapFolderUrlUtils.extractPostSlug(href)
    }

    fun looksLikeCloudflareChallenge(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        val t = body.lowercase()
        return t.contains("cf-mitigated") ||
                t.contains("just a moment") ||
                t.contains("challenges.cloudflare.com") ||
                t.contains("cf-chl-") ||
                (t.contains("<title>attention required") && t.contains("cloudflare"))
    }

    fun looksLikeLoggedOut(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        val m = Regex("""var\s+logged_in\s*=\s*(\d+)""").find(body) ?: return false
        return m.groupValues.getOrNull(1) == "0"
    }

    fun extractSiteVar(body: String?, varName: String): String? {
        if (body.isNullOrBlank()) return null
        val escaped = Regex.escape(varName)
        val regex = Regex("""var\s+$escaped\s*=\s*['"]([^'"]*)['"]""")
        return regex.find(body)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    fun extractSiteVarInt(body: String?, varName: String): Int? =
        extractSiteVar(body, varName)?.toIntOrNull()

    fun extractSecret(body: String?): String? = extractSiteVar(body, "secret")

    fun extractAjaxPath(body: String?): String? = extractSiteVar(body, "ajax_path")

    fun extractUploadsPath(body: String?): String? = extractSiteVar(body, "uploads_path")

    fun extractSitePath(body: String?): String? = extractSiteVar(body, "site_path")

    fun extractCurrentPage(body: String?): String? = extractSiteVar(body, "current_page")

    fun extractLoggedInFlag(body: String?): Boolean? {
        val raw = extractSiteVar(body, "logged_in") ?: return null
        return raw == "1"
    }

    fun parseSiteStats(body: String?): Triple<Int?, Int?, Int?> {
        if (body.isNullOrBlank()) return Triple(null, null, null)
        val regex = Regex(
            """Total\s+members\s*:\s*([\d,]+)\s*,?\s*Online\s*:\s*([\d,]+)\s*Guests\s*:\s*([\d,]+)""",
            RegexOption.IGNORE_CASE,
        )
        val m = regex.find(body) ?: return Triple(null, null, null)
        return Triple(
            m.groupValues[1].replace(",", "").toIntOrNull(),
            m.groupValues[2].replace(",", "").toIntOrNull(),
            m.groupValues[3].replace(",", "").toIntOrNull(),
        )
    }

    fun buildAbsoluteUrl(base: String, path: String): String =
        FapFolderUrlUtils.buildAbsoluteUrl(base, path)

    fun sanitizeFileName(raw: String?): String {
        if (raw.isNullOrBlank()) return "fapfolder_${System.currentTimeMillis()}"
        val safe = raw.replace(Regex("""[\\/:*?"<>|\x00-\x1F]"""), "_").trim()
        return safe.take(120).ifBlank { "fapfolder_${System.currentTimeMillis()}" }
    }

    fun extensionForVideo(url: String?): String {
        return FapFolderUrlUtils.extractVideoExtension(url)
            ?: FapFolderConstants.VIDEO_EXTENSIONS.firstOrNull { "mp4" == it }
            ?: "mp4"
    }
}

internal fun Element.isAdContainer(): Boolean {
    if (hasClass("hbanner") || hasClass("hmbanner") || hasClass("postbanner")) return true
    if (hasClass("of-ad-banner-parent") || hasClass("ui-box-ad")) return true
    if (hasClass("mof-ad-banner-parent") || hasClass("mof-ad-banner-mobile")) return true
    if (hasClass("mof-ad-banner-pc") || hasClass("of-ad-banner-pc")) return true
    if (hasClass("global-promo-banner")) return true
    if (id() == "global-promo-banner") return true
    return false
}

internal fun Element.containsOnlyAdContent(): Boolean {
    if (isAdContainer()) return true
    val iframe = selectFirst("iframe[src]") ?: return false
    val src = iframe.absUrl("src").takeIf { it.isNotBlank() }
        ?: iframe.attr("src").takeIf { it.isNotBlank() }
        ?: return false
    return FapFolderUrlUtils.isAdHost(src)
}
