package com.yenaly.han1meviewer.FapFolder.data.parser

import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.common.FapFolderUrlUtils
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderAlbumPage
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderMedia
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object FapFolderAlbumParser {

    data class AlbumSnapshot(
        val albumId: String,
        val title: String?,
        val ownerName: String?,
        val ownerUrl: String?,
        val photos: List<FapFolderMedia.Image>,
        val hasMore: Boolean,
        val nextOffset: Int,
        val streamGet: String?,
        val streamFilter: String?,
        val streamId: String?,
        val streamType: String?,
        val streamUid: String?,
    )

    fun parseStreamAttrs(doc: Document): FapFolderGroupParser.StreamAttrs? =
        FapFolderGroupParser.parseStreamAttrs(doc)

    fun hasSeeMore(doc: Document): Boolean =
        FapFolderGroupParser.hasSeeMore(doc)

    fun parseAlbumPageFromHtml(
        html: String?,
        pageUrl: String,
        uploadsBase: String? = null,
    ): AlbumSnapshot? {
        if (html.isNullOrBlank()) return null
        val doc: Document = try {
            Jsoup.parse(html, pageUrl)
        } catch (_: Exception) {
            return null
        }

        val albumId = FapFolderUrlUtils.extractAlbumId(pageUrl)
            ?: FapFolderUrlUtils.extractPhotoId(pageUrl)?.toString()
            ?: return null

        val title: String? = doc.selectFirst("div.album-details .album-title, h1.page-title, h2")
            ?.text()
            ?.let(FapFolderHtmlUtils::cleanText)
            ?.takeIf { it.isNotBlank() }

        val ownerAnchor = doc.selectFirst("div.album-details a[href]")
        val ownerName: String? = ownerAnchor?.text()
            ?.let(FapFolderHtmlUtils::cleanText)
            ?.takeIf { it.isNotBlank() }
        val ownerUrl: String? = ownerAnchor?.absUrl("href")?.takeIf { it.isNotBlank() }
            ?: ownerAnchor?.attr("href")?.takeIf { it.isNotBlank() }
                ?.let(FapFolderUrlUtils::absoluteUrl)

        val panel = doc.selectFirst("div.panel-photos")
            ?: doc.selectFirst("div[class*='panel-photos']")
        val photos = if (panel != null) parsePhotoTiles(panel, uploadsBase) else emptyList()

        val seeMore = doc.selectFirst(".js_see-more-infinite")
            ?: doc.selectFirst(".js_see-more")
        val attrs = FapFolderGroupParser.parseStreamAttrs(doc)
        val hasMore = when {
            seeMore == null -> false
            seeMore.hasClass("done") -> false
            photos.isEmpty() -> false
            else -> true
        }
        val nextOffset = attrs?.offset?.coerceAtLeast(2) ?: 2

        return AlbumSnapshot(
            albumId = albumId,
            title = title,
            ownerName = ownerName,
            ownerUrl = ownerUrl,
            photos = photos,
            hasMore = hasMore,
            nextOffset = nextOffset,
            streamGet = attrs?.get,
            streamFilter = attrs?.filter,
            streamId = attrs?.id ?: albumId,
            streamType = attrs?.type,
            streamUid = attrs?.uid,
        )
    }

    fun parseAlbum(
        html: String?,
        pageUrl: String,
        uploadsBase: String? = null,
        nextOffset: Int = 2,
    ): FapFolderAlbumPage? {
        val snapshot = parseAlbumPageFromHtml(html, pageUrl, uploadsBase) ?: return null
        return FapFolderAlbumPage(
            albumId = snapshot.albumId,
            title = snapshot.title,
            ownerName = snapshot.ownerName,
            ownerUrl = snapshot.ownerUrl,
            photos = snapshot.photos,
            hasMore = snapshot.hasMore,
            nextOffset = snapshot.nextOffset.takeIf { it > 0 } ?: nextOffset,
        )
    }

    fun parseAlbumFromHtml(
        html: String?,
        baseUrl: String,
        uploadsBase: String? = null,
    ): List<FapFolderMedia.Image> {
        if (html.isNullOrBlank()) return emptyList()
        val doc: Document = try {
            Jsoup.parseBodyFragment(html, baseUrl)
        } catch (_: Exception) {
            return emptyList()
        }
        val panel = doc.selectFirst("div.panel-photos") ?: doc.body()
        return parsePhotoTiles(panel, uploadsBase)
    }

    private fun parsePhotoTiles(
        panel: Element,
        uploadsBase: String?,
    ): List<FapFolderMedia.Image> {
        return panel.select(
            "div.pg_photo a[href], div.col-4 a[href*='/photos/'], a.pg_photo[href], a.js_lightbox[href]"
        )
            .mapNotNull { anchor ->
                if (anchor.containsOnlyAdContent()) return@mapNotNull null

                val photoId = FapFolderUrlUtils.extractPhotoId(anchor.attr("href"))
                    ?: anchor.attr("data-id").toLongOrNull()
                    ?: return@mapNotNull null

                val bg = FapFolderHtmlUtils.extractBackgroundImageUrl(anchor.attr("style"))
                val dataImage = anchor.attr("data-image").takeIf { it.isNotBlank() }
                val imgSrc = anchor.selectFirst("img[src]")?.absUrl("src")
                    ?.takeIf { it.isNotBlank() }

                val fullUrl = (dataImage ?: bg ?: imgSrc)
                    ?.let { FapFolderUrlUtils.normalizeMediaUrl(it, uploadsBase) }
                    ?: return@mapNotNull null

                val preview = anchor.selectFirst("img[data-src]")?.attr("data-src")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { FapFolderUrlUtils.normalizeMediaUrl(it, uploadsBase) }
                    ?: anchor.selectFirst("img[src]")?.absUrl("src")
                        ?.takeIf { it.isNotBlank() }

                val alt = anchor.attr("aria-label").takeIf { it.isNotBlank() }
                    ?: anchor.selectFirst("img")?.attr("alt")?.takeIf { it.isNotBlank() }

                FapFolderMedia.Image(
                    photoId = photoId,
                    fullUrl = fullUrl,
                    previewUrl = preview,
                    context = anchor.attr("data-context").takeIf { it.isNotBlank() }
                        ?: "photos",
                    alt = alt,
                )
            }
            .distinctBy { it.photoId ?: it.fullUrl }
    }

    fun defaultAlbumGet(): String = "album_photos"

    fun fallbackUploadsBase(): String = FapFolderConstants.UPLOADS_BASE
}
