package com.yenaly.han1meviewer.logic.model

interface HanimeInfoType : MultiItemEntity

data class HanimeInfo(
    override val title: String,
    override val coverUrl: String,
    override val videoCode: String,
    override val duration: String? = null,
    override val views: String? = null,
    override val uploadTime: String? = null,
    val genre: String? = null,

    val isPlaying: Boolean = false,

    val playlistItemId: String? = null,

    override var itemType: Int,
    override val reviews: String? = "",
    override val currentArtist: String? = "",
    val watched: Boolean ?= false,
): VideoItemType , HanimeInfoType {
    companion object {
        const val NORMAL = 0
        const val SIMPLIFIED = 1
    }
}
