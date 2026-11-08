package com.yenaly.han1meviewer.RouVideo.common

import android.text.format.DateUtils
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoCard
import java.time.Instant
import java.util.Locale

object RouVideoFormat {

    fun duration(seconds: Double): String {
        val total = seconds.toLong().coerceAtLeast(0L)
        if (total <= 0L) return ""
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun count(n: Long): String = when {
        n >= 1_000_000L -> String.format(Locale.US, "%.1fM", n / 1_000_000.0).replace(".0M", "M")
        n >= 10_000L -> String.format(Locale.US, "%.1fK", n / 1_000.0).replace(".0K", "K")
        else -> n.toString()
    }

    fun age(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        val millis = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull() ?: return ""
        return DateUtils.getRelativeTimeSpanString(
            millis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()
    }

    private val SKIPPED_TAGS: Set<String> =
        RouVideoConstants.MAIN_TAGS.toSet() + "中文字幕"

    fun displayTag(card: RouVideoCard, skipTag: String? = null): String? =
        card.tags.firstOrNull { it !in SKIPPED_TAGS && it != skipTag }

    fun badge(card: RouVideoCard): String? = when {
        card.isSeries -> card.seriesLabel
        card.maxResolution >= 720 -> "${card.maxResolution}P"
        else -> null
    }
}
