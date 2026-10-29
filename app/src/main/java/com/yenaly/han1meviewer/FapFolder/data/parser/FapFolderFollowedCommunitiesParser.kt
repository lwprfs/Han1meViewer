package com.yenaly.han1meviewer.FapFolder.data.parser

import com.yenaly.han1meviewer.FapFolder.common.FapFolderUrlUtils
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderGroupRow
import com.yenaly.han1meviewer.FapFolder.data.model.GroupVisibility
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object FapFolderFollowedCommunitiesParser {

    data class FirstPage(
        val username: String?,
        val userId: Long?,
        val groups: List<FapFolderGroupRow>,
        val hasMore: Boolean,
        val streamGet: String?,
        val streamUid: String?,
    )

    fun parseFirstPage(
        html: String?,
        pageUrl: String,
    ): FirstPage {
        if (html.isNullOrBlank()) {
            return FirstPage(null, null, emptyList(), false, null, null)
        }
        val doc: Document = runCatching { Jsoup.parse(html, pageUrl) }.getOrNull()
            ?: return FirstPage(null, null, emptyList(), false, null, null)

        val username = FapFolderSessionParser.parseLoggedInUsername(html)
        val userId = FapFolderSessionParser.parseLoggedInUserId(html)

        val groups = parseGroupItems(doc, pageUrl)

        val seeMore = doc.selectFirst("div.js_see-more[data-get=profile_groups]")
        val hasMore = seeMore != null && groups.isNotEmpty()

        return FirstPage(
            username = username,
            userId = userId,
            groups = groups,
            hasMore = hasMore,
            streamGet = seeMore?.attr("data-get")?.takeIf { it.isNotBlank() },
            streamUid = seeMore?.attr("data-uid")?.takeIf { it.isNotBlank() },
        )
    }

    fun parseAjaxFragment(
        htmlFragment: String?,
        baseUrl: String,
    ): List<FapFolderGroupRow> {
        if (htmlFragment.isNullOrBlank()) return emptyList()
        val wrapper = if (htmlFragment.contains("<ul", ignoreCase = true)) {
            htmlFragment
        } else {
            "<ul class=\"row\">$htmlFragment</ul>"
        }
        val doc = runCatching { Jsoup.parseBodyFragment(wrapper, baseUrl) }.getOrNull()
            ?: return emptyList()
        return parseGroupItems(doc, baseUrl)
    }

    private fun parseGroupItems(doc: Document, baseUrl: String): List<FapFolderGroupRow> {
        val items = doc.select("li > div.ui-box, li.col-md-6 > div.ui-box")
            .ifEmpty { doc.select("div.ui-box") }

        return items.mapNotNull { box ->
            val leaveBtn = box.selectFirst("button.js_leave-group[data-id]")
                ?: box.selectFirst("button.js_join-group[data-id]")
            val groupId = leaveBtn?.attr("data-id")?.toLongOrNull()
            val isMember = leaveBtn?.hasClass("js_leave-group") == true
            val privacyRaw = leaveBtn?.attr("data-privacy")?.lowercase().orEmpty()
            val visibility = when (privacyRaw) {
                "public" -> GroupVisibility.PUBLIC
                "private", "closed" -> GroupVisibility.PRIVATE
                "secret" -> GroupVisibility.SECRET
                else -> GroupVisibility.UNKNOWN
            }

            val nameAnchor = box.selectFirst("a.h6[href]")
                ?: box.selectFirst("div.mt10 > a[href]")
                ?: return@mapNotNull null

            val href = nameAnchor.absUrl("href").takeIf { it.isNotBlank() }
                ?: nameAnchor.attr("href").takeIf { it.isNotBlank() }
                    ?.let(FapFolderUrlUtils::absoluteUrl)
                ?: return@mapNotNull null

            val slug = FapFolderUrlUtils.extractGroupSlug(href) ?: return@mapNotNull null
            val name = FapFolderHtmlUtils.cleanText(nameAnchor.text())
                .ifBlank { slug }

            val avatarUrl = box.selectFirst("div.img img[src]")
                ?.absUrl("src")
                ?.takeIf { it.isNotBlank() }

            val followersText = box.selectFirst("div.mt10 > div")?.text()
                ?: box.selectFirst("div.mt10")?.text()
            val followers = FapFolderHtmlUtils.parseLeadingInt(followersText)

            FapFolderGroupRow(
                groupId = groupId,
                slug = slug,
                name = name,
                avatarUrl = avatarUrl,
                followerCount = followers,
                isMember = isMember,
                visibility = visibility,
            )
        }.distinctBy { it.groupId ?: it.slug }
    }

    private fun Element.attrOrNull(attr: String): String? =
        attr(attr).takeIf { it.isNotBlank() }
}
