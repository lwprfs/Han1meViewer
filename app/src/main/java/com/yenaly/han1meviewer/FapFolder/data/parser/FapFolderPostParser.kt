package com.yenaly.han1meviewer.FapFolder.data.parser

import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.common.FapFolderUrlUtils
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderAuthor
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderComment
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderMedia
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPollOption
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPost
import com.yenaly.han1meviewer.FapFolder.data.model.PostVariant
import com.yenaly.han1meviewer.FapFolder.data.model.ReactionCount
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object FapFolderPostParser {

    private val photoGridSelector = ".pg_wrapper a.js_lightbox, .pg_wrapper a.pg_photo"
    private val morePhotosRegex = Regex("""\+\s*(\d+)""")

    fun parsePost(
        postEl: Element,
        baseUrl: String,
        uploadsBase: String? = null,
        parseComments: Boolean = true,
    ): FapFolderPost? {
        val postId = postEl.attr("data-id").toLongOrNull()
            ?: postEl.attr("data-post-id").toLongOrNull()
            ?: return null

        val author = parseAuthor(postEl, baseUrl)

        val uploaderAnchor = postEl.selectFirst(".post-time > a[href]")

        val uploaderUsername: String? = uploaderAnchor?.text()?.trim()?.takeIf { it.isNotBlank() }
        val uploaderUrl: String? = uploaderAnchor?.absUrl("href")?.takeIf { it.isNotBlank() }
            ?: uploaderAnchor?.attr("href")?.takeIf { it.isNotBlank() }
                ?.let(FapFolderUrlUtils::absoluteUrl)

        val uploaderIsGold: Boolean = postEl.selectFirst(".post-time i.gold-badge") != null

        val action: String? = postEl.selectFirst(".post-title")?.text()?.trim()
            ?.takeIf { it.isNotBlank() }

        val postedAtIso: String = postEl.selectFirst(".post-time a.js_moment[data-time]")
            ?.attr("data-time")
            ?.let(FapFolderHtmlUtils::parseDataTimeIso)
            ?: postEl.selectFirst(".js_moment[data-time]")
                ?.attr("data-time")
                ?.let(FapFolderHtmlUtils::parseDataTimeIso)
                ?: ""

        val permalink: String? = postEl.selectFirst("a.dropdown-item[href*='/posts/']")
            ?.absUrl("href")
            ?.takeIf { it.isNotBlank() }
            ?: postEl.selectFirst("a[href*='/posts/']")
                ?.absUrl("href")
                ?.takeIf { it.isNotBlank() }
        val slug = permalink?.let(FapFolderUrlUtils::extractPostSlug)

        val bodyHtml: String = postEl.selectFirst("div.post-text")
            ?.html()
            .orEmpty()
        val bodyPlain: String = (
                postEl.selectFirst("div.post-text-plain")?.text()
                    ?: postEl.selectFirst("div.post-text")?.text()
                    ?: ""
                ).let(FapFolderHtmlUtils::cleanText)

        val hashtags: List<String> = postEl.select("div.post-text a[href*='/search/hashtag/']")
            .mapNotNull { anchor ->
                val href = anchor.attr("href")
                FapFolderUrlUtils.extractTagFromHashtagHref(href)
                    ?: anchor.text().removePrefix("#").trim().takeIf { it.isNotBlank() }
            }
            .distinct()

        val (media, variants) = parseMedia(postEl, baseUrl, uploadsBase)

        val isPremium: Boolean = postEl.hasClass("premiumpost") ||
                postEl.selectFirst(".premiumpostname") != null

        val reactions = parseReactions(postEl)
        val commentCount = FapFolderHtmlUtils.parseLeadingInt(
            postEl.selectFirst("span.js_comments-toggle, .js_comments-toggle")?.text()
        ) ?: 0
        val shareCount = FapFolderHtmlUtils.parseLeadingInt(
            postEl.selectFirst("span[data-url*='who_shares']")?.text()
        ) ?: 0
        val viewCount = parseViewCount(postEl)

        return FapFolderPost(
            postId = postId,
            slug = slug,
            permalink = permalink,
            author = author,
            uploaderUsername = uploaderUsername,
            uploaderUrl = uploaderUrl,
            uploaderIsGold = uploaderIsGold,
            action = action,
            postedAtIso = postedAtIso,
            bodyHtml = bodyHtml,
            bodyPlain = bodyPlain,
            hashtags = hashtags,
            media = media,
            variants = variants,
            isPremium = isPremium,
            reactions = reactions,
            commentCount = commentCount,
            shareCount = shareCount,
            viewCount = viewCount,
        )
    }

    fun parsePostComments(
        postEl: Element,
        baseUrl: String,
        uploadsBase: String? = null,
    ): List<FapFolderComment> {
        val container = postEl.selectFirst("div.post-footer")
            ?: postEl.selectFirst("div.post-comments")
            ?: return emptyList()
        return FapFolderCommentParser.parseComments(container, baseUrl, uploadsBase)
    }

    fun parsePostsFromList(
        root: Element?,
        baseUrl: String,
        uploadsBase: String? = null,
        includeComments: Boolean = false,
    ): List<FapFolderPost> {
        if (root == null) return emptyList()
        val candidates = root.select("li > div.post[data-id], div.post[data-id]")
            .distinctBy { it.attr("data-id") }
        return candidates.mapNotNull { el ->
            if (el.containsOnlyAdContent()) return@mapNotNull null
            parsePost(el, baseUrl, uploadsBase, parseComments = includeComments)
        }
    }

    fun parsePostsFromHtml(
        html: String?,
        baseUrl: String,
        uploadsBase: String? = null,
        includeComments: Boolean = false,
    ): List<FapFolderPost> {
        if (html.isNullOrBlank()) return emptyList()
        val doc: Document = try {
            Jsoup.parseBodyFragment(html, baseUrl)
        } catch (_: Exception) {
            return emptyList()
        }
        return parsePostsFromList(doc.body(), baseUrl, uploadsBase, includeComments)
    }

    private fun parseAuthor(postEl: Element, baseUrl: String): FapFolderAuthor {
        val anchor = postEl.selectFirst("a.post-author[href]")
            ?: postEl.selectFirst(".post-author[href]")

        val href = anchor?.absUrl("href")?.takeIf { it.isNotBlank() }
            ?: anchor?.attr("href")?.takeIf { it.isNotBlank() }
                ?.let(FapFolderUrlUtils::absoluteUrl)

        val groupSlug: String? = href?.let(FapFolderUrlUtils::extractGroupSlug)
        val username: String? = if (groupSlug == null) {
            href?.let(FapFolderUrlUtils::extractUsername)
        } else null

        val displayName: String = anchor?.text()?.trim()
            ?: "Unknown"

        val avatarUrl: String? = run {
            val avatarEl = postEl.selectFirst(".post-avatar-picture")
                ?: return@run null
            val style = avatarEl.attr("style")
            FapFolderHtmlUtils.extractBackgroundImageUrl(style)
                ?.let(FapFolderUrlUtils::normalizeMediaUrl)
                ?: avatarEl.selectFirst("img[src]")?.absUrl("src")?.takeIf { it.isNotBlank() }
                ?: avatarEl.attr("data-image").takeIf { it.isNotBlank() }
        }

        val isGold: Boolean = postEl.selectFirst(".post-author i.gold-badge, .post-author .gold-badge") != null

        val popover = postEl.selectFirst(".js_user-popover[data-uid]")
        val uid = popover?.attr("data-uid")?.toLongOrNull()

        return FapFolderAuthor(
            uid = uid,
            username = username,
            groupSlug = groupSlug,
            displayName = displayName,
            profileUrl = href,
            avatarUrl = avatarUrl,
            isGold = isGold,
            isAnonymous = false,
        )
    }

    private fun parseMedia(
        postEl: Element,
        baseUrl: String,
        uploadsBase: String?,
    ): Pair<List<FapFolderMedia>, Set<PostVariant>> {
        val media = mutableListOf<FapFolderMedia>()
        val variants = mutableSetOf<PostVariant>()

        val vidPlaceholder = postEl.selectFirst("div.vid-placeholder")
        if (vidPlaceholder != null) {
            val video = parseVideo(vidPlaceholder, baseUrl, uploadsBase)
            if (video != null) {
                media += video
                variants += PostVariant.VIDEO
            }
        }

        val photoAnchors = postEl.select(photoGridSelector)
        if (photoAnchors.isNotEmpty()) {
            val singleImageLayout = postEl.selectFirst(".pg_1x") != null
            val multiLayout = postEl.selectFirst(".pg_2x, .pg_2o3, .pg_1o3") != null

            val images = photoAnchors.mapNotNull { parseImageFromAnchor(it, uploadsBase) }
                .distinctBy { it.fullUrl }

            if (images.isNotEmpty()) {
                val overlayMore = postEl.selectFirst(".pg_2o3 .more, .pg_1o3 .more")
                val totalCount = overlayMore?.text()?.let { morePhotosRegex.find(it)?.groupValues?.get(1)?.toIntOrNull() }
                    ?: postEl.selectFirst("a[href*='/photos/']")
                        ?.text()
                        ?.let { FapFolderHtmlUtils.parseLeadingInt(it) }

                if (multiLayout || images.size > 1) {
                    val albumId = images.firstOrNull()?.let { img ->
                        img.photoId?.toString()
                    }
                    media += FapFolderMedia.Gallery(
                        photos = images,
                        totalCount = totalCount ?: images.size,
                        albumId = albumId,
                    )
                    variants += PostVariant.GALLERY
                } else if (singleImageLayout || images.size == 1) {
                    media += images
                    variants += PostVariant.SINGLE_IMAGE
                }
            }
        }

        val embedFrame = postEl.selectFirst(".embed-responsive iframe[src], iframe[src]")
        if (embedFrame != null && !FapFolderHtmlUtils.isAdUrl(embedFrame.absUrl("src"))) {
            val src = FapFolderHtmlUtils.extractIframeSrc(embedFrame)
            if (!src.isNullOrBlank()) {
                media += FapFolderMedia.Embed(
                    iframeUrl = src,
                    provider = FapFolderHtmlUtils.extractIframeProvider(embedFrame),
                )
                variants += PostVariant.EMBED
            }
        }

        val linkPreview = postEl.selectFirst(".post-media-meta .title")
        if (linkPreview != null) {
            val title = FapFolderHtmlUtils.cleanText(linkPreview.text())
            val targetUrl = linkPreview.selectFirst("a[href]")?.absUrl("href")
                ?.takeIf { it.isNotBlank() }
                ?: linkPreview.attr("href").takeIf { it.isNotBlank() }
            val description = FapFolderHtmlUtils.cleanText(
                postEl.selectFirst(".post-media-meta .text")?.text()
            )
            val host = FapFolderHtmlUtils.cleanText(
                postEl.selectFirst(".post-media-image .source")?.text()
            )
            val thumbnail = postEl.selectFirst(".post-media-image .image")
                ?.let { FapFolderHtmlUtils.extractBackgroundImageUrl(it.attr("style")) }
                ?.let(FapFolderUrlUtils::normalizeMediaUrl)
            if (title.isNotBlank()) {
                media += FapFolderMedia.LinkPreview(
                    title = title,
                    description = description.takeIf { it.isNotBlank() },
                    thumbnailUrl = thumbnail,
                    host = host.takeIf { it.isNotBlank() } ?: FapFolderUrlUtils.hostOf(targetUrl).orEmpty(),
                    targetUrl = targetUrl,
                )
                variants += PostVariant.LINK
            }
        }

        val pollContainer = postEl.selectFirst(".poll-options")
        if (pollContainer != null) {
            val poll = parsePoll(pollContainer)
            if (poll != null) {
                media += poll
                variants += PostVariant.POLL
            }
        }

        val funding = postEl.selectFirst(".post-funding-meta")
        if (funding != null) {
            media += FapFolderMedia.Funding(
                title = FapFolderHtmlUtils.cleanText(
                    funding.selectFirst(".funding-title")?.text()
                ),
                description = FapFolderHtmlUtils.cleanText(
                    funding.selectFirst(".funding-description")?.text()
                ).takeIf { it.isNotBlank() },
                current = FapFolderHtmlUtils.parseLeadingLong(
                    funding.selectFirst(".funding-completion")?.text()
                ),
                goal = null,
            )
            variants += PostVariant.FUNDING
        }

        val product = postEl.selectFirst(".post-product-wrapper")
        if (product != null) {
            media += FapFolderMedia.Product(
                name = FapFolderHtmlUtils.cleanText(
                    product.selectFirst(".post-product-details .title")?.text()
                ),
                description = FapFolderHtmlUtils.cleanText(
                    product.selectFirst(".post-product-details .description")?.text()
                ).takeIf { it.isNotBlank() },
                price = product.selectFirst(".post-product-price")?.text()
                    ?.trim()?.takeIf { it.isNotBlank() },
                imageUrl = product.selectFirst(".post-product-image img[src]")
                    ?.absUrl("src")?.takeIf { it.isNotBlank() },
            )
            variants += PostVariant.PRODUCT
        }

        val job = postEl.selectFirst(".post-job-meta")
        if (job != null) {
            media += FapFolderMedia.Job(
                title = FapFolderHtmlUtils.cleanText(
                    job.selectFirst(".job-title")?.text()
                ),
                description = FapFolderHtmlUtils.cleanText(
                    job.selectFirst(".job-description")?.text()
                ).takeIf { it.isNotBlank() },
                location = FapFolderHtmlUtils.cleanText(
                    job.selectFirst(".job-location")?.text()
                ).takeIf { it.isNotBlank() },
            )
            variants += PostVariant.JOB
        }

        if (postEl.selectFirst(".post-custom-fileds-wrapper") != null) {
            variants += PostVariant.CUSTOM_FIELDS
        }

        val bodyText = postEl.selectFirst("div.post-text")?.text()?.trim()
        if (!bodyText.isNullOrBlank() && variants.isEmpty()) {
            variants += PostVariant.TEXT
        }

        return media to variants
    }

    private fun parseVideo(
        vidPlaceholder: Element,
        baseUrl: String,
        uploadsBase: String?,
    ): FapFolderMedia.Video? {
        val videoIdRaw = vidPlaceholder.attr("data-id")
        val videoId = FapFolderHtmlUtils.extractVideoId(videoIdRaw)
            ?.toLongOrNull()
            ?: vidPlaceholder.attr("data-onplay-id").toLongOrNull()

        val onPlayId = vidPlaceholder.attr("data-onplay-id").toLongOrNull()

        val playUrl = vidPlaceholder.attr("data-src").takeIf { it.isNotBlank() }
            ?: return null

        val posterUrl = vidPlaceholder.attr("data-poster").takeIf { it.isNotBlank() }
            ?: FapFolderHtmlUtils.extractBackgroundImageUrl(vidPlaceholder.attr("style"))
                ?.let(FapFolderUrlUtils::normalizeMediaUrl)
            ?: vidPlaceholder.selectFirst("img.sr-only[src]")
                ?.absUrl("src")?.takeIf { it.isNotBlank() }

        val durationSeconds = vidPlaceholder.attr("data-length").toDoubleOrNull()?.toInt()
            ?: FapFolderHtmlUtils.parseDurationSeconds(
                vidPlaceholder.attr("data-length")
            )?.toInt()

        val aspectRatio = vidPlaceholder.attr("data-aspect-ratio").toDoubleOrNull()

        val adEligible = vidPlaceholder.attr("data-ad-eligible") == "1"

        return FapFolderMedia.Video(
            videoId = videoId,
            onPlayId = onPlayId,
            playUrl = playUrl,
            posterUrl = posterUrl,
            durationSeconds = durationSeconds,
            aspectRatio = aspectRatio,
            adEligible = adEligible,
            fileExtension = FapFolderUrlUtils.extractVideoExtension(playUrl),
        )
    }

    private fun parseImageFromAnchor(
        anchor: Element,
        uploadsBase: String?,
    ): FapFolderMedia.Image? {
        val dataImage = anchor.attr("data-image").takeIf { it.isNotBlank() }
        val bg = FapFolderHtmlUtils.extractBackgroundImageUrl(anchor.attr("style"))
        val imgSrc = anchor.selectFirst("img[src]")?.absUrl("src")?.takeIf { it.isNotBlank() }
        val imgDataSrc = anchor.selectFirst("img[data-src]")?.attr("data-src")
            ?.takeIf { it.isNotBlank() }

        val fullUrl = (dataImage ?: bg ?: imgSrc ?: imgDataSrc)
            ?.let { FapFolderUrlUtils.normalizeMediaUrl(it, uploadsBase) }
            ?: return null

        val photoId = anchor.attr("data-id").toLongOrNull()
            ?: FapFolderUrlUtils.extractPhotoId(anchor.attr("href"))

        val previewUrl = imgDataSrc?.let { FapFolderUrlUtils.normalizeMediaUrl(it, uploadsBase) }

        val context = anchor.attr("data-context").takeIf { it.isNotBlank() }

        return FapFolderMedia.Image(
            photoId = photoId,
            fullUrl = fullUrl,
            previewUrl = previewUrl,
            context = context,
            alt = anchor.selectFirst("img")?.attr("alt")?.takeIf { it.isNotBlank() },
        )
    }

    private fun parsePoll(container: Element): FapFolderMedia.Poll? {
        val totalVotes = container.attr("data-poll-votes").toIntOrNull()
            ?: FapFolderHtmlUtils.parseLeadingInt(container.text())
            ?: 0

        val options = container.select(".poll-option").mapNotNull { optionEl ->
            val id = optionEl.attr("data-id").toLongOrNull() ?: return@mapNotNull null
            val label = FapFolderHtmlUtils.cleanText(
                optionEl.selectFirst(".poll-option-title, .option-text, label")?.text()
            ).ifBlank { optionEl.text().substringBefore("%").trim() }
            val votes = optionEl.attr("data-option-votes").toIntOrNull()
                ?: FapFolderHtmlUtils.parseLeadingInt(optionEl.text())
                ?: 0
            val percentText = optionEl.selectFirst(".percentage-bg")?.attr("style")
                ?.let { Regex("""width\s*:\s*([\d.]+)""").find(it)?.groupValues?.getOrNull(1) }
                ?.toFloatOrNull()
                ?: 0f
            val isSelected = optionEl.selectFirst("input[type=radio]")?.hasAttr("checked") == true
            FapFolderPollOption(
                id = id,
                label = label,
                votes = votes,
                percent = if (percentText > 1f) percentText else percentText * 100f,
                isSelected = isSelected,
            )
        }

        if (options.isEmpty()) return null

        val hasVoted = options.any { it.isSelected }
        return FapFolderMedia.Poll(
            totalVotes = totalVotes,
            options = options,
            hasVoted = hasVoted,
        )
    }

    private fun parseReactions(postEl: Element): ReactionCount {
        val statsItem = postEl.selectFirst("div.reactions-stats")
            ?: postEl.selectFirst(".post-stats div.reactions-stats")
        val lastSpan = statsItem?.select("span")?.lastOrNull()
        val total = FapFolderHtmlUtils.parseLeadingInt(lastSpan?.text()) ?: 0
        return ReactionCount(total, null)
    }

    private fun parseViewCount(postEl: Element): Int? {
        val playSpan = postEl.selectFirst(".post-stats span.pointer.ml10")
            ?: postEl.selectFirst(".post-stats .fa-play-circle")?.parent()
        val text = playSpan?.text()?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return FapFolderHtmlUtils.parseLeadingInt(text)
    }
}
