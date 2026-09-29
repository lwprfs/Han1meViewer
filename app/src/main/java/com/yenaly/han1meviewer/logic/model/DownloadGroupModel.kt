package com.yenaly.han1meviewer.logic.model

import com.yenaly.han1meviewer.logic.entity.download.VideoWithCategories

sealed class DownloadedNode

data class DownloadHeaderNode(
    val groupId: Int,
    val groupKey: String,
    val originalVideos: List<VideoWithCategories>,
    var isExpanded: Boolean = true
) : DownloadedNode()

data class DownloadItemNode(
    val data: VideoWithCategories,
    val parentKey: Int
) : DownloadedNode()
