package com.yenaly.han1meviewer.FapFolder.common

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLDecoder
import java.net.URLEncoder

object FapFolderUrlUtils {

    private val HASHTAG_REGEX = FapFolderConstants.HASHTAG_REGEX
    private val VIDEO_EXT_REGEX =
        Regex("\\.(mp4|webm|m4a|m4v|mpeg)(\\?|$)", RegexOption.IGNORE_CASE)
    private val PHOTO_EXT_REGEX =
        Regex("\\.(webp|jpg|jpeg|png)(\\?|$)", RegexOption.IGNORE_CASE)
    private val BG_IMAGE_REGEX =
        Regex("""background-image\s*:\s*url\(['"]?([^'")]+)['"]?\)""", RegexOption.IGNORE_CASE)
    private val INT_PREFIX_REGEX = Regex("([\\d,]+)")

    fun normalizePath(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> FapFolderConstants.BASE_URL.trimEnd('/') + trimmed
            else -> FapFolderConstants.BASE_URL.trimEnd('/') + "/" + trimmed
        }
    }

    fun absoluteUrl(input: String?): String = normalizePath(input)

    fun normalizeAjaxPath(input: String?): String {
        if (input.isNullOrBlank()) return FapFolderConstants.AJAX_BASE
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> FapFolderConstants.BASE_URL.trimEnd('/') + trimmed
            else -> FapFolderConstants.AJAX_BASE + trimmed
        }
    }

    fun normalizeMediaUrl(raw: String?, uploadsBase: String? = null): String? {
        if (raw.isNullOrBlank()) return null
        val uploads = uploadsBase?.trimEnd('/') ?: FapFolderConstants.UPLOADS_BASE.trimEnd('/')
        val uploadsRoot = "https://" + FapFolderConstants.UPLOADS_HOST
        return when {
            raw.startsWith("http://", true) || raw.startsWith("https://", true) -> raw
            raw.startsWith("data:", true) -> raw
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("/uploads/") -> uploadsRoot + raw
            raw.startsWith("/") -> uploadsRoot + raw
            raw.startsWith("uploads/") -> uploadsRoot + "/" + raw
            raw.startsWith("photos/") ||
                raw.startsWith("preview/") ||
                raw.startsWith("thumbnails/") ||
                raw.startsWith("videos/") -> "$uploads/$raw"
            else -> "$uploads/$raw"
        }
    }

    fun resolveLightboxSource(source: String?, uploadsBase: String? = null): String? {
        if (source.isNullOrBlank()) return null
        val base = uploadsBase?.trimEnd('/') ?: FapFolderConstants.UPLOADS_BASE.trimEnd('/')
        return when {
            source.startsWith("http://", true) || source.startsWith("https://", true) -> source
            source.startsWith("//") -> "https:$source"
            source.startsWith("/") -> "https://" + FapFolderConstants.UPLOADS_HOST + source
            source.startsWith("uploads/") ->
                "https://" + FapFolderConstants.UPLOADS_HOST + "/" + source
            else -> "$base/$source"
        }
    }

    fun extractBackgroundImageUrl(styleOrHtml: String?): String? {
        if (styleOrHtml.isNullOrBlank()) return null
        return BG_IMAGE_REGEX.find(styleOrHtml)?.groupValues?.getOrNull(1)?.trim()
    }

    fun parseLeadingInt(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val m = INT_PREFIX_REGEX.find(raw) ?: return null
        return m.value.replace(",", "").toIntOrNull()
    }

    fun parseLeadingLong(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val m = INT_PREFIX_REGEX.find(raw) ?: return null
        return m.value.replace(",", "").toLongOrNull()
    }

    fun isVideoUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return VIDEO_EXT_REGEX.containsMatchIn(url)
    }

    fun isPhotoUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return PHOTO_EXT_REGEX.containsMatchIn(url)
    }

    fun extractVideoExtension(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val m = VIDEO_EXT_REGEX.find(url) ?: return null
        return m.groupValues.getOrNull(1)?.lowercase()
    }

    fun toSearchUrlPath(rawQuery: String): String {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) return "/search/"
        val hashtagMatch = HASHTAG_REGEX.find(trimmed)
        return if (hashtagMatch != null) {
            val tag = hashtagMatch.groupValues[1]
            "/search/hashtag/${URLEncoder.encode(tag, "UTF-8")}"
        } else {
            "/search/${URLEncoder.encode(trimmed, "UTF-8")}"
        }
    }

    fun toHashtagUrlPath(tag: String): String {
        val stripped = tag.trim().removePrefix("#")
        return "/search/hashtag/${URLEncoder.encode(stripped, "UTF-8")}"
    }

    fun toSearchAbsolute(rawQuery: String): String =
        FapFolderConstants.BASE_URL.trimEnd('/') + toSearchUrlPath(rawQuery)

    fun toHashtagAbsolute(tag: String): String =
        FapFolderConstants.BASE_URL.trimEnd('/') + toHashtagUrlPath(tag)

    fun extractHashtag(input: String?): String? {
        if (input.isNullOrBlank()) return null
        return HASHTAG_REGEX.find(input)?.groupValues?.getOrNull(1)
    }

    private fun pathSegments(href: String?): List<String>? {
        if (href.isNullOrBlank()) return null
        val parsed = href.trim().toHttpUrlOrNull() ?: return null
        return parsed.pathSegments.filter { it.isNotEmpty() }
    }

    fun extractPostSlug(href: String?): String? {
        if (href.isNullOrBlank()) return null
        val cleaned = href.substringBefore('?').substringBefore('#')
        val marker = "/posts/"
        val idx = cleaned.indexOf(marker)
        if (idx < 0) return null
        val slug = cleaned.substring(idx + marker.length)
            .trimEnd('/')
            .substringBefore('/')
        return slug.takeIf { it.isNotBlank() }
    }

    fun extractPhotoId(href: String?): Long? {
        val segs = pathSegments(href) ?: return null
        val idx = segs.indexOf("photos")
        if (idx < 0 || idx + 1 >= segs.size) return null
        return segs[idx + 1].toLongOrNull()
    }

    fun extractAlbumId(href: String?): String? = extractPhotoId(href)?.toString()

    fun extractGroupSlug(href: String?): String? {
        val segs = pathSegments(href) ?: return null
        val idx = segs.indexOf("groups")
        if (idx < 0 || idx + 1 >= segs.size) return null
        return segs[idx + 1].takeIf { it.isNotBlank() }
    }

    fun extractUsername(href: String?): String? {
        val segs = pathSegments(href) ?: return null
        if (segs.isEmpty()) return null
        val first = segs[0]
        if (first in RESERVED_SEGMENTS) return null
        return first.takeIf { it.isNotBlank() }
    }

    fun extractTagFromHashtagHref(href: String?): String? {
        if (href.isNullOrBlank()) return null
        val marker = "/search/hashtag/"
        val idx = href.indexOf(marker)
        if (idx < 0) return null
        val raw = href.substring(idx + marker.length).trimEnd('/').substringBefore('?')
        if (raw.isBlank()) return null
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull() ?: raw
    }

    fun encodeForm(value: String): String = URLEncoder.encode(value, "UTF-8")

    fun buildAbsoluteUrl(base: String, path: String): String {
        val b = base.trimEnd('/')
        val p = path.trimStart('/')
        return "$b/$p"
    }

    fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url.toHttpUrlOrNull()?.host
    }

    fun isAdHost(url: String?): Boolean {
        val host = hostOf(url) ?: return false
        return FapFolderConstants.AD_HOSTS_TO_SKIP.any { ad ->
            host == ad || host.endsWith(".$ad", ignoreCase = true)
        }
    }

    fun withUploadsPrefix(fileName: String?): String? = resolveLightboxSource(fileName)

    private val RESERVED_SEGMENTS = setOf(
        "groups", "posts", "search", "photos", "settings", "signin", "signout",
        "packages", "wallet", "includes", "content", "video", "uploads",
        "forums", "chat", "payments", "ads", "monetization",
    )
}