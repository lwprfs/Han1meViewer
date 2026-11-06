package com.yenaly.han1meviewer.SupJav.data.model

import com.yenaly.han1meviewer.logic.model.HanimeInfo

data class SupJavListPage(
    val items: List<HanimeInfo>,
    val hasNext: Boolean,
)

data class SupJavLink(val title: String, val url: String)

data class SupJavServer(val name: String, val token: String)

data class SupJavDetail(
    val code: String,
    val title: String,
    val coverUrl: String,
    val pageUrl: String,
    val category: SupJavLink?,
    val makers: List<SupJavLink>,
    val cast: List<SupJavLink>,
    val tags: List<SupJavLink>,
    val servers: List<SupJavServer>,
    val related: List<HanimeInfo>,
)

data class SupJavStream(
    val url: String,
    val label: String,

    val referer: String,

    val height: Int = 0,
)
