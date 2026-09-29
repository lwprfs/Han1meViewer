package com.yenaly.han1meviewer.ui.screen.home.download

import com.yenaly.han1meviewer.logic.entity.download.DownloadGroupEntity
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.entity.download.VideoWithCategories
import com.yenaly.han1meviewer.logic.model.DownloadedNode

data class DownloadUiState(
    val downloadingItems: List<HanimeDownloadEntity> = emptyList(),
    val downloadedNodes: List<DownloadedNode> = emptyList(),
    val displayGroups: List<DownloadGroupEntity> = emptyList(),
    val currentPage: Int = 0,
    val showCreateGroupDialog: Boolean = false,
    val multiSelectMode: Boolean = false,
    val selectedVideoIds: Set<Int> = emptySet(),
)

sealed interface DownloadEvent {

    data class OnPauseAll(val items: List<HanimeDownloadEntity>) : DownloadEvent

    data class OnResumeAll(val items: List<HanimeDownloadEntity>) : DownloadEvent

    data class OnPauseItem(val item: HanimeDownloadEntity) : DownloadEvent

    data class OnResumeItem(val item: HanimeDownloadEntity) : DownloadEvent

    data class OnDeleteDownloadingItem(val item: HanimeDownloadEntity) : DownloadEvent

    data object OnImportDownloaded : DownloadEvent

    data class OnOpenDownloadedVideo(val video: VideoWithCategories) : DownloadEvent

    data class OnLocalPlayback(val video: VideoWithCategories) : DownloadEvent

    data class OnExternalPlayback(val video: VideoWithCategories) : DownloadEvent

    data class OnDeleteDownloadedVideo(val video: VideoWithCategories) : DownloadEvent

    data class OnMoveVideoGroup(val video: VideoWithCategories, val groupId: Int) : DownloadEvent

    data class OnRenameGroup(val groupId: Int, val newName: String) : DownloadEvent

    data class OnCreateGroup(val name: String) : DownloadEvent

    data class OnDeleteGroup(val group: DownloadGroupEntity) : DownloadEvent

    data class OnToggleGroup(val groupId: Int) : DownloadEvent

    data class OnCreateGroupDialogChange(val visible: Boolean) : DownloadEvent

    data class OnPageChange(val page: Int) : DownloadEvent

    data object OnToggleMultiSelect : DownloadEvent

    data class OnToggleVideoSelection(val videoId: Int) : DownloadEvent

    data class OnSelectAllCurrentGroup(val groupId: Int, val select: Boolean) : DownloadEvent

    data class OnBatchDelete(val videos: List<VideoWithCategories>) : DownloadEvent

    data class OnBatchMoveGroup(val videos: List<VideoWithCategories>, val groupId: Int) : DownloadEvent

    data object OnBatchMoveRequest : DownloadEvent
}
