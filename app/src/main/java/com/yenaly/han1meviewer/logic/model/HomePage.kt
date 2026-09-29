package com.yenaly.han1meviewer.logic.model

data class HomePage(
    val csrfToken: String?,
    val avatarUrl: String?,
    val username: String?,
    val banner: Banner?,
    val latestHanime: MutableList<HanimeInfo>,
    val latestRelease: MutableList<HanimeInfo>,
    val ecchiAnime: MutableList<HanimeInfo>,
    val shortEpisodeAnime: MutableList<HanimeInfo>,
    val twoPointFiveDAnime: MutableList<HanimeInfo>,
    val threeDCG: MutableList<HanimeInfo>,
    val motionAnime: MutableList<HanimeInfo>,
    val twoDAnime: MutableList<HanimeInfo>,
    val aiGenerated: MutableList<HanimeInfo>,
    val mmd: MutableList<HanimeInfo>,
    val cosplay: MutableList<HanimeInfo>,
    val watchingNow: MutableList<HanimeInfo>,
    val newAnimeTrailer: MutableList<HanimeInfo>,
    val timestamp: Long = System.currentTimeMillis(),
    val userId: String
) {
    data class Banner(
        val title: String,
        val description: String?,
        val picUrl: String,

        val videoCode: String?,
    )
}
