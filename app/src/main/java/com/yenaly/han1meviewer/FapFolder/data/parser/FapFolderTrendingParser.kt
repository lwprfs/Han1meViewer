package com.yenaly.han1meviewer.FapFolder.data.parser

import com.yenaly.han1meviewer.FapFolder.common.FapFolderUrlUtils
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderTrendingTag
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object FapFolderTrendingParser {

    fun parse(doc: Document, baseUrl: String): List<FapFolderTrendingTag> {
        val card = doc.selectFirst("div.card.bg-gradient-red") ?: return emptyList()
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

    fun parseFromHtml(html: String?, baseUrl: String): List<FapFolderTrendingTag> {
        if (html.isNullOrBlank()) return emptyList()
        val doc = runCatching { Jsoup.parse(html, baseUrl) }.getOrNull() ?: return emptyList()
        return parse(doc, baseUrl)
    }
}