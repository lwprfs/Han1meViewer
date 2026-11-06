package com.yenaly.han1meviewer.SupJav.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.SupJav.data.local.SupJavHistoryEntity
import com.yenaly.han1meviewer.SupJav.data.repository.SupJavHistoryRepo
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteEmptyContent
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import kotlinx.coroutines.launch

@Composable
fun SupJavHistoryScreen(
    onNavigateToVideo: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    SupJavHistoryRepo.init(context)
    val scope = rememberCoroutineScope()
    val flow = remember { SupJavHistoryRepo.loadAll() }
    val items by flow.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text(stringResource(R.string.watch_history)) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { scope.launch { SupJavHistoryRepo.deleteAll() } }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null)
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.padding(padding)) { SiteEmptyContent(hint = "No watch history yet") }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            ) {
                items(items, key = { it.videoCode }) { entry ->
                    HistoryRow(
                        entry = entry,
                        onClick = { onNavigateToVideo(entry.videoCode) },
                        onDelete = { scope.launch { SupJavHistoryRepo.deleteByVideoCode(entry.videoCode) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: SupJavHistoryEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = entry.coverUrl,
            contentDescription = entry.title,
            modifier = Modifier
                .width(128.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
            imageLoader = com.yenaly.han1meviewer.SupJav.common.SupJavImageLoader.get(LocalContext.current),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.totalDuration > 0L) {
                LinearProgressIndicator(
                    progress = { (entry.lastPosition.toFloat() / entry.totalDuration).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Close, contentDescription = null)
        }
    }
}
