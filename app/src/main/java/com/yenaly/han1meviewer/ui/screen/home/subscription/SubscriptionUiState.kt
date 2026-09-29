package com.yenaly.han1meviewer.ui.screen.home.subscription

import com.yenaly.han1meviewer.logic.model.SubscriptionItem
import com.yenaly.han1meviewer.logic.model.SubscriptionVideosItem

data class SubscriptionUiState(
    val artists: List<SubscriptionItem> = emptyList(),
    val videos: List<SubscriptionVideosItem> = emptyList(),
    val isRefreshing: Boolean = false,
    val canLoadMore: Boolean = false,
    val currentPage: Int = 1,
    val maxPage: Int = 1,
    val error: Throwable? = null,
    val showCached: Boolean = false,
)

sealed interface SubscriptionEvent {

    data class OnClickArtist(val artistName: String) : SubscriptionEvent

    data class OnLongClickArtist(val artistName: String) : SubscriptionEvent

    data class OnClickVideo(val videoCode: String) : SubscriptionEvent

    data class OnLongClickVideo(val videoCode: String, val title: String) : SubscriptionEvent

    data object OnRefresh : SubscriptionEvent

    data object OnLoadMore : SubscriptionEvent

    data class OnGoToPage(val page: Int) : SubscriptionEvent

    data object OnBack : SubscriptionEvent
}
