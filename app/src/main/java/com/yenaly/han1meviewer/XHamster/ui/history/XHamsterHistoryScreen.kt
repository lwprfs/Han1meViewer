package com.yenaly.han1meviewer.XHamster.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.XHamster.common.XHamsterImageLoader
import com.yenaly.han1meviewer.XHamster.data.local.XHamsterHistoryEntity
import com.yenaly.han1meviewer.XHamster.data.repository.XHamsterHistoryRepo
import com.yenaly.han1meviewer.ui.component.SiteHistoryEntry
import com.yenaly.han1meviewer.ui.component.SiteHistoryScreen
import kotlinx.coroutines.flow.map

@Composable
fun XHamsterHistoryScreen(
    onNavigateToVideo: (String) -> Unit,
    onBack: () -> Unit,
) {
    XHamsterHistoryRepo.init(LocalContext.current)
    val entries = remember { XHamsterHistoryRepo.loadAll().map { list -> list.map { it.toEntry() } } }
    SiteHistoryScreen(
        entries = entries,
        emptyHint = R.string.xhamster_no_history,
        imageLoader = { XHamsterImageLoader.get(it) },
        onDeleteAll = { XHamsterHistoryRepo.deleteAll() },
        onDelete = { XHamsterHistoryRepo.deleteByVideoCode(it) },
        onNavigateToVideo = onNavigateToVideo,
        onBack = onBack,
    )
}

private fun XHamsterHistoryEntity.toEntry() = SiteHistoryEntry(
    videoCode = videoCode,
    title = title,
    coverUrl = coverUrl,
    progress = if (totalDuration > 0L) lastPosition.toFloat() / totalDuration else null,
)
