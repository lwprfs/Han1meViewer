package com.yenaly.han1meviewer.ui.component

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.yenaly.han1meviewer.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** One row of a site's watch history, already reduced to what the UI needs. */
data class SiteHistoryEntry(
    val videoCode: String,
    val title: String,
    val coverUrl: String,
    /** 0..1 watched fraction, or `null` when the total duration is unknown. */
    val progress: Float?,
)

/**
 * Watch-history list shared by the simple sites (XHamster, RouVideo, SupJav).
 *
 * @param entries     live history, newest first.
 * @param emptyHint   string shown when there is no history.
 * @param imageLoader per-site coil loader for the cover images.
 * @param titleOf     optional display transform for a title (e.g. translation).
 */
@Composable
fun SiteHistoryScreen(
    entries: Flow<List<SiteHistoryEntry>>,
    @StringRes emptyHint: Int,
    imageLoader: (Context) -> ImageLoader,
    onDeleteAll: suspend () -> Unit,
    onDelete: suspend (videoCode: String) -> Unit,
    onNavigateToVideo: (String) -> Unit,
    onBack: () -> Unit,
    titleOf: (String) -> String = { it },
) {
    val scope = rememberCoroutineScope()
    val items by entries.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text(stringResource(R.string.watch_history)) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { scope.launch { onDeleteAll() } }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null)
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.padding(padding)) { SiteEmptyContent(hint = stringResource(emptyHint)) }
        } else {
            val loader = imageLoader(LocalContext.current)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(12.dp),
            ) {
                items(items, key = { it.videoCode }) { entry ->
                    SiteVideoRow(
                        title = titleOf(entry.title),
                        coverUrl = entry.coverUrl,
                        onClick = { onNavigateToVideo(entry.videoCode) },
                        progress = entry.progress,
                        imageLoader = loader,
                        trailing = {
                            IconButton(onClick = { scope.launch { onDelete(entry.videoCode) } }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        },
                    )
                }
            }
        }
    }
}
