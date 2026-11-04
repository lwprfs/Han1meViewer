package com.yenaly.han1meviewer.MissAV.ui.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.WebsiteState
import com.yenaly.han1meviewer.ui.component.SiteVideoSectionHeader
import com.yenaly.han1meviewer.ui.component.VideoCardItem
import com.yenaly.han1meviewer.ui.screen.rememberVideoGridColumns

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MissAvRelatedVideos(
    state: WebsiteState<List<HanimeInfo>>,
    fallback: List<HanimeInfo>,
    currentCode: String,
    onNavigateToVideo: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val apiVideos = (state as? WebsiteState.Success)?.info.orEmpty()
    val videos = remember(apiVideos, fallback, currentCode) {
        (apiVideos.ifEmpty { fallback })
            .filter { !it.videoCode.equals(currentCode, ignoreCase = true) }
            .distinctBy { it.videoCode }
    }

    if (videos.isEmpty()) {
        if (state is WebsiteState.Loading) {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        return
    }

    val columns = rememberVideoGridColumns()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SiteVideoSectionHeader(title = stringResource(R.string.related_video))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val gap = 8.dp
            val itemWidth = (maxWidth - gap * (columns - 1)) / columns - 0.5.dp
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = columns,
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                videos.forEach { video ->
                    VideoCardItem(
                        modifier = Modifier.width(itemWidth),
                        videoItem = video,
                        onClickVideosItem = {
                            onNavigateToVideo(video.videoCode, "/en/${video.videoCode}")
                        },
                        onLongClickVideosItem = { _, _ -> },
                    )
                }
            }
        }
    }
}
