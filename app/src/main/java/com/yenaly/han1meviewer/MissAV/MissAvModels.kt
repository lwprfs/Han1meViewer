package com.yenaly.han1meviewer.MissAV

import com.yenaly.han1meviewer.logic.model.HanimeInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class MissAvHomePage(
    val popularVideos: MutableList<HanimeInfo>,
    val latestVideos: MutableList<HanimeInfo>,
    val hasNextPage: Boolean = false,
)

@Serializable
data class RecommendationsResponse(
    val recommId: String,
    @SerialName("recomms")
    val recommendations: List<Recommendation>,
    val numberNextRecommsCalls: Int,
)

@Serializable
data class Recommendation(
    val id: String,
    @SerialName("values")
    val videoInfo: VideoInfo,
)

@Serializable
data class VideoInfo(
    val dm: Int?,
    @SerialName("title_en")
    val titleEn: String?,
)

@Serializable
data class RelatedResponse(
    val json: RecommendationsResponse,
)

data class MissAvVideoInfo(
    val title: String,
    val coverUrl: String,
    val videoCode: String,
    val jpTitle: String?,
    val genres: String?,
    val directors: String?,
    val makers: String?,
    val actresses: String?,
    val label: String?,
    val series: String?,
    val description: String?,
    val videoUrl: String?,
    val relatedVideos: List<HanimeInfo>,
)