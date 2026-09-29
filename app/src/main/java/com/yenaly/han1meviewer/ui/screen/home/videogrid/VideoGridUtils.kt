package com.yenaly.han1meviewer.ui.screen.home.videogrid

import androidx.compose.foundation.lazy.grid.LazyGridState
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.PageLoadingState

fun LazyGridState.canLoadMore(
    items: List<HanimeInfo>,
    state: PageLoadingState<*>,
): Boolean {
    if (items.isEmpty()) return false
    if (state is PageLoadingState.Loading || state is PageLoadingState.NoMoreData || state is PageLoadingState.Error) {
        return false
    }
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return false
    return lastVisible >= layoutInfo.totalItemsCount - 4
}
