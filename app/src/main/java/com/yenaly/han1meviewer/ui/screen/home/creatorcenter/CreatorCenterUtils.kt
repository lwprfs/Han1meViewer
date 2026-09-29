package com.yenaly.han1meviewer.ui.screen.home.creatorcenter

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.state.PageLoadingState

fun LazyGridState.canLoadMore(state: PageLoadingState<*>): Boolean {
    if (state is PageLoadingState.Loading || state is PageLoadingState.NoMoreData || state is PageLoadingState.Error) return false
    val total = layoutInfo.totalItemsCount
    if (total == 0) return false
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return false
    return lastVisible >= total - 4
}

@Composable
fun String.toReviewStatusInfo(): ReviewStatusInfo = when {
    contains("已上傳") -> ReviewStatusInfo(
        text = stringResource(R.string.creator_status_uploaded),
        color = Color(0xFF27C93F)
    )
    contains("排隊") -> ReviewStatusInfo(
        text = stringResource(R.string.creator_status_queued),
        color = Color(0xFFF9A825)
    )
    contains("待處理") -> ReviewStatusInfo(
        text = stringResource(R.string.creator_status_pending),
        color = Color(0xFFFF9800)
    )
    contains("轉檔") -> ReviewStatusInfo(
        text = stringResource(R.string.creator_status_transcoding),
        color = Color(0xFF42A5F5)
    )
    else -> ReviewStatusInfo(
        text = this,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

fun String.extractFileNameFromUrl(): String =
    substringAfterLast('/').substringBefore('?')

data class ReviewStatusInfo(
    val text: String,
    val color: Color,
)
