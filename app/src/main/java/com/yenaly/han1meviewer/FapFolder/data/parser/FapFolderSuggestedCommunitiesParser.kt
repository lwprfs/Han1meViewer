package com.yenaly.han1meviewer.FapFolder.data.parser

import com.yenaly.han1meviewer.FapFolder.common.FapFolderUrlUtils
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderSuggestedCommunity
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object FapFolderSuggestedCommunitiesParser {

    fun parse(doc: Document, baseUrl: String): List<FapFolderSuggestedCommunity> {
        val card = findSuggCard(doc) ?: return emptyList()
        return card.select("li.feeds-item").mapNotNull { li ->
            val avatarAnchor = li.selectFirst("a.data-avatar[href*='/groups/']")
                ?: li.selectFirst("a[href*='/groups/']")
                ?: return@mapNotNull null
            val href = avatarAnchor.absUrl("href").takeIf { it.isNotBlank() }
                ?: avatarAnchor.attr("href").takeIf { it.isNotBlank() }
                    ?.let(FapFolderUrlUtils::absoluteUrl)
                ?: return@mapNotNull null
            val slug = FapFolderUrlUtils.extractGroupSlug(href) ?: return@mapNotNull null
            val name = FapFolderHtmlUtils.cleanText(
                li.selectFirst("span.name a")?.text()
                    ?: li.selectFirst(".data-content .name")?.text()
                    ?: li.selectFirst("span.name")?.text()
            ).ifBlank { slug }
            val avatarUrl = avatarAnchor.selectFirst("img")?.absUrl("src")
                ?.takeIf { it.isNotBlank() }
            val joinBtn = li.selectFirst("button.js_join-group, button.js_leave-group")
            val groupId = joinBtn?.attr("data-id")?.toLongOrNull()
            val isMember = joinBtn?.hasClass("js_leave-group") == true
            val followerCount = FapFolderHtmlUtils.parseLeadingInt(
                li.selectFirst(".data-content > div:not(.name):not(.text)")?.text()
                    ?: li.selectFirst(".data-content .text")?.text()
            )
            FapFolderSuggestedCommunity(
                groupId = groupId,
                slug = slug,
                name = name,
                avatarUrl = avatarUrl,
                followerCount = followerCount,
                isMember = isMember,
            )
        }.distinctBy { it.slug.lowercase() }
    }

    fun parseFromHtml(html: String?, baseUrl: String): List<FapFolderSuggestedCommunity> {
        if (html.isNullOrBlank()) return emptyList()
        val doc = runCatching { Jsoup.parse(html, baseUrl) }.getOrNull() ?: return emptyList()
        return parse(doc, baseUrl)
    }

    private fun findSuggCard(doc: Document): org.jsoup.nodes.Element? {
        doc.select("div.card").forEach { card ->
            val headerText = FapFolderHtmlUtils.cleanText(
                card.selectFirst(".card-header-sugg")?.text()
                    ?: card.selectFirst(".card-header")?.text()
            ).lowercase()
            if (headerText.contains("suggested") && headerText.contains("communit")) {
                return card
            }
        }
        return null
    }
}