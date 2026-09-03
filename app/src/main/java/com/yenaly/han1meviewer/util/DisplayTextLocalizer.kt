package com.yenaly.han1meviewer.util

import com.yenaly.yenaly_libs.utils.LanguageHelper
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.Locale

object DisplayTextLocalizer {

    private val viewsRegex = Regex("""([\d.]+)(万次|萬次|万|萬)?""")
    private val relativeTimeRegex = Regex("""^(?:ge)?([\d.]+)(分钟|分鐘|小时|小時|天|周|週|个月|個月|年)前$""")

    fun localizeViews(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return text

        val match = viewsRegex.find(trimmed)
        if (match == null) return text

        val countStr = match.groupValues[1]
        val unit = match.groupValues.getOrNull(2) ?: ""

        val num = countStr.toDoubleOrNull() ?: return text

        return when (language()) {
            Locale.ENGLISH.language -> {
                val formatted = when {
                    unit.contains("万") -> {
                        val value = num * 10000
                        formatNumber(value)
                    }
                    unit.contains("萬") -> {
                        val value = num * 10000
                        formatNumber(value)
                    }
                    unit.isEmpty() || unit == "次" -> {
                        val value = num
                        formatNumber(value)
                    }
                    else -> {
                        val value = num
                        formatNumber(value)
                    }
                }
                "👁️‍🗨️ $formatted"
            }
            Locale.SIMPLIFIED_CHINESE.language -> {
                if (unit.contains("万") || unit.contains("萬")) "${countStr}万次" else "${countStr}次"
            }
            Locale.JAPANESE.language -> {
                if (unit.contains("万") || unit.contains("萬")) "${countStr}万回" else "${countStr}回"
            }
            else -> {
                if (unit.contains("万") || unit.contains("萬")) "${countStr}萬次" else "${countStr}次"
            }
        }
    }

    fun localizeRelativeTime(text: String): String {
        val match = relativeTimeRegex.matchEntire(text.trim()) ?: return text
        val count = match.groupValues[1]
        val unit = match.groupValues[2]
        return when (language()) {
            Locale.SIMPLIFIED_CHINESE.language -> "$count${unit.toSimplifiedUnit()}前"
            Locale.ENGLISH.language -> "$count ${unit.toEnglishUnit(count)} ago"
            Locale.JAPANESE.language -> "$count${unit.toJapaneseUnit()}前"
            else -> "$count${unit.toTraditionalUnit()}前"
        }
    }

    private fun language(): String = LanguageHelper.preferredLanguage.language

    private fun formatNumber(value: Double): String {
        val df = DecimalFormat("#,###.#")
        df.roundingMode = RoundingMode.HALF_UP

        return when {
            value >= 1_000_000_000 -> {
                val billions = value / 1_000_000_000
                val formatted = if (billions % 1 == 0.0) {
                    df.format(billions.toLong())
                } else {
                    df.format(billions)
                }
                "${formatted}B"
            }
            value >= 1_000_000 -> {
                val millions = value / 1_000_000
                val formatted = if (millions % 1 == 0.0) {
                    df.format(millions.toLong())
                } else {
                    df.format(millions)
                }
                "${formatted}M"
            }
            value >= 1_000 -> {
                val thousands = value / 1_000
                val formatted = if (thousands % 1 == 0.0) {
                    df.format(thousands.toLong())
                } else {
                    df.format(thousands)
                }
                "${formatted}K"
            }
            else -> {
                df.format(value.toLong())
            }
        }
    }

    private fun String.toSimplifiedUnit(): String = when (this) {
        "分鐘", "分钟" -> "分钟"
        "小時", "小时" -> "小时"
        "週", "周" -> "周"
        "個月", "个月" -> "个月"
        else -> this
    }

    private fun String.toTraditionalUnit(): String = when (this) {
        "分钟", "分鐘" -> "分鐘"
        "小时", "小時" -> "小時"
        "周", "週" -> "週"
        "个月", "個月" -> "個月"
        else -> this
    }

    private fun String.toJapaneseUnit(): String = when (this) {
        "分钟", "分鐘" -> "分"
        "小时", "小時" -> "時間"
        "天" -> "日"
        "周", "週" -> "週間"
        "个月", "個月" -> "か月"
        "年" -> "年"
        else -> this
    }

    private fun String.toEnglishUnit(count: String): String {
        val singular = count == "1"
        return when (this) {
            "分钟", "分鐘" -> if (singular) "minute" else "minutes"
            "小时", "小時" -> if (singular) "hour" else "hours"
            "天" -> if (singular) "day" else "days"
            "周", "週" -> if (singular) "week" else "weeks"
            "个月", "個月" -> if (singular) "month" else "months"
            "年" -> if (singular) "year" else "years"
            else -> this
        }
    }
}
