package com.yenaly.han1meviewer.OnAir.ui.tag

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.OnAir.data.model.OnAirPageInfo
import com.yenaly.han1meviewer.OnAir.data.model.OnAirTag
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetworkRepo
import com.yenaly.han1meviewer.OnAir.ui.home.InfiniteScrollFooter
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent
import com.yenaly.han1meviewer.ui.component.lazy.LazyVerticalGrid
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnAirTagsIndexScreen(
    onBack: () -> Unit,
    onOpenTag: (String) -> Unit,
    onLoadFailure: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    var tags by remember { mutableStateOf<List<OnAirTag>>(emptyList()) }
    var state by remember { mutableStateOf<PageLoadingState<*>?>(null) }
    var pageInfo by remember { mutableStateOf<OnAirPageInfo?>(null) }
    var loadedPage by remember { mutableIntStateOf(1) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("popular") }

    fun load(page: Int, replace: Boolean) {
        if (isLoadingMore) return
        isLoadingMore = true
        if (replace) state = PageLoadingState.Loading
        scope.launch {
            OnAirNetworkRepo.getTagsPage(
                page = page,
                query = query.takeIf { it.isNotBlank() },
                sort = sortMode,
            ).collect { s ->
                state = s
                when (s) {
                    is PageLoadingState.Success -> {
                        val pair = s.info as Pair<List<OnAirTag>, OnAirPageInfo?>
                        tags = if (replace) pair.first
                        else (tags + pair.first).distinctBy { it.name }
                        pageInfo = pair.second
                        loadedPage = page
                        isLoadingMore = false
                    }
                    is PageLoadingState.NoMoreData,
                    is PageLoadingState.Error -> isLoadingMore = false
                    is PageLoadingState.Loading -> Unit
                }
            }
        }
    }

    LaunchedEffect(Unit) { load(1, replace = true) }

    LaunchedEffect(gridState, isLoadingMore, pageInfo) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            total to last
        }.distinctUntilChanged().collect { (total, last) ->
            if (total > 4 && last >= total - 4 && !isLoadingMore && pageInfo != null) {
                load(loadedPage + 1, replace = false)
            }
        }
    }

    LaunchedEffect(state) {
        val s = state
        if (s is PageLoadingState.Error && tags.isEmpty()) {
            onLoadFailure(s.throwable.message ?: "Tags load failed")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tags") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { load(1, replace = true) }),
                        decorationBox = { inner ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Box(modifier = Modifier.padding(start = 8.dp)) {
                                    if (query.isEmpty()) Text(
                                        text = "Find a tag…",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    inner()
                                }
                            }
                        },
                    )
                }
                IconButton(onClick = { load(1, replace = true) }) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = sortMode == "popular",
                    onClick = {
                        if (sortMode != "popular") {
                            sortMode = "popular"
                            load(1, replace = true)
                        }
                    },
                    label = { Text("Most videos") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
                FilterChip(
                    selected = sortMode == "alpha",
                    onClick = {
                        if (sortMode != "alpha") {
                            sortMode = "alpha"
                            load(1, replace = true)
                        }
                    },
                    label = { Text("A-Z") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }

            when {
                state is PageLoadingState.Loading && tags.isEmpty() -> LoadingContent()
                state is PageLoadingState.Error && tags.isEmpty() -> {
                    val err = state as PageLoadingState.Error
                    ErrorContent(
                        message = err.throwable.message ?: "Failed",
                        onRetry = { load(1, replace = true) },
                    )
                }
                tags.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tags found")
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    enableItemAnimation = false,
                ) {
                    items(tags, key = { it.name }) { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            onClick = { onOpenTag(tag.name) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                tag.count?.let {
                                    Text(
                                        text = " $it",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    if (tags.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            InfiniteScrollFooter(isLoadingMore = isLoadingMore)
                        }
                    }
                }
            }
        }
    }
}
