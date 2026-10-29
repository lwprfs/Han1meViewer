package com.yenaly.han1meviewer.FapFolder.data.parser

import com.yenaly.han1meviewer.FapFolder.common.FapFolderUrlUtils
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderSuggestedCommunity
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderTrendingTag
import org.jsoup.nodes.Document

object FapFolderHomeExtrasParser {

    fun parseTrending(doc: Document, baseUrl: String): List<FapFolderTrendingTag> {
        val card = doc.selectFirst("div.card.bg-gradient-red")
            ?: doc.selectFirst("div.card:has(a.trending-item)")
            ?: return emptyList()
        val headerText = FapFolderHtmlUtils.cleanText(
            card.selectFirst(".card-header")?.text()
        ).lowercase()
        if (headerText.isNotBlank() && !headerText.contains("trending")) return emptyList()

        return card.select("a.trending-item").mapNotNull { anchor ->
            val href = anchor.absUrl("href").takeIf { it.isNotBlank() }
                ?: anchor.attr("href").takeIf { it.isNotBlank() }
                    ?.let(FapFolderUrlUtils::absoluteUrl)
                ?: return@mapNotNull null
            val tagText = FapFolderHtmlUtils.cleanText(
                anchor.selectFirst("span.hash")?.text() ?: anchor.text()
            ).removePrefix("#")
            if (tagText.isBlank()) return@mapNotNull null
            val count = FapFolderHtmlUtils.parseLeadingInt(
                anchor.selectFirst("span.frequency")?.text()
            )
            FapFolderTrendingTag(
                tag = tagText,
                postCount = count,
                url = href,
            )
        }.distinctBy { it.tag.lowercase() }
    }

    fun parseSuggested(doc: Document, baseUrl: String): List<FapFolderSuggestedCommunity> {
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
