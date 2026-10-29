package com.yenaly.han1meviewer.FapFolder.data.parser

import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.common.FapFolderUrlUtils
import com.yenaly.han1meviewer.FapFolder.data.local.FapFolderSessionCache
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderSessionContext
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderSiteStats
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderViewer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object FapFolderSessionParser {

    private val pointsRegex =
        Regex("""Points:\s*<span[^>]*class=["'][^"']*badge-info[^"']*["'][^>]*>([^<]+)</span>""")

    private val walletRegex =
        Regex("""Wallet:\s*<span[^>]*class=["'][^"']*badge-success[^"']*["'][^>]*>([^<]+)</span>""")

    private val usernameAltRegex =
        Regex("""alt=["']([^"']+)\s+profile picture["']""", RegexOption.IGNORE_CASE)

    fun parse(html: String?, baseUrl: String = FapFolderConstants.BASE_URL): FapFolderSessionContext {
        if (html.isNullOrBlank()) return FapFolderSessionContext.empty()

        val doc = try {
            Jsoup.parse(html, baseUrl)
        } catch (_: Exception) {
            return FapFolderSessionContext.empty()
        }

        val loggedIn = FapFolderHtmlUtils.extractLoggedInFlag(html)
            ?: inferLoggedInFromDom(doc)
            ?: false

        val sitePath = FapFolderHtmlUtils.extractSitePath(html)
            ?: FapFolderConstants.BASE_URL.trimEnd('/')

        val ajaxPath = FapFolderHtmlUtils.extractAjaxPath(html)
            ?: FapFolderConstants.AJAX_BASE.trimEnd('/')

        val uploadsPath = FapFolderHtmlUtils.extractUploadsPath(html)
            ?: FapFolderConstants.UPLOADS_BASE.trimEnd('/')

        val secret = FapFolderHtmlUtils.extractSecret(html)

        val currentPage = FapFolderHtmlUtils.extractCurrentPage(html)

        val viewer = parseViewer(doc, html, loggedIn)

        val siteStats = parseSiteStats(doc, html)

        return FapFolderSessionContext(
            loggedIn = loggedIn,
            sitePath = sitePath,
            ajaxPath = ensureTrailingSlash(ajaxPath),
            uploadsPath = uploadsPath.trimEnd('/'),
            secret = secret,
            viewer = viewer,
            siteStats = siteStats,
            currentPage = currentPage,
        )
    }

    fun parseAndCache(
        html: String?,
        baseUrl: String = FapFolderConstants.BASE_URL,
    ): FapFolderSessionContext {
        val parsed = parse(html, baseUrl)
        if (parsed.loggedIn || parsed.viewer.username != null) {
            FapFolderSessionCache.update(parsed)
        }
        return parsed
    }

    private fun ensureTrailingSlash(path: String): String =
        if (path.endsWith('/')) path else "$path/"

    private fun inferLoggedInFromDom(doc: Document): Boolean? {
        if (doc.selectFirst(".user-menu img") != null) return true
        if (doc.selectFirst("a[href*='/signout']") != null) return true
        if (doc.selectFirst("body.visitor") != null) return false
        if (doc.selectFirst("a[href*='/signin']") != null) return false
        return null
    }

    private fun parseViewer(
        doc: Document,
        html: String,
        loggedIn: Boolean,
    ): FapFolderViewer {
        val userMenuImg = doc.selectFirst(".user-menu img")

        val avatarUrl: String? = userMenuImg?.absUrl("src")
            ?.takeIf { it.isNotBlank() }
            ?: userMenuImg?.attr("src")?.takeIf { it.isNotBlank() }
                ?.let(FapFolderUrlUtils::absoluteUrl)

        val usernameFromAlt: String? = userMenuImg?.attr("alt")
            ?.let { alt -> usernameAltRegex.find(alt)?.groupValues?.getOrNull(1)?.trim() }
            ?.takeIf { it.isNotBlank() }

        val username: String? = usernameFromAlt
            ?: extractUsernameFromMenu(doc)
            ?: parseLoggedInUsername(html)

        val userId: Long? = parseLoggedInUserId(html)
            ?: run {
                val m = Regex("""c_user=(\d+)""").find(html)
                m?.groupValues?.getOrNull(1)?.toLongOrNull()
            }

        val points: String? = pointsRegex.find(html)
            ?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }

        val wallet: String? = walletRegex.find(html)
            ?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }

        return FapFolderViewer(
            userId = userId,
            username = username,
            avatarUrl = avatarUrl,
            points = points,
            wallet = wallet,
            isLoggedIn = loggedIn,
        )
    }

    private fun extractUsernameFromMenu(doc: Document): String? {
        val sidebar = doc.selectFirst(".offcanvas-sidebar") ?: return null
        val link = sidebar.selectFirst("a[href*='/'][href]") ?: return null
        val href = link.attr("href").takeIf { it.isNotBlank() } ?: return null
        val candidate = FapFolderUrlUtils.extractUsername(href) ?: return null
        if (candidate.isBlank()) return null
        return candidate
    }

    fun parseLoggedInUsername(html: String?): String? {
        if (html.isNullOrBlank()) return null
        val doc = runCatching { Jsoup.parse(html) }.getOrNull() ?: return null

        val profileAnchor = doc.select("div.dropdown-menu a.dropdown-item[href]")
            .firstOrNull { it.text().trim().equals("Profile", ignoreCase = true) }
        if (profileAnchor != null) {
            val href = profileAnchor.absUrl("href").takeIf { it.isNotBlank() }
                ?: profileAnchor.attr("href").takeIf { it.isNotBlank() }
            val slug = href?.trimEnd('/')?.substringAfterLast('/')
            if (!slug.isNullOrBlank()) return slug
        }

        val sidebarAnchor = doc.selectFirst("ul.main-side-nav > li:first-child > a[href]")
        if (sidebarAnchor != null) {
            val href = sidebarAnchor.absUrl("href").takeIf { it.isNotBlank() }
                ?: sidebarAnchor.attr("href").takeIf { it.isNotBlank() }
            val slug = href?.trimEnd('/')?.substringAfterLast('/')
            if (!slug.isNullOrBlank()) return slug
        }

        val headerAnchor = doc.selectFirst("div.profile-name-wrapper > a[href]")
        if (headerAnchor != null) {
            val href = headerAnchor.absUrl("href").takeIf { it.isNotBlank() }
                ?: headerAnchor.attr("href").takeIf { it.isNotBlank() }
            val slug = href?.trimEnd('/')?.substringAfterLast('/')
            if (!slug.isNullOrBlank()) return slug
        }

        return null
    }

    fun parseLoggedInUserId(html: String?): Long? {
        if (html.isNullOrBlank()) return null
        val doc = runCatching { Jsoup.parse(html) }.getOrNull() ?: return null
        val crop = doc.selectFirst("i.js_init-crop-picture[data-handle=user][data-id]")
            ?: doc.selectFirst("i.js_init-position-picture[data-handle=user][data-id]")
        crop?.attr("data-id")?.toLongOrNull()?.let { return it }
        doc.selectFirst("div.js_see-more[data-get=profile_groups][data-uid]")
            ?.attr("data-uid")?.toLongOrNull()?.let { return it }
        return null
    }

    private fun parseSiteStats(
        doc: Document,
        html: String,
    ): FapFolderSiteStats? {
        val (members, online, guests) = FapFolderHtmlUtils.parseSiteStats(html)
        if (members == null && online == null && guests == null) return null
        return FapFolderSiteStats(
            totalMembers = members,
            online = online,
            guests = guests,
        )
    }

    fun isLoggedIn(html: String?): Boolean {
        if (html.isNullOrBlank()) return false
        return FapFolderHtmlUtils.extractLoggedInFlag(html) == true
    }

    fun isCloudflareChallenge(html: String?): Boolean =
        FapFolderHtmlUtils.looksLikeCloudflareChallenge(html)
}
