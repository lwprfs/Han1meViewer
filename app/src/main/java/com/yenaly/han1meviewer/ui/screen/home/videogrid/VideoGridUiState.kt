package com.yenaly.han1meviewer.ui.screen.home.videogrid

import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.PageLoadingState

data class VideoGridUiState(
    val items: List<HanimeInfo> = emptyList(),
    val state: PageLoadingState<*> = PageLoadingState.Loading,
    val loadedPageCount: Int = 0,
    val totalPages: Int = 1,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val isError: Boolean = false,
    val isEmpty: Boolean = false,
)
