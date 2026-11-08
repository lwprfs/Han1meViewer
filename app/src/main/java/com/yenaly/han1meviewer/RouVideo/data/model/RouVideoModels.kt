package com.yenaly.han1meviewer.RouVideo.data.model

import com.yenaly.han1meviewer.RouVideo.common.RouVideoSettings
import com.yenaly.han1meviewer.RouVideo.common.RouVideoTagLabels
import com.yenaly.han1meviewer.RouVideo.common.RouVideoTranslation

data class RouVideoCard(
    val id: String,
    val title: String,
    val titleZh: String? = null,
    val coverUrl: String = "",
    val durationSec: Double = 0.0,
    val viewCount: Long = 0L,
    val createdAt: String? = null,
    val code: String? = null,
    val tags: List<String> = emptyList(),
    val maxResolution: Int = 0,
    val isSeries: Boolean = false,
    val isPoster: Boolean = false,
    val seriesLabel: String? = null,
) {
    val displayTitle: String get() = RouVideoTranslation.title(RouVideoSettings.pick(title, titleZh))
}

data class RouVideoListPage(
    val items: List<RouVideoCard>,
    val pageNum: Int,
    val totalPage: Int,
    val total: Int,
    val tagOptions: List<RouVideoTagCount> = emptyList(),
    val siblings: List<RouVideoTagCount> = emptyList(),
) {
    val hasNext: Boolean get() = pageNum < totalPage
}

data class RouVideoTagCount(val id: String, val count: Int = 0)

data class RouVideoCategory(
    val id: String,
    val count: Int = 0,
    val kids: Int = 0,
    val coverUrl: String? = null,
)

data class RouVideoTaxonomy(
    val cats: List<RouVideoCategory>,
    val genre: List<RouVideoTagCount>,
    val byParent: Map<String, List<RouVideoTagCount>>,
    val totalTags: Int,
)

data class RouVideoEpisode(
    val id: String,
    val title: String,
    val titleZh: String? = null,
    val episode: Int = 0,
    val durationSec: Double = 0.0,
    val viewCount: Long = 0L,
    val coverUrl: String = "",
) {
    val displayTitle: String get() = RouVideoTranslation.title(RouVideoSettings.pick(title, titleZh))
}

data class RouVideoSeriesInfo(
    val id: String,
    val title: String,
    val titleZh: String? = null,
    val status: String? = null,
    val epLabel: String? = null,
    val episodeCount: Int = 0,
    val totalEpisodes: Int = 0,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val viewCount: Long = 0L,
) {
    val displayTitle: String get() = RouVideoTranslation.title(RouVideoSettings.pick(title, titleZh))
    val isCompleted: Boolean get() = status.equals("COMPLETED", ignoreCase = true)
}

data class RouVideoDetail(
    val id: String,
    val title: String,
    val titleZh: String? = null,
    val code: String? = null,
    val coverUrl: String = "",
    val durationSec: Double = 0.0,
    val viewCount: Long = 0L,
    val likeCount: Long = 0L,
    val createdAt: String? = null,
    val tags: List<String> = emptyList(),
    val tagsZh: List<String> = emptyList(),
    val pageUrl: String,

    val streamPath: String,
    val series: RouVideoSeriesInfo? = null,
    val episodes: List<RouVideoEpisode> = emptyList(),
    val related: List<RouVideoCard> = emptyList(),
) {
    val displayTitle: String get() = RouVideoTranslation.title(RouVideoSettings.pick(title, titleZh))

    fun displayTagAt(index: Int): String =
        RouVideoTagLabels.label(tags[index]).takeIf { it != tags[index] }
            ?: if (RouVideoSettings.titleLanguage == RouVideoSettings.LANG_SIMPLIFIED)
                tagsZh.getOrNull(index)?.takeIf { it.isNotBlank() } ?: tags[index]
            else tags[index]
}

data class RouVideoSearchLanding(
    val hotSearches: List<String>,
    val cats: List<RouVideoCategory>,
    val trending: List<RouVideoCard>,
)
