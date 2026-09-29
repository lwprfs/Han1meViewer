package com.yenaly.han1meviewer.ui.screen.home.myplaylist

import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.model.Playlists
import com.yenaly.han1meviewer.logic.state.PageLoadingState

data class PlaylistUiState(
    val playlists: List<Playlists.Playlist> = emptyList(),
    val isRefreshing: Boolean = false,
    val showSheet: Boolean = false,
    val selectedListCode: String = "",
    val selectedListTitle: String = "",
    val isLoadingMore: Boolean = false,
    val noMorePlaylists: Boolean = false,
    val playlistPage: Int = 1,
    val totalPages: Int = 1,
)

sealed interface PlaylistEvent {

    data object OnRefresh : PlaylistEvent

    data object OnLoadMore : PlaylistEvent

    data class OnGoToPage(val page: Int) : PlaylistEvent

    data class OnPlaylistClick(val listCode: String, val title: String) : PlaylistEvent

    data object OnDismissSheet : PlaylistEvent

    data object OnBack : PlaylistEvent

    data class OnCreatePlaylist(val title: String, val desc: String) : PlaylistEvent
}

data class PlaylistSheetCallbacks(
    val loadItems: (page: Int, listCode: String, refresh: Boolean) -> Unit,
    val modifyPlaylist: (listCode: String, title: String, desc: String, delete: Boolean) -> Unit,
    val deleteFromPlaylist: (itemId: String, position: Int) -> Unit,
    val updateScrollState: (listCode: String, firstVisibleIndex: Int, scrollOffset: Int) -> Unit,
    val getScrollState: (listCode: String) -> Pair<Int, Int>,
    val onLoadPlaylists: () -> Unit,
)

data class PlaylistSheetUiState(
    val playlistItems: List<HanimeInfo> = emptyList(),
    val playlistState: PageLoadingState<*> = PageLoadingState.Loading,
    val playlistDesc: String? = null,
    val currentListCode: String = "",
    val currentListTitle: String = "",
    val currentPage: Int = 1,
)
