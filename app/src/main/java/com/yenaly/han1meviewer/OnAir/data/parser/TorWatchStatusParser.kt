package com.yenaly.han1meviewer.OnAir.data.parser

import com.yenaly.han1meviewer.OnAir.data.model.OnAirMirrorStatus
import org.jsoup.Jsoup

object TorWatchStatusParser {

    private val httpCodeRegex = Regex("""Status Code:\s*(\d{3})""")

    fun parse(html: String, host: String): OnAirMirrorStatus {
        if (html.isBlank()) {
            return OnAirMirrorStatus.Error(host, "Empty response from TorWatch")
        }
        return runCatching {
            val doc = Jsoup.parse(html)
            val card = doc.selectFirst("div#result-box div.result-container")
                ?: return OnAirMirrorStatus.Error(host, "Result box not found in page")

            val mainText = card.selectFirst("span.main-status-text")
                ?.text()?.trim().orEmpty()
            val isOnline = card.hasClass("alert-success") ||
                    mainText.contains("ONLINE", ignoreCase = true)
            val isOffline = card.hasClass("alert-error") ||
                    mainText.contains("OFFLINE", ignoreCase = true) ||
                    mainText.contains("UNREACHABLE", ignoreCase = true)

            if (isOnline) {
                val statusLine = card.selectFirst("span.text-info")
                    ?.text()?.trim().orEmpty()
                val code = httpCodeRegex.find(statusLine)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val detail = statusLine.ifBlank {
                    card.selectFirst("div.detail-reason-text")?.text()?.trim().orEmpty()
                }
                OnAirMirrorStatus.Online(host, httpCode = code, detail = detail)
            } else if (isOffline) {
                val reason = card
                    .selectFirst("span.font-bold.whitespace-pre-wrap")
                    ?.text()?.trim()
                    ?: card.selectFirst("div.detail-reason-text")
                        ?.text()?.trim()
                        ?.substringAfter("Reason:", "")
                        ?.trim()
                    ?: "Site is offline or unreachable."
                OnAirMirrorStatus.Offline(host, reason)
            } else {
                OnAirMirrorStatus.Error(
                    host,
                    "Unrecognised TorWatch response: ${mainText.ifBlank { "no status text" }}",
                )
            }
        }.getOrElse { e ->
            OnAirMirrorStatus.Error(host, e.message ?: "Parse failure")
        }
    }
}
