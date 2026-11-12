package com.yenaly.han1meviewer.XHamster.data.parser

import com.yenaly.han1meviewer.XHamster.common.XHamsterConstants
import com.yenaly.han1meviewer.XHamster.common.XHamsterFormat
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterAccountSettings
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterChannel
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterCreator
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterDashboard
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterCommentsPage
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterEntityProfile
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterGallery
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterGalleryInfo
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterGalleryPage
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterPhoto
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterPhotoDetail
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterProfileTab
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterShort
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterShortsPage
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterLink
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterListPage
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterOption
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterPage
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterProfile
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
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

    private fun JsonObject.firstStr(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { str(it) }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonObject.boolOrNull(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() }

    private fun JsonObject.flag(vararg keys: String): Boolean = keys.any { boolOrNull(it) == true }

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

    private fun findArray(el: JsonElement?, key: String): JsonArray? = when (el) {
        is JsonObject -> el.arr(key) ?: el.values.firstNotNullOfOrNull { findArray(it, key) }
        is JsonArray -> el.firstNotNullOfOrNull { findArray(it, key) }
        else -> null
    }

    private fun findObjectWithKey(el: JsonElement?, key: String): JsonObject? = when (el) {
        is JsonObject ->
            if (el.containsKey(key)) el
            else el.values.firstNotNullOfOrNull { findObjectWithKey(it, key) }

        is JsonArray -> el.firstNotNullOfOrNull { findObjectWithKey(it, key) }
        else -> null
    }

    private fun humanize(key: String): String =
        key.replace(Regex("([a-z])([A-Z])"), "\$1 \$2").replace('_', ' ')
            .replaceFirstChar { it.uppercase() }

    private fun pathOf(url: String): String =
        XHamsterConstants.relativePath(url).substringBefore('#')

    private fun doc(html: String): Document = Jsoup.parse(html, XHamsterConstants.BASE_URL)

    private fun imgUrl(el: Element): String {
        val img = (if (el.tagName() == "img") el else el.selectFirst("img")) ?: return ""
        val raw = img.attr("data-src").ifBlank { img.attr("src") }
        return if (raw.startsWith("data:")) "" else raw
    }

    private fun nextUrl(root: JsonObject?, document: Document, hasItems: Boolean): String? {
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
        val href = document.selectFirst("link[rel=next]")?.attr("href")?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("a[rel=next]")?.attr("href")?.takeIf { it.isNotBlank() }
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

        fun add(link: Element, scope: Element) {
            val code = XHamsterConstants.codeFromUrl(link.attr("href")) ?: return
            if (out.containsKey(code)) return
            val img = scope.selectFirst("img")
            val title = scope.selectFirst(".mobile-video-thumb__name")?.text()
                ?: link.attr("aria-label").ifBlank { null }
                ?: img?.attr("alt")?.ifBlank { null }
                ?: link.attr("title").ifBlank { null }
                ?: link.text()
            if (title.isBlank()) return
            out[code] = HanimeInfo(
                title = title.trim(),
                coverUrl = img?.let { imgUrl(it) }.orEmpty(),
                videoCode = code,
                duration = scope.selectFirst(".time time, time")?.text()?.trim()
                    ?.takeIf { it.isNotBlank() },
                views = scope.selectFirst(".video-thumb-views")?.text()
                    ?.removeSuffix("views")?.trim()?.takeIf { it.isNotBlank() },
                itemType = HanimeInfo.NORMAL,
            )
        }

        val cards = document.select("[data-role=mobile-video-thumb], [data-video-type]")
        if (cards.isNotEmpty()) {
            cards.forEach { card ->
                if (card.attr("data-video-type").let { it.isNotBlank() && it != "video" }) return@forEach
                val link = card.selectFirst("a[href*=/videos/]") ?: return@forEach
                add(link, card)
            }
        } else {
            document.select("a[href*=/videos/]").forEach { a -> add(a, a.parent() ?: a) }
        }
        val items = out.values.toList()
        return XHamsterListPage(items, nextUrl(null, document, items.isNotEmpty()))
    }

    private fun channelFrom(o: JsonObject): XHamsterChannel? {
        val url = o.str("channelURL") ?: return null
        val name = o.str("channelName") ?: return null
        return XHamsterChannel(
            name = name,
            path = pathOf(url),
            logoUrl = o.firstStr("siteLogoURL", "thumbURL").orEmpty(),
            videoCount = o.lng("videoCount"),
            subscribers = o.obj("subscriptionModel")?.lng("subscribers"),
        )
    }

    private fun channelsFrom(el: JsonElement?): List<XHamsterChannel> {
        val out = LinkedHashMap<String, XHamsterChannel>()
        walk(el) { o -> channelFrom(o)?.let { out.putIfAbsent(it.path, it) } }
        return out.values.toList()
    }

    fun parseChannels(html: String): XHamsterPage<XHamsterChannel> = buildPage(html) { root, document ->
        val items = channelsFrom(root?.obj("layoutPage")?.arr("channels")).ifEmpty { channelsFrom(root) }
        if (items.isNotEmpty()) return@buildPage items
        val out = LinkedHashMap<String, XHamsterChannel>()
        document.select("a[href*=/channels/]").forEach { a ->
            val path = pathOf(a.attr("href"))
            if (!Regex("^/channels/[^/]+$").matches(path) || path == "/channels/all") return@forEach
            if (out.containsKey(path)) return@forEach
            val name = a.attr("title").ifBlank { a.selectFirst("img")?.attr("alt").orEmpty() }
                .ifBlank { a.text() }.trim()
            if (name.isBlank()) return@forEach
            out[path] = XHamsterChannel(name, path, imgUrl(a), null, null)
        }
        out.values.toList()
    }

    private fun creatorFrom(o: JsonObject): XHamsterCreator? {
        val url = o.str("pageURL") ?: return null
        val path = pathOf(url)
        if (!path.startsWith("/creators/") && !path.startsWith("/pornstars/")) return null
        if (!o.containsKey("videoCount")) return null
        val name = o.str("name") ?: return null
        return XHamsterCreator(
            name = name,
            path = path,
            avatarUrl = o.firstStr("imageUserAvatarUrl", "logoThumbUrl", "imageThumbUrl").orEmpty(),
            videoCount = o.lng("videoCount"),
            viewsCount = o.lng("viewsCount"),
            verified = o.flag("isVerified"),
        )
    }

    fun parseCreators(html: String): XHamsterPage<XHamsterCreator> = buildPage(html) { root, document ->
        fun scan(el: JsonElement?): List<XHamsterCreator> {
            val out = LinkedHashMap<String, XHamsterCreator>()
            walk(el) { o -> creatorFrom(o)?.let { out.putIfAbsent(it.path, it) } }
            return out.values.toList()
        }

        val items = scan(root?.obj("layoutPage")?.obj("pornstarListProps")).ifEmpty { scan(root) }
        if (items.isNotEmpty()) return@buildPage items
        val out = LinkedHashMap<String, XHamsterCreator>()
        document.select("a[href*=/creators/]").forEach { a ->
            val path = pathOf(a.attr("href"))
            if (!Regex("^/creators/[^/]+$").matches(path) ||
                path in setOf("/creators/all", "/creators/videos", "/creators/contest")
            ) return@forEach
            if (out.containsKey(path)) return@forEach
            val name = a.attr("title").ifBlank { a.selectFirst("img")?.attr("alt").orEmpty() }
                .ifBlank { a.text() }.trim()
            if (name.isBlank()) return@forEach
            out[path] = XHamsterCreator(name, path, imgUrl(a), null, null, false)
        }
        out.values.toList()
    }

    private fun galleryFrom(o: JsonObject): XHamsterGallery? {
        val url = o.str("link") ?: return null
        if (!url.contains("/photos/gallery/")) return null
        val title = o.str("title") ?: return null
        val author = o.obj("author")
        return XHamsterGallery(
            id = o.anyStr("id") ?: url.trimEnd('/').substringAfterLast('/'),
            title = title,
            url = XHamsterConstants.absolute(url),
            coverUrl = o.firstStr("imgSrc", "imgSrcset").orEmpty(),
            photosCount = o.lng("photosCount"),
            viewsText = o.str("viewsText"),
            authorName = author?.str("name"),
            authorPath = author?.str("link")?.let { pathOf(it) },
        )
    }

    fun parseGalleries(html: String): XHamsterPage<XHamsterGallery> = buildPage(html) { root, document ->
        fun scan(el: JsonElement?): List<XHamsterGallery> {
            val out = LinkedHashMap<String, XHamsterGallery>()
            walk(el) { o -> galleryFrom(o)?.let { out.putIfAbsent(it.id, it) } }
            return out.values.toList()
        }

        val items = scan(root?.obj("photosPage")?.arr("galleriesItems")).ifEmpty { scan(root) }
        if (items.isNotEmpty()) return@buildPage items
        val out = LinkedHashMap<String, XHamsterGallery>()
        document.select("a[href*=/photos/gallery/]").forEach { a ->
            val url = XHamsterConstants.absolute(a.attr("href"))
            val id = url.trimEnd('/').substringAfterLast('/')
            if (out.containsKey(id)) return@forEach
            val title = a.attr("title").ifBlank { a.selectFirst("img")?.attr("alt").orEmpty() }
                .ifBlank { a.text() }.trim()
            if (title.isBlank()) return@forEach
            out[id] = XHamsterGallery(id, title, url, imgUrl(a), null, null, null, null)
        }
        out.values.toList()
    }

    fun parseCategories(html: String): List<XHamsterOption> {
        val out = LinkedHashMap<String, XHamsterOption>()
        val single = Regex("^/categories/[^/?#]+$")
        walk(XHamsterParser.extractInitials(html)) { o ->
            if (o.flag("isLanguageGroup")) return@walk
            val url = o.str("url") ?: return@walk
            val path = pathOf(url)
            if (!single.matches(path)) return@walk
            val name = o.str("name") ?: return@walk
            out.putIfAbsent(path, XHamsterOption(name, path))
        }
        if (out.isNotEmpty()) return out.values.toList()
        val seen = LinkedHashMap<String, XHamsterOption>()
        doc(html).select("a[href*=/categories/]").forEach { a ->
            val path = pathOf(a.attr("href"))
            if (!single.matches(path) || seen.containsKey(path)) return@forEach
            val name = a.attr("title").ifBlank { a.text() }.trim()
            if (name.isNotBlank()) seen[path] = XHamsterOption(name, path)
        }
        return seen.values.toList()
    }

    fun parsePhotoFilters(html: String): List<XHamsterOption> {
        val root = XHamsterParser.extractInitials(html)
        val out = LinkedHashMap<String, XHamsterOption>()
        fun accept(p: String) = p.startsWith("/photos") && !p.startsWith("/photos/gallery")

        val tabs = findObjectWithKey(root, "tabSetProps")?.obj("tabSetProps")?.arr("items")
        tabs?.forEach { el ->
            val tab = el as? JsonObject ?: return@forEach
            val link = tab.obj("link") ?: return@forEach
            val name = link.str("text")?.trim() ?: return@forEach
            val url = link.str("link") ?: return@forEach
            val path = pathOf(url)
            if (accept(path)) out.putIfAbsent(path, XHamsterOption(name, path, tab.flag("isSelected")))
        }
        val urls = findObjectWithKey(root, "filtersProps")?.obj("filtersProps")?.obj("urls")
        urls?.forEach { (key, value) ->
            val url = (value as? JsonPrimitive)?.contentOrNull ?: return@forEach
            val path = pathOf(url)
            if (accept(path)) {
                out.putIfAbsent(path, XHamsterOption(humanize(key.removePrefix("photos")), path))
            }
        }
        if (out.isNotEmpty()) return out.values.toList()

        val seen = LinkedHashMap<String, XHamsterOption>()
        doc(html).select("a[href*=/photos]").forEach { a ->
            val path = pathOf(a.attr("href"))
            if (!accept(path) || seen.containsKey(path)) return@forEach
            val name = a.attr("title").ifBlank { a.text() }.trim()
            if (name.isNotBlank()) seen[path] = XHamsterOption(name, path)
        }
        return seen.values.toList()
    }

    fun parseTopCreatorOptions(html: String, kind: String): List<XHamsterOption> {
        val list = findArray(XHamsterParser.extractInitials(html), kind) ?: return emptyList()
        return list.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val name = o.str("name") ?: return@mapNotNull null
            val url = o.str("value") ?: o.str("url") ?: return@mapNotNull null
            XHamsterOption(name, pathOf(url), o.flag("selected"))
        }.distinctBy { it.path }
    }

    fun parseProfile(html: String): XHamsterProfile? {
        val root = XHamsterParser.extractInitials(html)
        val document = doc(html)
        val layout = root?.obj("layoutPage")

        val user = layout?.obj("userModel")
            ?: root?.obj("displayUserModel")
            ?: findObjectWithKey(root, "userModel")?.obj("userModel")

        val name = user?.firstStr("displayName", "name")
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("h1")?.text()?.takeIf { it.isNotBlank() }
            ?: return null

        val id = user?.lng("id") ?: 0L
        val ownId = root?.lng("userId")
        val isMine = layout?.flag("isMyProfile") == true || root?.flag("isMyProfile") == true ||
                (id != 0L && ownId != null && id == ownId)

        val videos = XHamsterParser.parseCards(
            layout?.obj("videoListProps")?.arr("videoList")
        ).ifEmpty { XHamsterParser.parseListPage(html).items }

        return XHamsterProfile(
            id = id,
            name = name,
            avatarUrl = user?.str("thumbURL")
                ?: document.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() },
            isMine = isMine,
            isPublic = user?.boolOrNull("isPublicProfile") ?: true,
            videosTotal = layout?.lng("videosTotal"),
            momentsTotal = layout?.lng("momentsTotal"),
            friendsTotal = layout?.lng("friendsTotal"),
            favoritesTotal = layout?.lng("favoriteVideosTotal"),
            channels = channelsFrom(layout?.arr("channels")),
            videos = videos,
        )
    }

    fun parseDashboard(html: String): XHamsterDashboard? {
        val root = XHamsterParser.extractInitials(html) ?: return null
        val layout = root.obj("layoutPage")
        val dash = root.obj("dashboard") ?: layout?.obj("dashboardEntity") ?: return null
        val summary = dash.obj("summary")
        val adShare = summary?.obj("adShare")
        val premium = summary?.obj("premium")

        val menu = LinkedHashMap<String, XHamsterLink>()
        (root.arr("creatorMenuList") ?: layout?.arr("creatorMenuList"))?.forEach { el ->
            val o = el as? JsonObject ?: return@forEach
            val title = o.str("text") ?: return@forEach
            val href = o.str("href") ?: return@forEach
            menu.putIfAbsent(href, XHamsterLink(title, pathOf(href)))
        }

        return XHamsterDashboard(
            balance = adShare?.anyStr("balance"),
            pending = adShare?.anyStr("pending"),
            premiumRevenue = premium?.anyStr("monthlyRevenue"),
            adShareEnabled = adShare?.flag("enabled") == true,
            canUpload = dash.flag("canUpload"),
            menu = menu.values.toList(),
        )
    }

    private val PRIVACY_TITLES = mapOf(
        "pm" to "Private messages",
        "promo" to "Promotional messages",
        "performerCollaboration" to "Collaboration requests",
        "acceptProfileComments" to "Profile comments",
        "acceptContentComments" to "Content comments",
    )

    fun parseAccountSettings(profileHtml: String?, privacyHtml: String?): XHamsterAccountSettings {
        val profileRoot = profileHtml?.let { XHamsterParser.extractInitials(it) }
        val privacyRoot = privacyHtml?.let { XHamsterParser.extractInitials(it) }

        val user = profileRoot?.obj("displayUserModel")
        val personal = user?.obj("personalInfo")
        val emailBox = profileRoot?.obj("editEmailContainerComponent")

        val privacy = ArrayList<XHamsterLink>()
        val settings = privacyRoot?.obj("userPrivacySettings")
        settings?.forEach { (key, value) ->
            val current = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@forEach
            val options = settings.arr(key + "List")
            if (options == null && key !in PRIVACY_TITLES) return@forEach
            val text = options?.firstNotNullOfOrNull { el ->
                (el as? JsonObject)?.takeIf { it.str("value") == current }?.str("text")
            } ?: humanize(current)
            privacy += XHamsterLink(PRIVACY_TITLES[key] ?: humanize(key), text)
        }

        return XHamsterAccountSettings(
            displayName = user?.firstStr("displayName", "name"),
            email = emailBox?.str("mainEmail") ?: user?.str("email"),
            emailVerified = emailBox?.flag("isEmailVerified") == true || user?.flag("mailVerified") == true,
            gender = personal?.obj("gender")?.str("label"),
            country = personal?.obj("geo")?.str("countryName"),
            relation = personal?.obj("relations")?.str("label"),
            profileVisibility = user?.boolOrNull("isPublicProfile")?.let { if (it) "Public" else "Private" },
            privacy = privacy,
        )
    }

    private fun photoFrom(o: JsonObject?): XHamsterPhoto? {
        o ?: return null
        val id = o.lng("id") ?: return null
        val full = o.str("imgSrc") ?: o.str("imgSrcset")?.substringBefore(' ')?.substringBefore(',')
        ?: return null
        val thumb = o.str("imgSrcset")?.substringBefore(' ')?.substringBefore(',') ?: full
        return XHamsterPhoto(
            id = id,
            thumbUrl = thumb,
            fullUrl = full,
            width = (o.lng("originWidth") ?: 0L).toInt(),
            height = (o.lng("originHeight") ?: 0L).toInt(),
            commentsCount = (o.lng("commentsCount") ?: 0L).toInt(),
            pageUrl = o.str("link") ?: "",
            alt = o.str("alt").orEmpty(),
        )
    }

    fun parseGalleryPage(html: String): XHamsterGalleryPage? {
        val root = XHamsterParser.extractInitials(html) ?: return null
        val g = root.obj("galleryPage") ?: return null
        val photos = g.arr("photoItems").orEmpty()
            .mapNotNull { photoFrom(it as? JsonObject) }
            .distinctBy { it.id }
        val info = g.obj("infoProps")
        val author = info?.obj("authorInfoProps")
        val tags = info?.arr("categoriesTags").orEmpty().mapNotNull { el ->
            val t = el as? JsonObject ?: return@mapNotNull null
            val label = t.str("label") ?: return@mapNotNull null
            val href = t.str("href") ?: return@mapNotNull null
            com.yenaly.han1meviewer.XHamster.data.model.XHamsterLink(label, pathOf(href))
        }
        val document = doc(html)
        val title = g.str("title")
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: return null
        val galleryInfo = XHamsterGalleryInfo(
            id = g.lng("id") ?: 0L,
            title = title,
            description = info?.str("description"),
            pageUrl = g.obj("paginationProps")?.str("pageLinkFirst")
                ?: document.selectFirst("link[rel=canonical]")?.attr("href").orEmpty(),
            photosTotal = g.lng("photosCount"),
            views = info?.obj("viewsInfoProps")?.lng("views"),
            authorName = author?.str("authorName"),
            authorPath = author?.str("authorLink")?.let { pathOf(it) },
            createdSec = author?.lng("createdDate"),
            tags = tags,
        )
        return XHamsterGalleryPage(
            info = galleryInfo,
            photos = photos,
            nextUrl = nextUrl(root, document, photos.isNotEmpty()),
        )
    }

    fun parsePhotoPage(html: String): XHamsterPhotoDetail? {
        val root = XHamsterParser.extractInitials(html) ?: return null
        val layout = root.obj("layoutPage") ?: return null
        val details = layout.obj("photoDetails")
        val list = layout.obj("commentsProps")?.obj("commentsList")
        val counters = list?.obj("counters")
        val comments = XHamsterParser.parseCommentList(list?.arr("items"))
        val total = counters?.lng("total") ?: details?.lng("commentsCount") ?: comments.size.toLong()
        val pages = (counters?.lng("pages") ?: 1L).toInt().coerceAtLeast(1)
        val rating = details?.obj("rating")
        return XHamsterPhotoDetail(
            photoId = details?.lng("id") ?: 0L,
            likes = rating?.lng("likes"),
            dislikes = rating?.lng("dislikes"),
            views = details?.lng("views"),
            commentsTotal = total,
            comments = XHamsterCommentsPage(
                items = comments,
                total = total,
                page = 1,
                maxPages = pages,
                writeDenied = list?.get("denyWriteComment")?.let { it !is JsonNull } == true,
            ),
        )
    }

    private fun shortFrom(o: JsonObject): XHamsterShort? {
        val page = o.str("pageURL") ?: return null
        if (!page.contains("/shorts/")) return null
        val video = o.str("trailerURL") ?: return null
        return XHamsterShort(
            id = o.lng("id") ?: page.hashCode().toLong(),
            title = o.str("title").orEmpty(),
            pageUrl = page,
            videoUrl = video,
            coverUrl = o.firstStr("imageURL", "thumbURL").orEmpty(),
            views = o.lng("views"),
        )
    }

    private fun shortsFrom(list: JsonArray?): List<XHamsterShort> =
        list.orEmpty().mapNotNull { (it as? JsonObject)?.let(::shortFrom) }

    private fun collectShorts(root: JsonObject?): List<XHamsterShort> {
        val primary = shortsFrom(
            root?.obj("momentsComponent")?.obj("videoListProps")?.arr("videoThumbProps")
        ).ifEmpty { shortsFrom(root?.obj("momentsListComponent")?.arr("videoThumbProps")) }
        if (primary.isNotEmpty()) return primary.distinctBy { it.id }
        val out = LinkedHashMap<Long, XHamsterShort>()
        walk(root) { o -> shortFrom(o)?.let { out.putIfAbsent(it.id, it) } }
        return out.values.toList()
    }

    fun parseShortsPage(html: String): XHamsterShortsPage {
        val root = XHamsterParser.extractInitials(html)
        val items = collectShorts(root)
        return XHamsterShortsPage(items, nextUrl(root, doc(html), items.isNotEmpty()))
    }

    private fun parseTabs(root: JsonObject?, basePath: String): List<XHamsterProfileTab> {
        val tabs = (root?.obj("uiTabSetComponent") ?: root?.obj("layoutPage")?.obj("uiTabSetComponent"))
            ?.arr("items").orEmpty()
        return tabs.mapNotNull { el ->
            val tab = el as? JsonObject ?: return@mapNotNull null
            val link = tab.obj("link") ?: return@mapNotNull null
            val text = link.str("text")?.trim() ?: return@mapNotNull null
            val url = link.str("link") ?: return@mapNotNull null
            if (url.contains("/fh/out") || url.contains("faphouse", ignoreCase = true)) return@mapNotNull null
            val path = pathOf(url).ifBlank { basePath }
            XHamsterProfileTab(text, path, tab.lng("counter"), tab.flag("isSelected"))
        }
    }

    private fun parseCreatorProfile(html: String, path: String): XHamsterEntityProfile? {
        val root = XHamsterParser.extractInitials(html) ?: return null
        val info = root.obj("infoComponent") ?: return null
        val top = info.obj("pornstarTop") ?: return null
        val name = top.str("name") ?: info.str("pageTitle") ?: return null
        val sub = info.obj("subscribeButtonsProps")?.obj("subscribeButtonProps")
        val tabs = parseTabs(root, path)
        val shortsTab = tabs.firstOrNull { it.path.endsWith("/shorts") }
        val videosTab = tabs.firstOrNull { !it.path.endsWith("/shorts") }
        val videos = XHamsterParser.parseCards(
            root.obj("newestVideoSectionComponent")?.obj("videoListProps")?.arr("videoThumbProps")
        ).ifEmpty {
            XHamsterParser.parseCards(
                root.obj("trendingVideoSectionComponent")?.obj("videoListProps")?.arr("videoThumbProps")
            )
        }
        val profileFlags = top.obj("profile")
        return XHamsterEntityProfile(
            path = path,
            kind = XHamsterEntityProfile.Kind.CREATOR,
            entityId = root.obj("channelInfo")?.lng("id") ?: sub?.lng("id") ?: 0L,
            name = name,
            avatarUrl = top.str("thumbUrl"),
            country = top.str("translatedCountryName"),
            verified = profileFlags?.flag("isVerified") == true,
            isPublic = true,
            isMine = false,
            videoCount = top.lng("videoCount") ?: videosTab?.count,
            shortsCount = shortsTab?.count,
            photosCount = null,
            viewsCount = top.lng("viewsCount"),
            favoritesCount = null,
            subscribers = sub?.lng("subscribers"),
            subscribed = sub?.flag("subscribed") == true,
            subscriptionModel = sub?.str("modelName"),
            aliases = info.str("aliases"),
            channels = emptyList(),
            tabs = tabs,
            videos = videos,
            shorts = collectShorts(root),
        )
    }

    fun parseEntityProfile(html: String, path: String): XHamsterEntityProfile? {
        parseCreatorProfile(html, path)?.let { return it }
        val user = parseProfile(html) ?: return null
        val root = XHamsterParser.extractInitials(html)
        val tabs = parseTabs(root, path).ifEmpty {
            buildList {
                add(XHamsterProfileTab("Videos", "$path/videos", user.videosTotal, true))
                if ((user.momentsTotal ?: 0L) > 0L) {
                    add(XHamsterProfileTab("Short videos", "$path/shorts", user.momentsTotal))
                }
            }
        }
        return XHamsterEntityProfile(
            path = path,
            kind = XHamsterEntityProfile.Kind.USER,
            entityId = user.id,
            name = user.name,
            avatarUrl = user.avatarUrl,
            country = null,
            verified = false,
            isPublic = user.isPublic,
            isMine = user.isMine,
            videoCount = user.videosTotal,
            shortsCount = user.momentsTotal,
            photosCount = null,
            viewsCount = null,
            favoritesCount = user.favoritesTotal,
            subscribers = null,
            subscribed = false,
            subscriptionModel = null,
            aliases = null,
            channels = user.channels,
            tabs = tabs,
            videos = user.videos,
            shorts = collectShorts(root),
        )
    }
}
