package com.yenaly.han1meviewer.HentaiMama.data.model

import java.time.LocalDate

data class UpcomingHeader(
    val parentLabel: String,
    val parentUrl: String,
    val monthName: String,
    val monthNumber: Int?,
    val year: Int?,
) {
    val monthPageSlug: String
        get() = if (monthNumber != null && year != null) {
            "${monthName.lowercase()}-$year"
        } else ""
}

data class UpcomingCard(
    val slug: String,
    val url: String,
    val episodeTitle: String,
    val episodeNumber: Int?,
    val seriesTitle: String?,
    val seriesSlug: String?,
    val seriesUrl: String?,
    val poster: String,
    val posterSmall: String?,
    val altText: String,
    val rating: Double?,
    val favoritePostId: Int?,
    val favoriteNonce: String?,
    val airDateRaw: String,
    val airDate: LocalDate?,
    val studio: String?,
    val studioSlug: String?,
    val studioUrl: String?,
    val synopsis: String?,
    val hasReadMore: Boolean,
) {
    val isBrandNewSeries: Boolean get() = seriesUrl.isNullOrBlank()
}

data class UpcomingPage(
    val header: UpcomingHeader,
    val cards: List<UpcomingCard>,
)

data class UpcomingMonthOption(
    val displayName: String,
    val monthName: String,
    val monthNumber: Int,
    val year: Int,
) {
    val slug: String get() = "$monthName-$year"
}
