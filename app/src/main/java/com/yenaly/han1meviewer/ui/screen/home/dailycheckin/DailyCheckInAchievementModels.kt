package com.yenaly.han1meviewer.ui.screen.home.dailycheckin

import com.yenaly.han1meviewer.ui.viewmodel.MonthlyStats

internal const val STATS_TYPE_DISPLAY_COUNT = 6

internal const val EXTRA_ACHIEVEMENT_DISPLAY_COUNT = 6

data class Achievement(
    val emoji: String,
    val title: String,
    val subtitle: String,
    val videoCode: String = "",
)

data class MainAchievementRule(
    val check: (checkedDays: Int, monthlyTotal: Int, bestStreak: Int) -> Boolean,
    val emoji: String,
    val title: String,
    val subtitle: String,
)

data class ExtraAchievementRule(
    val check: (MonthlyStats) -> Boolean,
    val emoji: String,
    val label: String,
    val value: String,
    val videoCode: String = "",
)

fun buildMainAchievementRules(
    checkedDays: Int,
    monthlyTotal: Int,
    bestStreak: Int,
    formattedTitles: Map<String, String>,
    formattedSubs: Map<String, String>,
): List<MainAchievementRule> = buildList {
    val tl = { key: String -> formattedTitles[key] ?: key }
    val sb = { key: String -> formattedSubs[key] ?: key }

    if (monthlyTotal >= 200) add(
        MainAchievementRule(
            check = { _, t, _ -> t >= 200 },
            emoji = "\uD83E\uDD34",
            title = tl("legend"),
            subtitle = sb("god"),
        )
    )

    if (monthlyTotal >= 100) add(
        MainAchievementRule(
            check = { _, t, _ -> t >= 100 },
            emoji = "\uD83D\uDC51",
            title = tl("champion"),
            subtitle = sb("top"),
        )
    )

    if (monthlyTotal >= 69) add(
        MainAchievementRule(
            check = { _, t, _ -> t >= 69 },
            emoji = "\uD83D\uDE0F",
            title = tl("nice"),
            subtitle = sb("nice"),
        )
    )

    if (monthlyTotal >= 50) add(
        MainAchievementRule(
            check = { _, t, _ -> t >= 50 },
            emoji = "\uD83C\uDFC6",
            title = tl("champion"),
            subtitle = sb("top"),
        )
    )

    if (checkedDays >= 25) add(
        MainAchievementRule(
            check = { d, _, _ -> d >= 25 },
            emoji = "\uD83D\uDD25",
            title = tl("onFire"),
            subtitle = sb("days"),
        )
    )

    if (checkedDays >= 15) add(
        MainAchievementRule(
            check = { d, _, _ -> d >= 15 },
            emoji = "\uD83D\uDE80",
            title = tl("great"),
            subtitle = sb("days"),
        )
    )

    if (bestStreak >= 7) add(
        MainAchievementRule(
            check = { _, _, s -> s >= 7 },
            emoji = "\u2B50",
            title = tl("weekStreak"),
            subtitle = sb("streak"),
        )
    )

    if (bestStreak >= 3) add(
        MainAchievementRule(
            check = { _, _, s -> s >= 3 },
            emoji = "\uD83D\uDCAA",
            title = tl("streak"),
            subtitle = sb("streak"),
        )
    )
}

fun buildExtraAchievementRules(
    stats: MonthlyStats,
    formattedLabels: Map<String, String>,
): List<ExtraAchievementRule> = buildList {
    val lb = { key: String -> formattedLabels[key] ?: key }

    if (stats.uniqueDishes >= 3) add(
        ExtraAchievementRule(
            check = { it.uniqueDishes >= 3 },
            emoji = "\uD83C\uDF7D\uFE0F",
            label = lb("dishVariety"),
            value = "${stats.uniqueDishes}种",
        )
    )

    if (stats.topDishCount >= 3) add(
        ExtraAchievementRule(
            check = { it.topDishCount >= 3 },
            emoji = "\uD83C\uDFAF",
            label = stats.topDish,
            value = "${lb("topDish")}·${stats.topDishCount}次",
            videoCode = stats.topDishVideoCode,
        )
    )

    if (stats.maxDailyTypes >= 3) add(
        ExtraAchievementRule(
            check = { it.maxDailyTypes >= 3 },
            emoji = "\uD83C\uDF08",
            label = lb("multiType"),
            value = "${stats.maxDailyTypes}种",
        )
    )

    if (stats.dominantPeriod == "22~02") add(
        ExtraAchievementRule(
            check = { it.dominantPeriod == "22~02" },
            emoji = "\uD83E\uDD71",
            label = lb("nightOwl"),
            value = "22~02時",
        )
    )

    if (stats.dominantPeriod == "05~10") add(
        ExtraAchievementRule(
            check = { it.dominantPeriod == "05~10" },
            emoji = "\uD83C\uDF05",
            label = lb("morning"),
            value = "05~10時",
        )
    )

    if (stats.totalFeelingChars >= 100) add(
        ExtraAchievementRule(
            check = { it.totalFeelingChars >= 100 },
            emoji = "\uD83D\uDCDD",
            label = lb("scholar"),
            value = stats.scholarDate,
        )
    )
    if (stats.daysChecked >= 6) add(
        ExtraAchievementRule(
            check = { it.daysChecked >= 6 },
            emoji = "\uD83D\uDCA5",
            label = lb("sixTimes"),
            value = "6",
        )
    )
}

fun evaluateMainAchievements(
    rules: List<MainAchievementRule>,
    checkedDays: Int,
    monthlyTotal: Int,
    bestStreak: Int,
    defaultTitle: String,
    defaultSubtitle: String,
): List<Achievement> {
    val matches = rules.filter { it.check(checkedDays, monthlyTotal, bestStreak) }
        .map { Achievement(emoji = it.emoji, title = it.title, subtitle = it.subtitle) }
    return matches.ifEmpty {
        listOf(
            Achievement(
                emoji = "\uD83D\uDC4D",
                title = defaultTitle,
                subtitle = defaultSubtitle
            )
        )
    }
}

fun evaluateExtraAchievements(
    rules: List<ExtraAchievementRule>,
    stats: MonthlyStats,
    maxCount: Int,
): List<Achievement> = rules
    .filter { it.check(stats) }
    .take(maxCount)
    .map {
        Achievement(
            emoji = it.emoji,
            title = it.value,
            subtitle = it.label,
            videoCode = it.videoCode
        )
    }
