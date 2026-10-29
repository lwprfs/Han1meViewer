package com.yenaly.han1meviewer.FapFolder.data.parser

import com.yenaly.han1meviewer.FapFolder.common.FapFolderUrlUtils
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderComment
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderLightboxResponse
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPost
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object FapFolderLightboxParser {

    data class ParsedLightbox(
        val response: FapFolderLightboxResponse?,
        val hasCallback: Boolean,
        val callbackBody: String?,
        val error: Throwable?,
    )

    fun parse(
        rawJson: String?,
        baseUrl: String,
        uploadsBase: String? = null,
        requestedPhotoId: Long? = null,
    ): ParsedLightbox {
        if (rawJson.isNullOrBlank()) {
            return ParsedLightbox(null, false, null, null)
        }

        val json: JSONObject = try {
            JSONObject(rawJson)
        } catch (e: Exception) {
            return ParsedLightbox(null, false, null, e)
        }

        val callback = json.optString("callback").takeIf { it.isNotBlank() }
        if (callback != null) {
            return ParsedLightbox(null, true, callback, null)
        }

        val lightboxHtml = json.optString("lightbox").takeIf { it.isNotBlank() }
            ?: return ParsedLightbox(null, false, null, null)

        val doc: Document = try {
            Jsoup.parse(lightboxHtml, baseUrl)
        } catch (e: Exception) {
            return ParsedLightbox(null, false, null, e)
        }

        val currentAnchor = doc.selectFirst("a.js_lightbox[data-image]")
            ?: doc.selectFirst("a.js_lightbox[data-id]")
            ?: doc.selectFirst("img[src*='fap.onl/uploads/photos/']")

        val currentPhotoId: Long = requestedPhotoId
            ?: currentAnchor?.attr("data-id")?.toLongOrNull()
            ?: 0L

        val currentImageUrl: String? = currentAnchor?.attr("data-image")
            ?.takeIf { it.isNotBlank() }
            ?.let { FapFolderUrlUtils.normalizeMediaUrl(it, uploadsBase) }
            ?: currentAnchor?.absUrl("src")?.takeIf { it.isNotBlank() }
            ?: FapFolderHtmlUtils.extractBackgroundImageUrl(currentAnchor?.attr("style") ?: "")
                ?.let { FapFolderUrlUtils.normalizeMediaUrl(it, uploadsBase) }

        val post: FapFolderPost? = run {
            val postEl = doc.selectFirst("div.post[data-id]") ?: return@run null
            FapFolderPostParser.parsePost(
                postEl = postEl,
                baseUrl = baseUrl,
                uploadsBase = uploadsBase,
                parseComments = false,
            )
        }

        val comments: List<FapFolderComment> = run {
            val commentsRoot = doc.selectFirst("div.post-footer")
                ?: doc.selectFirst("ul.js_comments")
                ?: return@run emptyList()
            FapFolderCommentParser.parseComments(commentsRoot, baseUrl, uploadsBase)
        }

        val nextObj = json.optJSONObject("next")
        val prevObj = json.optJSONObject("prev")

        val nextPhotoId = nextObj?.optString("photo_id")?.toLongOrNull()
        val nextPhotoSource = nextObj?.optString("source")?.takeIf { it.isNotBlank() }

        val prevPhotoId = prevObj?.optString("photo_id")?.toLongOrNull()
        val prevPhotoSource = prevObj?.optString("source")?.takeIf { it.isNotBlank() }

        val response = FapFolderLightboxResponse(
            currentPhotoId = currentPhotoId,
            currentImageUrl = currentImageUrl,
            post = post,
            comments = comments,
            nextPhotoId = nextPhotoId,
            prevPhotoId = prevPhotoId,
            nextPhotoSource = nextPhotoSource,
            prevPhotoSource = prevPhotoSource,
        )

        return ParsedLightbox(response, false, null, null)
    }

    fun currentPhotoUrlFromNextSource(source: String?, uploadsBase: String?): String? {
        if (source.isNullOrBlank()) return null
        return FapFolderUrlUtils.normalizeMediaUrl(source, uploadsBase)
    }
}
