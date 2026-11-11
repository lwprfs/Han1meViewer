package com.yenaly.han1meviewer.XHamster.data.model

import com.yenaly.han1meviewer.logic.model.HanimeInfo

data class XHamsterListPage(
    val items: List<HanimeInfo>,

    val nextUrl: String?,
)

data class XHamsterUser(
    val id: Long,
    val name: String,
    val profileUrl: String,
)

data class XHamsterLink(val title: String, val url: String)

data class XHamsterStream(
    val label: String,
    val url: String,

    val height: Int = 0,
)

data class XHamsterDetail(
    val code: String,
    val title: String,
    val description: String,
    val coverUrl: String,
    val pageUrl: String,
    val durationSec: Long,
    val views: Long?,
    val likes: Long?,
    val dislikes: Long?,
    val dateAgo: String?,
    val uploader: String?,
    val maxResolution: String?,
    val tags: List<XHamsterLink>,

    val hlsTemplate: String?,

    val hlsDirect: String?,

    val mp4Fallback: String?,
    val related: List<com.yenaly.han1meviewer.logic.model.HanimeInfo>,
)
