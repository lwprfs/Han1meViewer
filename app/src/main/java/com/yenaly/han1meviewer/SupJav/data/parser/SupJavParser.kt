package com.yenaly.han1meviewer.SupJav.data.parser

import com.yenaly.han1meviewer.SupJav.common.SupJavConstants
import com.yenaly.han1meviewer.SupJav.data.model.SupJavDetail
import com.yenaly.han1meviewer.SupJav.data.model.SupJavLink
import com.yenaly.han1meviewer.SupJav.data.model.SupJavListPage
import com.yenaly.han1meviewer.SupJav.data.model.SupJavServer
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object SupJavParser {

    private val CODE_REGEX = Regex("""/(\d+)\.html""")

    fun extractCode(url: String): String? = CODE_REGEX.find(url)?.groupValues?.get(1)

    fun parseListPage(html: String): SupJavListPage {
        val doc = Jsoup.parse(html, SupJavConstants.BASE_URL)
        val items = doc.select("div.posts > div.post").mapNotNull(::parseCard)
        val hasNext = doc.selectFirst("div.pagination li.next-page") != null
        return SupJavListPage(items = items.distinctBy { it.videoCode }, hasNext = hasNext)
    }

    private fun parseCard(el: Element): HanimeInfo? {
        val link = el.selectFirst("a.img") ?: el.selectFirst("a[href]") ?: return null
        val code = extractCode(link.absUrl("href")) ?: return null
        val img = link.selectFirst("img")
        val title = img?.attr("alt").orEmpty().ifBlank { link.attr("title") }
            .ifBlank { el.selectFirst("h3")?.text().orEmpty() }
        val cover = img?.absUrl("data-original").orEmpty().ifBlank {
            img?.absUrl("src").orEmpty().takeUnless { it.startsWith("data:") }.orEmpty()
        }
        return HanimeInfo(
            title = title,
            coverUrl = cover,
            videoCode = code,
            itemType = HanimeInfo.NORMAL,
        )
    }

    fun parseDetail(html: String, code: String): SupJavDetail? {
        val doc = Jsoup.parse(html, SupJavConstants.BASE_URL)
        val meta = doc.selectFirst("div.content > div.post-meta")
            ?: doc.selectFirst("div.post-meta")
            ?: return null
        val title = meta.selectFirst("h2")?.text().orEmpty()
        if (title.isBlank()) return null
        val cover = meta.selectFirst("img")?.absUrl("src").orEmpty()

        fun links(scope: Element?): List<SupJavLink> =
            scope?.select("a")?.map { SupJavLink(it.text().trim(), it.absUrl("href")) }
                ?.filter { it.title.isNotBlank() }.orEmpty()

        val cats = meta.selectFirst("div.cats")
        val category = cats?.selectFirst("p.cat a")?.let {
            SupJavLink(it.text().trim(), it.absUrl("href"))
        }
        fun rowFor(label: String): List<SupJavLink> = links(
            cats?.select("p")?.firstOrNull {
                it.selectFirst("span")?.text()?.trim()?.startsWith(label, ignoreCase = true) == true
            }
        )

        val servers = doc.select("div.btnst > a")
            .map { SupJavServer(it.text().trim(), it.attr("data-link").trim().reversed()) }
            .filter { it.name.isNotBlank() && it.token.isNotBlank() }

        val related = doc.select("div.posts > div.post").mapNotNull(::parseCard)
            .filter { it.videoCode != code }

        return SupJavDetail(
            code = code,
            title = title,
            coverUrl = cover,
            pageUrl = SupJavConstants.videoUrl(code),
            category = category,
            makers = rowFor("Maker"),
            cast = rowFor("Cast"),
            tags = links(meta.selectFirst("div.tags")),
            servers = servers,
            related = related.distinctBy { it.videoCode },
        )
    }
}
