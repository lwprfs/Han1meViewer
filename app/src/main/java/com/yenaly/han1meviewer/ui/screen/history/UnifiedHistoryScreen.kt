package com.yenaly.han1meviewer.ui.screen.history

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.FapFolder.data.local.FapFolderHistoryRepo
import com.yenaly.han1meviewer.HentaiMama.data.local.HentaiMamaHistoryRepo
import com.yenaly.han1meviewer.MissAV.data.repository.MissAvHistoryRepo
import com.yenaly.han1meviewer.OnAir.data.local.OnAirHistoryRepo
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.logic.DatabaseRepo
import com.yenaly.han1meviewer.logic.entity.WatchHistoryEntity
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.navigation.CrossSiteNavigator
import com.yenaly.han1meviewer.ui.navigation.PendingVideoOpen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

data class UnifiedHistoryItem(
    val site: SiteType,
    val code: String,
    val title: String,
    val coverUrl: String,
    val watchDate: Long,
    val positionMs: Long,
    val durationMs: Long,
    val path: String?,
    val subtitle: String?,
    val completed: Boolean,
    val autoPlay: Boolean = false,
) {
    val key: String get() = "${site.name}:$code"

    val progress: Float?
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else null

    fun toRequest(): PendingVideoOpen = PendingVideoOpen(
        site = site,
        videoCode = code,
        path = path,

        resumePosition = if (site == SiteType.HENTAIMAMA && !completed) positionMs else 0L,
        title = title,
        autoPlay = autoPlay,
    )
}

class UnifiedHistoryViewModel(application: Application) : AndroidViewModel(application) {

    init {

        MissAvHistoryRepo.init(application)
        HentaiMamaHistoryRepo.init(application)
        OnAirHistoryRepo.init(application)
        FapFolderHistoryRepo.init(application)
    }

    private fun <T> safe(flow: kotlinx.coroutines.flow.Flow<List<T>>) =
        flow.catch { emit(emptyList()) }

    val items: StateFlow<List<UnifiedHistoryItem>?> = combine(
        safe(DatabaseRepo.WatchHistory.loadAll().map { it.toList() }),
        safe(MissAvHistoryRepo.loadAll()),
        safe(HentaiMamaHistoryRepo.loadAll()),
        safe(OnAirHistoryRepo.loadAll()),
        safe(FapFolderHistoryRepo.loadAll()),
    ) { hanime, missav, mama, onair, fap ->
        buildList {
            hanime.forEach { add(it.toItem()) }
            missav.forEach {
                add(
                    UnifiedHistoryItem(
                        site = SiteType.MISSAV,
                        code = it.videoCode,
                        title = it.title,
                        coverUrl = it.coverUrl,
                        watchDate = it.watchDate,
                        positionMs = it.lastPosition,
                        durationMs = it.totalDuration,
                        path = "/en/${it.videoCode}",
                        subtitle = null,
                        completed = it.totalDuration > 0 &&
                                it.lastPosition >= it.totalDuration - 3_000L,
                    )
                )
            }
            mama.forEach {
                add(
                    UnifiedHistoryItem(
                        site = SiteType.HENTAIMAMA,
                        code = it.videoCode,
                        title = it.title,
                        coverUrl = it.coverUrl,
                        watchDate = it.watchDate,
                        positionMs = it.lastPosition,
                        durationMs = it.totalDuration,
                        path = it.lastEpisodeUrl.ifBlank { null },
                        subtitle = it.lastEpisodeTitle.ifBlank { null },
                        completed = it.completed,
                    )
                )
            }
            onair.forEach {
                add(
                    UnifiedHistoryItem(
                        site = SiteType.ONAIR,
                        code = it.videoId,
                        title = it.title,
                        coverUrl = it.coverUrl,
                        watchDate = it.watchDate,
                        positionMs = it.lastPosition,
                        durationMs = it.totalDuration,
                        path = null,
                        subtitle = it.uploaderName,
                        completed = it.completed,
                    )
                )
            }
            fap.forEach {
                add(
                    UnifiedHistoryItem(
                        site = SiteType.FAPFOLDER,
                        code = it.postId.toString(),
                        title = it.title,
                        coverUrl = it.coverUrl,
                        watchDate = it.watchDate,
                        positionMs = it.lastPosition,
                        durationMs = it.totalDuration,

                        path = it.postSlug?.ifBlank { null },
                        subtitle = it.authorName,
                        completed = it.completed,
                        autoPlay = it.isVideo,
                    )
                )
            }
        }.sortedByDescending { it.watchDate }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun WatchHistoryEntity.toItem() = UnifiedHistoryItem(
        site = SiteType.HANIME,
        code = videoCode,
        title = title,
        coverUrl = coverUrl,
        watchDate = watchDate,
        positionMs = 0L,
        durationMs = 0L,
        path = null,
        subtitle = null,
        completed = watched,
    )

    fun delete(item: UnifiedHistoryItem) {
        viewModelScope.launch {
            runCatching {
                when (item.site) {
                    SiteType.HANIME, SiteType.JAVCHU -> {
                        val entity = DatabaseRepo.WatchHistory.findBy(item.code)
                        if (entity != null) DatabaseRepo.WatchHistory.delete(entity)
                    }

                    SiteType.MISSAV -> MissAvHistoryRepo.deleteByVideoCode(item.code)
                    SiteType.HENTAIMAMA -> HentaiMamaHistoryRepo.deleteByVideoCode(item.code)
                    SiteType.ONAIR -> OnAirHistoryRepo.deleteByVideoId(item.code)
                    SiteType.FAPFOLDER -> item.code.toLongOrNull()?.let {
                        FapFolderHistoryRepo.deleteByPostId(it)
                    }
                }
            }
        }
    }

    fun clear(site: SiteType?) {
        viewModelScope.launch {
            runCatching {
                if (site == null || site == SiteType.HANIME) DatabaseRepo.WatchHistory.deleteAll()
                if (site == null || site == SiteType.MISSAV) MissAvHistoryRepo.deleteAll()
                if (site == null || site == SiteType.HENTAIMAMA) HentaiMamaHistoryRepo.deleteAll()
                if (site == null || site == SiteType.ONAIR) OnAirHistoryRepo.deleteAll()
                if (site == null || site == SiteType.FAPFOLDER) FapFolderHistoryRepo.deleteAll()
            }
        }
    }
}

private val filterSites = listOf(
    SiteType.HANIME,
    SiteType.MISSAV,
    SiteType.HENTAIMAMA,
    SiteType.ONAIR,
    SiteType.FAPFOLDER,
)

private fun siteBadge(site: SiteType): String = when (site) {
    SiteType.HANIME, SiteType.JAVCHU -> "Hanime / Javchu"
    else -> CrossSiteNavigator.siteLabel(site)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedHistoryScreen(
    activity: MainActivity,
    onBack: () -> Unit,
    viewModel: UnifiedHistoryViewModel = viewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf<SiteType?>(null) }
    var itemToDelete by remember { mutableStateOf<UnifiedHistoryItem?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val visible = remember(items, filter) {
        items?.filter { filter == null || it.site == filter }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.site_watch_history_all_sites)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showClearConfirm = true },
                        enabled = !visible.isNullOrEmpty(),
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.site_clear_history))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filter == null,
                    onClick = { filter = null },
                    label = { Text(stringResource(R.string.site_all)) },
                )
                filterSites.forEach { site ->
                    FilterChip(
                        selected = filter == site,
                        onClick = { filter = if (filter == site) null else site },
                        label = { Text(siteBadge(site)) },
                    )
                }
            }

            when {
                visible == null -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                visible.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.site_nothing_watched_yet),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visible, key = { it.key }) { item ->
                        UnifiedHistoryRow(
                            item = item,
                            onClick = { activity.requestCrossSiteOpen(item.toRequest()) },
                            onDelete = { itemToDelete = item },
                        )
                    }
                }
            }
        }
    }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.site_remove_from_history)) },
            text = { Text(item.title.ifBlank { item.code }) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(item)
                    itemToDelete = null
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { itemToDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.site_clear_history_2)) },
            text = {
                Text(
                    if (filter == null) stringResource(R.string.site_this_removes_the_watch_history_of)
                    else stringResource(R.string.site_this_removes_the_watch_history_of_2, siteBadge(filter!!))
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clear(filter)
                    showClearConfirm = false
                }) { Text(stringResource(R.string.site_clear)) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun UnifiedHistoryRow(
    item: UnifiedHistoryItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateText = remember(item.watchDate) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(item.watchDate))
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = item.coverUrl.ifBlank { null },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(112.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title.ifBlank { item.code },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${siteBadge(item.site)} · $dateText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.progress?.let { progress ->
                    LinearProgressIndicator(
                        progress = { if (item.completed) 1f else progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove))
            }
        }
    }
}
