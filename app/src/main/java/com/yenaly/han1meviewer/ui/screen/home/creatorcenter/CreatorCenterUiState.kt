package com.yenaly.han1meviewer.ui.screen.home.creatorcenter

import com.yenaly.han1meviewer.logic.model.CreatorSort
import com.yenaly.han1meviewer.logic.model.CreatorTab
import com.yenaly.han1meviewer.logic.model.CreatorUploadingItem
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.PageLoadingState

data class CreatorCenterUiState(
    val selectedTab: CreatorTab = CreatorTab.Uploaded,
    val uploadedItems: List<HanimeInfo> = emptyList(),
    val uploadingItems: List<CreatorUploadingItem> = emptyList(),
    val uploadedState: PageLoadingState<*> = PageLoadingState.Loading,
    val uploadingState: PageLoadingState<*> = PageLoadingState.Loading,
    val uploadedSort: CreatorSort = CreatorSort.Latest,
    val uploadingSort: CreatorSort = CreatorSort.Latest,
    val uploadedPage: Int = 0,
    val uploadingPage: Int = 0,
    val uploadedTotalPages: Int = 1,
    val uploadingTotalPages: Int = 1,
    val uploadedLoadingMore: Boolean = false,
    val uploadingLoadingMore: Boolean = false,
)

sealed interface CreatorCenterEvent {

    data class OnTabChange(val tab: CreatorTab) : CreatorCenterEvent

    data class OnSortChange(val tab: CreatorTab, val sort: CreatorSort) : CreatorCenterEvent

    data class OnLoadMore(val tab: CreatorTab) : CreatorCenterEvent

    data class OnGoToPage(val tab: CreatorTab, val page: Int) : CreatorCenterEvent

    data class OnRefresh(val tab: CreatorTab) : CreatorCenterEvent

    data class OnOpenUploadedVideo(val item: HanimeInfo) : CreatorCenterEvent

    data class OnOpenUploadingVideo(val item: CreatorUploadingItem) : CreatorCenterEvent

    data object OnBack : CreatorCenterEvent
}
