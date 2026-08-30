package com.yenaly.han1meviewer.HentaiMama

import com.yenaly.han1meviewer.logic.model.HanimeInfo

data class HentaiMamaHomePage(
    val popularVideos: List<HanimeInfo>,
    val latestVideos: List<HanimeInfo>,
)

data class HentaiMamaVideoInfo(
    val title: String,
    val coverUrl: String,
    val videoCode: String,
    val description: String?,
    val genre: String?,
    val author: String?,
    val status: String?,
    val videoUrls: List<HentaiMamaVideoLink>,
    val episodes: List<HentaiMamaEpisode>,
    val relatedVideos: List<HanimeInfo>,
)

data class HentaiMamaVideoLink(
    val quality: String,
    val url: String,
)

data class HentaiMamaEpisode(
    val title: String,
    val url: String,
    val date: String?,
    val episodeNumber: Float?,
    val dateTimestamp: Long = 0L,
)
