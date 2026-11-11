package com.yenaly.han1meviewer.RouVideo.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.RouVideo.common.RouVideoImageLoader
import com.yenaly.han1meviewer.RouVideo.common.RouVideoTranslation
import com.yenaly.han1meviewer.RouVideo.data.local.RouVideoHistoryEntity
import com.yenaly.han1meviewer.RouVideo.data.repository.RouVideoHistoryRepo
import com.yenaly.han1meviewer.ui.component.SiteHistoryEntry
import com.yenaly.han1meviewer.ui.component.SiteHistoryScreen
import kotlinx.coroutines.flow.map

@Composable
fun RouVideoHistoryScreen(
    onNavigateToVideo: (String) -> Unit,
    onBack: () -> Unit,
) {
    RouVideoHistoryRepo.init(LocalContext.current)
    val entries = remember { RouVideoHistoryRepo.loadAll().map { list -> list.map { it.toEntry() } } }
    SiteHistoryScreen(
        entries = entries,
        emptyHint = R.string.rouvideo_no_history,
        imageLoader = { RouVideoImageLoader.get(it) },
        onDeleteAll = { RouVideoHistoryRepo.deleteAll() },
        onDelete = { RouVideoHistoryRepo.deleteByVideoCode(it) },
        onNavigateToVideo = onNavigateToVideo,
        onBack = onBack,
        titleOf = { RouVideoTranslation.title(it) },
    )
}

private fun RouVideoHistoryEntity.toEntry() = SiteHistoryEntry(
    videoCode = videoCode,
    title = title,
    coverUrl = coverUrl,
    progress = if (totalDuration > 0L) lastPosition.toFloat() / totalDuration else null,
)
