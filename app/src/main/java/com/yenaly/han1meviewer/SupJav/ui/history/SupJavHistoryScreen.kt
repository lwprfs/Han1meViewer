package com.yenaly.han1meviewer.SupJav.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.SupJav.common.SupJavImageLoader
import com.yenaly.han1meviewer.SupJav.data.local.SupJavHistoryEntity
import com.yenaly.han1meviewer.SupJav.data.repository.SupJavHistoryRepo
import com.yenaly.han1meviewer.ui.component.SiteHistoryEntry
import com.yenaly.han1meviewer.ui.component.SiteHistoryScreen
import kotlinx.coroutines.flow.map

@Composable
fun SupJavHistoryScreen(
    onNavigateToVideo: (String) -> Unit,
    onBack: () -> Unit,
) {
    SupJavHistoryRepo.init(LocalContext.current)
    val entries = remember { SupJavHistoryRepo.loadAll().map { list -> list.map { it.toEntry() } } }
    SiteHistoryScreen(
        entries = entries,
        emptyHint = R.string.supjav_no_history,
        imageLoader = { SupJavImageLoader.get(it) },
        onDeleteAll = { SupJavHistoryRepo.deleteAll() },
        onDelete = { SupJavHistoryRepo.deleteByVideoCode(it) },
        onNavigateToVideo = onNavigateToVideo,
        onBack = onBack,
    )
}

private fun SupJavHistoryEntity.toEntry() = SiteHistoryEntry(
    videoCode = videoCode,
    title = title,
    coverUrl = coverUrl,
    progress = if (totalDuration > 0L) lastPosition.toFloat() / totalDuration else null,
)
