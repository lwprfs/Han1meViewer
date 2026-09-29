package com.yenaly.han1meviewer.ui.screen.home.homepage

import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import com.yenaly.han1meviewer.logic.model.Announcement
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.model.HomePage

data class HomeData(
    val page: HomePage,
    val announcements: List<Announcement> = emptyList()
)

val LocalSearchHistoryQuery = staticCompositionLocalOf<suspend (String) -> List<String>> {
    { emptyList() }
}

data class HomeHeroItem(
    val imageUrl: String,
    val title: String,
    val subtitle: String?,
    val videoCode: String?,
)

data class HomeCategory(
    val key: String,
    @param:StringRes val titleRes: Int,
    val genre: String? = null,
    val sort: String? = null,
    val tags: String? = null,
    val videos: List<HanimeInfo>
)
