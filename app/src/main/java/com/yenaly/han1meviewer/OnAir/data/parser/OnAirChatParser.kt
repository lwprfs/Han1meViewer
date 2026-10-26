package com.yenaly.han1meviewer.OnAir.data.parser

import android.util.Log
import com.yenaly.han1meviewer.OnAir.data.model.OnAirChatMessage
import com.yenaly.han1meviewer.OnAir.data.model.OnAirChatPage
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object OnAirChatParser {

    private const val TAG = "OnAirChatParser"

    fun parseChatPage(html: String, baseUrl: String): OnAirChatPage? {
        val doc = Jsoup.parse(html, baseUrl)
        val section = doc.selectFirst("section#chat-section") ?: run {
            Log.w(TAG, "Chat section not found")
            return null
        }

        val csrfToken = section.selectFirst("form#chat-form input[name=csrf_token]")
            ?.attr("value")
            ?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("form[action=\"/chat/send\"] input[name=csrf_token]")
                ?.attr("value")
                ?.takeIf { it.isNotBlank() }
            ?: run {
                Log.w(TAG, "CSRF token not found on chat page")
                return null
            }

        val latestId = section.attr("data-latest-id").takeIf { it.isNotBlank() }
        val onLastPage = section.attr("data-on-last-page")
            .equals("true", ignoreCase = true)

        val messages = parseMessages(doc, baseUrl)
        val formError = doc.selectFirst("p#chat-send-error")
            ?.text()?.trim()?.takeIf { it.isNotBlank() }

        return OnAirChatPage(
            messages = messages,
            csrfToken = csrfToken,
            latestId = latestId,
            onLastPage = onLastPage,
            formError = formError,
        )
    }

    fun parseMessages(doc: Document, baseUrl: String): List<OnAirChatMessage> {
        return doc.select("div.comment-item.chat-message").mapNotNull { el ->
            runCatching {
                val rawId = el.id().removePrefix("chat-msg-").takeIf { it.isNotBlank() }
                    ?: return@runCatching null
                val meta = el.selectFirst("p.comment-meta")
                val authorAnchor = meta?.selectFirst("a[href^=/user/]")
                val author = authorAnchor?.text()?.trim()?.takeIf { it.isNotBlank() }
                val authorUrl = authorAnchor?.absUrl("href")?.takeIf { it.isNotBlank() }
                val tsEl = meta?.selectFirst("span.chat-timestamp")
                val tsLabel = tsEl?.text()?.trim()?.takeIf { it.isNotBlank() }
                val tsRaw = tsEl?.attr("data-ts")?.takeIf { it.isNotBlank() }
                val body = el.selectFirst("p.comment-body")?.text()?.trim().orEmpty()
                if (body.isBlank()) return@runCatching null
                OnAirChatMessage(
                    id = rawId,
                    author = author,
                    authorUrl = authorUrl,
                    timestampLabel = tsLabel,
                    timestampRaw = tsRaw,
                    body = body,
                )
            }.onFailure { Log.w(TAG, "Failed parsing chat message: ${it.message}") }
                .getOrNull()
        }
    }

    fun detectLoginRequired(html: String): Boolean =
        html.contains("action=\"/login\"", ignoreCase = true) &&
                !html.contains("user-modal-name", ignoreCase = true)

    fun detectRateLimited(html: String): Boolean =
        html.contains("too many requests", ignoreCase = true) ||
                html.contains("rate limit", ignoreCase = true)
}
