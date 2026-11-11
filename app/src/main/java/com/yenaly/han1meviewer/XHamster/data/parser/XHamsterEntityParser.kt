package com.yenaly.han1meviewer.XHamster.data.parser

import com.yenaly.han1meviewer.XHamster.common.XHamsterConstants
import com.yenaly.han1meviewer.XHamster.common.XHamsterFormat
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterAccountSettings
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterChannel
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterCreator
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterDashboard
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterGallery
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterLink
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterListPage
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterOption
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterPage
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterProfile
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object XHamsterEntityParser {

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.anyStr(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.lng(key: String): Long? =
        (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

    private fun JsonObject.firstLng(vararg keys: String): Long? =
        keys.firstNotNullOfOrNull { lng(it) }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonObject.flag(vararg keys: String): Boolean = keys.any { k ->
        (this[k] as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.contentOrNull == "true") } == true
    }

    private fun JsonObject.link(): String? =
        str("pageURL") ?: str("pageUrl") ?: str("url") ?: str("link") ?: str("href")

    private fun JsonObject.label(): String? =
        str("name") ?: str("title") ?: str("label") ?: str("text")

    private fun JsonObject.image(): String? =
        str("thumbURL") ?: str("thumbUrl") ?: str("avatarURL") ?: str("avatarUrl")
            ?: str("logoURL") ?: str("logoUrl") ?: str("imageURL") ?: str("imageUrl")
            ?: str("previewURL") ?: str("thumb")

    private fun walk(el: JsonElement?, visit: (JsonObject) -> Unit) {
        when (el) {
            is JsonObject -> {
                visit(el)
                el.values.forEach { walk(it, visit) }
            }

            is JsonArray -> el.forEach { walk(it, visit) }
            else -> Unit
        }
    }

    private fun findObjectWithKey(el: JsonElement?, key: String): JsonObject? = when (el) {
        is JsonObject ->
            if (el.containsKey(key)) el
            else el.values.firstNotNullOfOrNull { findObjectWithKey(it, key) }

        is JsonArray -> el.firstNotNullOfOrNull { findObjectWithKey(it, key) }
        else -> null
    }

    private fun findStr(root: JsonElement?, vararg keys: String): String? {
        var result: String? = null
        walk(root) { o ->
            if (result == null) {
                for (k in keys) {
                    val v = o.anyStr(k)
                    if (v != null) {
                        result = v
                        break
                    }
                }
            }
        }
        return result
    }

    private fun findArray(el: JsonElement?, key: String): JsonArray? = when (el) {
        is JsonObject -> el.arr(key) ?: el.values.firstNotNullOfOrNull { findArray(it, key) }
        is JsonArray -> el.firstNotNullOfOrNull { findArray(it, key) }
        else -> null
    }

    private fun doc(html: String): Document = Jsoup.parse(html, XHamsterConstants.BASE_URL)

    private fun imgUrl(el: Element): String {
        val img = (if (el.tagName() == "img") el else el.selectFirst("img")) ?: return ""
        val raw = img.attr("data-src").ifBlank { img.attr("src") }
        return if (raw.startsWith("data:")) "" else raw
    }

    private fun pathOf(url: String): String =
        XHamsterConstants.relativePath(url).substringBefore('#')

    private fun nextUrl(root: JsonObject?, doc: Document, hasItems: Boolean): String? {
        if (!hasItems) return null
        val pagination = findObjectWithKey(root, "pageLinkTemplate")
        val template = pagination?.str("pageLinkTemplate")
        if (pagination != null && template != null) {
            val current = pagination.lng("currentPageNumber") ?: pagination.lng("active") ?: 1L
            val last = pagination.lng("lastPageNumber") ?: pagination.lng("maxPage")?.takeIf { it > 0 }
            if (last != null && current >= last) return null
            val next = (pagination.lng("next")?.takeIf { it > current } ?: (current + 1)).toString()
            return XHamsterConstants.absolute(
                template.replace("{#}", next).replace("_URL_PAGE_REPLACER_", next)
            )
        }
        val href = doc.selectFirst("link[rel=next]")?.attr("href")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("a[rel=next]")?.attr("href")?.takeIf { it.isNotBlank() }
        return href?.let { XHamsterConstants.absolute(it.replace("&amp;", "&")) }
    }

    private fun <T> buildPage(
        html: String,
        extract: (JsonObject?, Document) -> List<T>,
    ): XHamsterPage<T> {
        val root = XHamsterParser.extractInitials(html)
        val document = doc(html)
        val items = extract(root, document)
        return XHamsterPage(items, nextUrl(root, document, items.isNotEmpty()))
    }

    fun parseHtmlVideoList(html: String): XHamsterListPage {
        val document = doc(html)
        val out = LinkedHashMap<String, HanimeInfo>()
        document.select("a[href*=/videos/]").forEach { a ->
            val code = XHamsterConstants.codeFromUrl(a.attr("href")) ?: return@forEach
            if (out.containsKey(code)) return@forEach
            val img = a.selectFirst("img")
            val title = a.attr("title").ifBlank { img?.attr("alt").orEmpty() }
                .ifBlank { a.text() }.trim()
            if (title.isBlank()) return@forEach
            val scope = a.parent() ?: a
            val duration = (a.selectFirst("[class*=duration]") ?: scope.selectFirst("[class*=duration]"))
                ?.text()?.trim()?.takeIf { it.isNotBlank() }
            out[code] = HanimeInfo(
                title = title,
                coverUrl = img?.let { imgUrl(it) }.orEmpty(),
                videoCode = code,
                duration = duration,
                itemType = HanimeInfo.NORMAL,
            )
        }
        val items = out.values.toList()
        return XHamsterListPage(items, nextUrl(null, document, items.isNotEmpty()))
    }

    fun parseChannels(html: String): XHamsterPage<XHamsterChannel> = buildPage(html) { root, document ->
        val out = LinkedHashMap<String, XHamsterChannel>()
        walk(root) { o ->
            val url = o.link() ?: return@walk
            val path = pathOf(url)
            if (!path.startsWith("/channels/")) return@walk
            val name = o.label() ?: return@walk
            out.putIfAbsent(
                path,
                XHamsterChannel(
                    name = name,
                    path = path,
                    logoUrl = o.image().orEmpty(),
                    videoCount = o.firstLng("videos", "videosCount", "videoCount", "quantity"),
                    subscribers = o.obj("subscriptionModel")?.lng("subscribers")
                        ?: o.firstLng("subscribers", "subscribersCount"),
                ),
            )
        }
        if (out.isEmpty()) {
            document.select("a[href*=/channels/]").forEach { a ->
                val path = pathOf(a.attr("href"))
                if (!path.startsWith("/channels/") || out.containsKey(path)) return@forEach
                val name = a.attr("title").ifBlank { a.selectFirst("img")?.attr("alt").orEmpty() }
                    .ifBlank { a.text() }.trim()
                if (name.isBlank()) return@forEach
                out[path] = XHamsterChannel(name, path, imgUrl(a), null, null)
            }
        }
        out.values.toList()
    }

    fun parseCreators(html: String): XHamsterPage<XHamsterCreator> = buildPage(html) { root, document ->
        val out = LinkedHashMap<String, XHamsterCreator>()
        fun isCreatorPath(p: String) = p.startsWith("/creators/") || p.startsWith("/users/")
        walk(root) { o ->
            val url = o.link() ?: return@walk
            val path = pathOf(url)
            if (!isCreatorPath(path) || path.startsWith("/creators/videos")) return@walk
            val name = o.label() ?: return@walk
            out.putIfAbsent(
                path,
                XHamsterCreator(
                    name = name,
                    path = path,
                    avatarUrl = o.image().orEmpty(),
                    videoCount = o.firstLng("videos", "videosCount", "videoCount", "quantity"),
                    viewsCount = o.firstLng("views", "viewsCount"),
                    verified = o.flag("isVerified", "verified"),
                ),
            )
        }
        if (out.isEmpty()) {
            document.select("a[href*=/creators/], a[href*=/users/]").forEach { a ->
                val path = pathOf(a.attr("href"))
                if (!isCreatorPath(path) || path.startsWith("/creators/videos") || out.containsKey(path)) {
                    return@forEach
                }
                val name = a.attr("title").ifBlank { a.selectFirst("img")?.attr("alt").orEmpty() }
                    .ifBlank { a.text() }.trim()
                if (name.isBlank()) return@forEach
                out[path] = XHamsterCreator(name, path, imgUrl(a), null, null, false)
            }
        }
        out.values.toList()
    }

    fun parseGalleries(html: String): XHamsterPage<XHamsterGallery> = buildPage(html) { root, document ->
        val out = LinkedHashMap<String, XHamsterGallery>()
        walk(root) { o ->
            val url = o.link() ?: return@walk
            if (!url.contains("/photos/gallery/")) return@walk
            val title = o.label() ?: return@walk
            val id = o.anyStr("id") ?: url.trimEnd('/').substringAfterLast('/')
            val author = o.obj("author") ?: o.obj("user") ?: o.obj("publisher")
            out.putIfAbsent(
                id,
                XHamsterGallery(
                    id = id,
                    title = title,
                    url = XHamsterConstants.absolute(url),
                    coverUrl = o.image().orEmpty(),
                    photosCount = o.firstLng("quantity", "photosCount", "photos", "count"),
                    viewsText = XHamsterFormat.count(o.firstLng("views", "viewsCount")),
                    authorName = author?.label(),
                    authorPath = author?.link()?.let { pathOf(it) },
                ),
            )
        }
        if (out.isEmpty()) {
            document.select("a[href*=/photos/gallery/]").forEach { a ->
                val url = XHamsterConstants.absolute(a.attr("href"))
                val id = url.trimEnd('/').substringAfterLast('/')
                if (out.containsKey(id)) return@forEach
                val title = a.attr("title").ifBlank { a.selectFirst("img")?.attr("alt").orEmpty() }
                    .ifBlank { a.text() }.trim()
                if (title.isBlank()) return@forEach
                out[id] = XHamsterGallery(id, title, url, imgUrl(a), null, null, null, null)
            }
        }
        out.values.toList()
    }

    private fun optionFromJson(o: JsonObject): XHamsterOption? {
        val name = o.label() ?: return null
        val url = o.link() ?: o.str("path") ?: return null
        return XHamsterOption(
            name = name,
            path = pathOf(url),
            selected = o.flag("selected", "isSelected", "active", "isActive", "current"),
        )
    }

    private fun optionsFromLinks(
        document: Document,
        selector: String,
        accept: (String) -> Boolean,
    ): List<XHamsterOption> {
        val out = LinkedHashMap<String, XHamsterOption>()
        document.select(selector).forEach { a ->
            val path = pathOf(a.attr("href"))
            if (path.isBlank() || !accept(path) || out.containsKey(path)) return@forEach
            val name = a.attr("title").ifBlank { a.text() }.trim()
            if (name.isBlank()) return@forEach
            val cls = a.className() + " " + (a.parent()?.className() ?: "")
            val selected = a.attr("aria-current").isNotBlank() ||
                    cls.contains("active") || cls.contains("selected")
            out[path] = XHamsterOption(name, path, selected)
        }
        return out.values.toList()
    }

    fun parseCategories(html: String): List<XHamsterOption> {
        val out = LinkedHashMap<String, XHamsterOption>()
        walk(XHamsterParser.extractInitials(html)) { o ->
            val url = o.link() ?: return@walk
            val path = pathOf(url)
            if (!path.startsWith("/categories/")) return@walk
            val name = o.label() ?: return@walk
            out.putIfAbsent(path, XHamsterOption(name, path))
        }
        if (out.isNotEmpty()) return out.values.toList()
        return optionsFromLinks(doc(html), "a[href*=/categories/]") {
            it.startsWith("/categories/") && it.trimEnd('/').count { c -> c == '/' } == 2
        }
    }

    fun parsePhotoFilters(html: String): List<XHamsterOption> {
        fun accept(p: String) = p.startsWith("/photos") && !p.startsWith("/photos/gallery")
        val out = LinkedHashMap<String, XHamsterOption>()
        walk(XHamsterParser.extractInitials(html)) { o ->
            val opt = optionFromJson(o) ?: return@walk
            if (accept(opt.path)) out.putIfAbsent(opt.path, opt)
        }
        if (out.isNotEmpty()) return out.values.toList()
        return optionsFromLinks(doc(html), "a[href*=/photos]") { accept(it) }
    }

    fun parseTopCreatorOptions(html: String, kind: String): List<XHamsterOption> {
        val fromJson = findArray(XHamsterParser.extractInitials(html), kind)
            ?.mapNotNull { (it as? JsonObject)?.let { o -> optionFromJson(o) } }
            .orEmpty()
        if (fromJson.isNotEmpty()) return fromJson.distinctBy { it.path }
        return optionsFromLinks(doc(html), "a[href*=/creators/videos]") {
            it.startsWith("/creators/videos")
        }
    }

    fun parseProfile(html: String): XHamsterProfile? {
        val root = XHamsterParser.extractInitials(html)
        val document = doc(html)

        val entity = findObjectWithKey(root, "userEntity")?.obj("userEntity")
            ?: findObjectWithKey(root, "profileUser")?.obj("profileUser")
            ?: findObjectWithKey(root, "userModel")?.obj("userModel")
        val ownId = root?.lng("userId")?.takeIf { it > 0L }
        val id = entity?.lng("id") ?: entity?.lng("userId") ?: ownId

        val name = entity?.label() ?: entity?.str("username") ?: entity?.str("userName")
        ?: findStr(entity ?: root, "displayName", "username", "userName")
        ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() }
        ?: document.selectFirst("h1")?.text()?.takeIf { it.isNotBlank() }
        ?: return null

        val avatar = entity?.image()
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() }

        val counters = entity?.obj("counters") ?: entity?.obj("counts")
        fun total(vararg keys: String): Long? =
            counters?.firstLng(*keys) ?: entity?.firstLng(*keys)

        val channels = LinkedHashMap<String, XHamsterChannel>()
        walk(root) { o ->
            val url = o.link() ?: return@walk
            val path = pathOf(url)
            if (!path.startsWith("/channels/")) return@walk
            val chName = o.label() ?: return@walk
            channels.putIfAbsent(
                path,
                XHamsterChannel(
                    name = chName,
                    path = path,
                    logoUrl = o.image().orEmpty(),
                    videoCount = o.firstLng("videos", "videosCount", "videoCount"),
                    subscribers = o.obj("subscriptionModel")?.lng("subscribers")
                        ?: o.firstLng("subscribers", "subscribersCount"),
                ),
            )
        }

        val isMine = entity?.flag("isMine", "isOwner", "isMyProfile") == true ||
                (id != null && ownId != null && id == ownId)
        val isPublic = !(entity?.flag("isPrivate", "isClosed") ?: false) &&
                (entity?.get("isPublic")?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: true)

        return XHamsterProfile(
            id = id ?: 0L,
            name = name,
            avatarUrl = avatar,
            isMine = isMine,
            isPublic = isPublic,
            videosTotal = total("videos", "videosCount", "videoCount"),
            momentsTotal = total("moments", "momentsCount"),
            friendsTotal = total("friends", "friendsCount"),
            favoritesTotal = total("favorites", "favoritesCount"),
            channels = channels.values.toList(),
            videos = XHamsterParser.parseListPage(html).items,
        )
    }

    fun parseDashboard(html: String): XHamsterDashboard? {
        val root = XHamsterParser.extractInitials(html) ?: return null
        val menu = LinkedHashMap<String, XHamsterLink>()
        (findArray(root, "menu") ?: findArray(root, "menuItems") ?: findArray(root, "navigation"))
            ?.forEach { el ->
                val o = el as? JsonObject ?: return@forEach
                val title = o.label() ?: return@forEach
                val url = o.link() ?: return@forEach
                menu.putIfAbsent(url, XHamsterLink(title, pathOf(url)))
            }
        return XHamsterDashboard(
            balance = findStr(root, "balance", "availableBalance"),
            pending = findStr(root, "pending", "pendingBalance", "pendingAmount"),
            premiumRevenue = findStr(root, "premiumRevenue", "premium"),
            adShareEnabled = findObjectWithKey(root, "adShareEnabled")?.flag("adShareEnabled") == true ||
                    findObjectWithKey(root, "isAdShareEnabled")?.flag("isAdShareEnabled") == true,
            canUpload = findObjectWithKey(root, "canUpload")?.flag("canUpload") == true ||
                    findObjectWithKey(root, "isUploadAllowed")?.flag("isUploadAllowed") == true,
            menu = menu.values.toList(),
        )
    }

    private fun humanize(key: String): String =
        key.replace(Regex("([a-z])([A-Z])"), "\$1 \$2").replace('_', ' ')
            .replaceFirstChar { it.uppercase() }

    private fun selectedOptions(document: Document): List<XHamsterLink> =
        document.select("select").mapNotNull { sel ->
            val value = (sel.selectFirst("option[selected]") ?: sel.selectFirst("option"))
                ?.text()?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val label = sel.id().takeIf { it.isNotBlank() }
                ?.let { id -> document.select("label").firstOrNull { it.attr("for") == id }?.text() }
                ?.trim()?.takeIf { it.isNotBlank() }
                ?: sel.attr("name").takeIf { it.isNotBlank() }?.let { humanize(it) }
                ?: return@mapNotNull null
            XHamsterLink(label, value)
        }

    private fun selectValue(document: Document?, vararg nameParts: String): String? {
        if (document == null) return null
        return document.select("select").firstOrNull { sel ->
            nameParts.any { sel.attr("name").contains(it, ignoreCase = true) }
        }?.let { sel ->
            (sel.selectFirst("option[selected]") ?: sel.selectFirst("option"))
                ?.text()?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    fun parseAccountSettings(profileHtml: String?, privacyHtml: String?): XHamsterAccountSettings {
        val profileRoot = profileHtml?.let { XHamsterParser.extractInitials(it) }
        val privacyRoot = privacyHtml?.let { XHamsterParser.extractInitials(it) }
        val profileDoc = profileHtml?.let { doc(it) }
        val privacyDoc = privacyHtml?.let { doc(it) }

        val displayName = findStr(profileRoot, "displayName", "display_name", "nickName")
            ?: profileDoc?.select("input")?.firstOrNull {
                it.attr("name").contains("display", true) || it.attr("name").contains("nick", true)
            }?.attr("value")?.takeIf { it.isNotBlank() }
        val email = findStr(profileRoot, "email")
            ?: profileDoc?.selectFirst("input[type=email], input[name=email]")?.attr("value")
                ?.takeIf { it.isNotBlank() }
        val emailVerified = findObjectWithKey(profileRoot, "emailVerified")?.flag("emailVerified") == true ||
                findObjectWithKey(profileRoot, "isEmailVerified")?.flag("isEmailVerified") == true

        val privacy = ArrayList<XHamsterLink>()
        walk(privacyRoot) { o ->
            val block = o["privacySettings"] ?: o["privacy"] ?: return@walk
            when (block) {
                is JsonObject -> block.forEach { (k, v) ->
                    val value = (v as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                    if (value != null) privacy += XHamsterLink(humanize(k), value)
                }

                is JsonArray -> block.forEach { el ->
                    val item = el as? JsonObject ?: return@forEach
                    val title = item.label() ?: return@forEach
                    val value = item.anyStr("value") ?: item.anyStr("selected") ?: return@forEach
                    privacy += XHamsterLink(title, value)
                }

                else -> Unit
            }
        }
        if (privacy.isEmpty() && privacyDoc != null) privacy += selectedOptions(privacyDoc)

        return XHamsterAccountSettings(
            displayName = displayName,
            email = email,
            emailVerified = emailVerified,
            gender = findStr(profileRoot, "gender") ?: selectValue(profileDoc, "gender"),
            country = findStr(profileRoot, "country", "countryName") ?: selectValue(profileDoc, "country"),
            relation = findStr(profileRoot, "relation", "relationship")
                ?: selectValue(profileDoc, "relation"),
            profileVisibility = findStr(privacyRoot, "profileVisibility", "profileAccess")
                ?: selectValue(privacyDoc, "visib", "profile"),
            privacy = privacy.distinctBy { it.title },
        )
    }
}
