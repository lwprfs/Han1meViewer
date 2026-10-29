package com.yenaly.han1meviewer.FapFolder.data.parser

import com.yenaly.han1meviewer.FapFolder.common.FapFolderUrlUtils
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderAutocompleteSuggestion
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object FapFolderAutocompleteParser {

    fun parse(html: String?, baseUrl: String): List<FapFolderAutocompleteSuggestion> {
        if (html.isNullOrBlank()) return emptyList()
        val doc = runCatching { Jsoup.parseBodyFragment(html, baseUrl) }.getOrNull()
            ?: return emptyList()

        val suggestions = mutableListOf<FapFolderAutocompleteSuggestion>()

        doc.select("a.data-container").forEach { anchor ->
            val href = anchor.absUrl("href").takeIf { it.isNotBlank() }
                ?: anchor.attr("href").takeIf { it.isNotBlank() }
                    ?.let(FapFolderUrlUtils::absoluteUrl)
            val label = FapFolderHtmlUtils.cleanText(
                anchor.selectFirst(".data-content .name")?.text() ?: anchor.text()
            )
            val avatar = anchor.selectFirst(".data-avatar img")?.absUrl("src")
                ?.takeIf { it.isNotBlank() }
            val bio = FapFolderHtmlUtils.cleanText(
                anchor.selectFirst(".data-content .text")?.text()
            ).takeIf { it.isNotBlank() }
            val groupSlug = href?.let(FapFolderUrlUtils::extractGroupSlug)
            val username = href?.let(FapFolderUrlUtils::extractUsername)

            when {
                !groupSlug.isNullOrBlank() -> {
                    suggestions += FapFolderAutocompleteSuggestion.Community(
                        label = label.ifBlank { groupSlug },
                        href = href,
                        slug = groupSlug,
                        avatarUrl = avatar,
                        followerCount = FapFolderHtmlUtils.parseLeadingInt(bio),
                    )
                }
                !username.isNullOrBlank() -> {
                    suggestions += FapFolderAutocompleteSuggestion.User(
                        label = label.ifBlank { username },
                        href = href,
                        username = username,
                        avatarUrl = avatar,
                        bio = bio,
                    )
                }
            }
        }

        doc.select("a[href*='/search/hashtag/']").forEach { anchor ->
            val href = anchor.absUrl("href").takeIf { it.isNotBlank() }
                ?: anchor.attr("href").takeIf { it.isNotBlank() }
                    ?.let(FapFolderUrlUtils::absoluteUrl)
            val tag = FapFolderUrlUtils.extractTagFromHashtagHref(href)
                ?: FapFolderHtmlUtils.cleanText(anchor.text()).removePrefix("#")
                    .takeIf { it.isNotBlank() }
                ?: return@forEach
            val label = if (tag.startsWith("#")) tag else "#$tag"
            suggestions += FapFolderAutocompleteSuggestion.Hashtag(
                label = label,
                href = href,
                tag = tag.removePrefix("#"),
            )
        }

        doc.select("a[href*='/search/']").forEach { anchor ->
            val href = anchor.absUrl("href").takeIf { it.isNotBlank() }
                ?: anchor.attr("href").takeIf { it.isNotBlank() }
                    ?.let(FapFolderUrlUtils::absoluteUrl)
                ?: return@forEach
            if (href.contains("/search/hashtag/")) return@forEach
            val label = FapFolderHtmlUtils.cleanText(anchor.text())
            if (label.isBlank()) return@forEach
            val query = href.substringAfter("/search/").substringBefore('?')
                .substringBefore('#')
                .let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() ?: it }
                .takeIf { it.isNotBlank() }
                ?: return@forEach
            suggestions += FapFolderAutocompleteSuggestion.SearchFor(
                label = label,
                href = href,
                query = query,
                isHashtag = false,
            )
        }

        return suggestions.distinctBy { it.label.lowercase() + "|" + it.href.orEmpty() }
    }

    fun fallbackFor(query: String, isHashtag: Boolean, baseUrl: String): FapFolderAutocompleteSuggestion.SearchFor {
        val trimmed = query.trim().removePrefix("#")
        val path = if (isHashtag) {
            FapFolderUrlUtils.toHashtagUrlPath(trimmed)
        } else {
            FapFolderUrlUtils.toSearchUrlPath(trimmed)
        }
        val href = baseUrl.trimEnd('/') + path
        val label = if (isHashtag) "Search for #$trimmed" else "Search for $trimmed"
        return FapFolderAutocompleteSuggestion.SearchFor(
            label = label,
            href = href,
            query = trimmed,
            isHashtag = isHashtag,
        )
    }
}
