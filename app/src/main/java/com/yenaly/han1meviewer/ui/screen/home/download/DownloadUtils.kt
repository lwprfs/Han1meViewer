package com.yenaly.han1meviewer.ui.screen.home.download

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.entity.download.DownloadGroupEntity
import com.yenaly.han1meviewer.logic.entity.download.VideoWithCategories
import com.yenaly.han1meviewer.logic.model.DownloadHeaderNode
import com.yenaly.han1meviewer.logic.model.DownloadItemNode
import com.yenaly.han1meviewer.logic.model.DownloadedNode
import com.yenaly.han1meviewer.logic.state.DownloadState

fun List<VideoWithCategories>.toNodeList(
    groupIdToNameMap: Map<Int, String>,
    collapseDownloadedGroup: Boolean,
): List<DownloadHeaderNode> {
    val groupedData = this.groupBy { it.video.groupId }.toSortedMap()
    return buildList {
        for ((groupId, videos) in groupedData) {
            add(
                DownloadHeaderNode(
                    groupId = groupId,
                    groupKey = groupIdToNameMap[groupId] ?: "ID: $groupId",
                    originalVideos = videos,
                    isExpanded = !collapseDownloadedGroup,
                )
            )
        }
    }
}

fun List<DownloadHeaderNode>.toFlatNodeList(): List<DownloadedNode> {
    val flatList = mutableListOf<DownloadedNode>()
    for (header in this) {
        flatList.add(header)
        if (header.isExpanded) {
            header.originalVideos.forEach { video ->
                flatList.add(DownloadItemNode(video, header.groupId))
            }
        }
    }
    return flatList
}

@Composable
fun List<DownloadGroupEntity>.toDisplayGroups(): List<DownloadGroupEntity> = map { group ->
    if (group.id == DownloadGroupEntity.DEFAULT_GROUP_ID) {
        group.copy(name = stringResource(R.string.ungrouped))
    } else {
        group
    }
}

@Composable
fun downloadStateText(state: DownloadState, progress: Int): String = when (state) {
    DownloadState.Queued -> stringResource(R.string.already_in_queue)
    DownloadState.Downloading -> stringResource(R.string.download_progress_percent, progress)
    DownloadState.Paused -> stringResource(R.string.paused)
    DownloadState.Failed -> stringResource(R.string.download_failed_tap_retry)
    DownloadState.Finished -> stringResource(R.string.download_complete)
    DownloadState.Unknown -> stringResource(R.string.loading)
}

fun downloadStateIcon(state: DownloadState): Int = when (state) {
    DownloadState.Queued -> R.drawable.ic_baseline_play_arrow_24
    DownloadState.Downloading -> R.drawable.ic_baseline_pause_24
    DownloadState.Paused -> R.drawable.ic_baseline_play_arrow_24
    DownloadState.Failed -> R.drawable.baseline_error_outline_24
    DownloadState.Finished -> R.drawable.ic_baseline_check_circle_24
    DownloadState.Unknown -> R.drawable.ic_baseline_download_24
}
