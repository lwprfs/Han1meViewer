package com.yenaly.han1meviewer.FapFolder.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderAutocompleteSuggestion
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderFeedItem
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderSearchTab
import com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderGroupRowCard
import com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderInteractivePostCard
import com.yenaly.han1meviewer.FapFolder.ui.components.FapFolderUserRowCard
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FapFolderSearchScreen(
    initialQuery: String?,
    initialHashtag: Boolean,
    initialTab: String,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    viewModel: FapFolderSearchViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    var queryText by remember { mutableStateOf(initialQuery ?: "") }

    LaunchedEffect(initialQuery, initialHashtag, initialTab) {
        viewModel.initialise(initialQuery, initialHashtag, initialTab)
    }

    LaunchedEffect(listState, uiState.hasMore, uiState.isLoadingMore, uiState.isLoading) {
        snapshotFlow {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            total to last
        }
            .distinctUntilChanged()
            .collect { (total, last) ->
                if (total > 4 &&
                    last >= total - 4 &&
                    uiState.hasMore &&
                    !uiState.isLoadingMore &&
                    !uiState.isLoading
                ) {
                    viewModel.loadMore()
                }
            }
    }

    fun submit() {
        viewModel.setQuery(queryText)
        viewModel.submit()
        viewModel.saveHistory()
        keyboard?.hide()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Search",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            OutlinedTextField(
                value = queryText,
                onValueChange = {
                    queryText = it
                    viewModel.setQuery(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("Search posts, users, or #hashtags") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (queryText.isNotBlank()) {
                        IconButton(onClick = {
                            queryText = ""
                            viewModel.setQuery("")
                            viewModel.hideAutocomplete()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
            )

            if (uiState.autocompleteVisible && uiState.autocompleteQuery.isNotBlank()) {
                AutocompleteDropdown(
                    rows = uiState.autocompleteRows,
                    loading = uiState.autocompleteLoading,
                    onPick = { suggestion ->
                        viewModel.submitAutocomplete(suggestion)
                        when (suggestion) {
                            is FapFolderAutocompleteSuggestion.Hashtag -> {
                                queryText = "#${suggestion.tag}"
                                onOpenHashtag(suggestion.tag)
                            }
                            is FapFolderAutocompleteSuggestion.SearchFor -> {
                                queryText = suggestion.query
                                submit()
                            }
                            is FapFolderAutocompleteSuggestion.User -> {
                                onOpenProfile(suggestion.username)
                                viewModel.hideAutocomplete()
                            }
                            is FapFolderAutocompleteSuggestion.Community -> {
                                onOpenGroup(suggestion.slug)
                                viewModel.hideAutocomplete()
                            }
                        }
                    },
                )
                return@Column
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TabChip(
                    label = "Posts",
                    selected = uiState.activeTab == FapFolderSearchTab.POSTS,
                    onClick = { viewModel.changeTab(FapFolderSearchTab.POSTS) },
                )
                TabChip(
                    label = "Users",
                    selected = uiState.activeTab == FapFolderSearchTab.USERS,
                    onClick = { viewModel.changeTab(FapFolderSearchTab.USERS) },
                )
                TabChip(
                    label = "Communities",
                    selected = uiState.activeTab == FapFolderSearchTab.COMMUNITIES,
                    onClick = { viewModel.changeTab(FapFolderSearchTab.COMMUNITIES) },
                )
            }

            if (uiState.activeTab == FapFolderSearchTab.POSTS) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = uiState.activeFilter == FapFolderConstants.FILTER_ALL,
                        onClick = { viewModel.changeFilter(FapFolderConstants.FILTER_ALL) },
                        label = { Text("All") },
                    )
                    FilterChip(
                        selected = uiState.activeFilter == FapFolderConstants.FILTER_PHOTOS,
                        onClick = { viewModel.changeFilter(FapFolderConstants.FILTER_PHOTOS) },
                        label = { Text("Photos") },
                    )
                    FilterChip(
                        selected = uiState.activeFilter == FapFolderConstants.FILTER_VIDEOS,
                        onClick = { viewModel.changeFilter(FapFolderConstants.FILTER_VIDEOS) },
                        label = { Text("Videos") },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.rawQuery.isBlank() && uiState.items.isEmpty() -> {
                        SearchHint(message = "Type a query and press Enter to search.")
                    }

                    uiState.isLoading && uiState.items.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }

                    uiState.errorMessage != null && uiState.items.isEmpty() -> {
                        SearchError(
                            message = uiState.errorMessage ?: "Search failed.",
                            onRetry = { viewModel.retry() },
                        )
                    }

                    uiState.empty && uiState.items.isEmpty() -> {
                        SearchHint(
                            message = "No results for ${queryText.ifBlank { "this query" }}.",
                        )
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                end = 8.dp,
                                top = 4.dp,
                                bottom = 24.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(uiState.items, key = { it.stableKey }) { item ->
                                when (item) {
                                    is FapFolderFeedItem.Post -> {
                                        val post = item.post
                                        FapFolderInteractivePostCard(
                                            post = post,
                                            onClickPost = {
                                                val slug = post.slug ?: post.postId.toString()
                                                onOpenPost(slug)
                                            },
                                            onClickHashtag = onOpenHashtag,
                                            onClickAuthor = { author ->
                                                val group = author.groupSlug
                                                val user = author.username
                                                when {
                                                    !group.isNullOrBlank() -> onOpenGroup(group)
                                                    !user.isNullOrBlank() -> onOpenProfile(user)
                                                }
                                            },
                                            onClickVideo = { },
                                            onClickImage = { _, _ -> },
                                            onClickPoll = { },
                                            onClickLink = { },
                                            onLongClickPost = { },
                                        )
                                    }

                                    is FapFolderFeedItem.User -> FapFolderUserRowCard(
                                        user = item.user,
                                        onClick = {
                                            val username = item.user.username
                                            if (username.isNotBlank()) onOpenProfile(username)
                                        },
                                    )

                                    is FapFolderFeedItem.Community -> FapFolderGroupRowCard(
                                        group = item.group,
                                        onClick = {
                                            val slug = item.group.slug
                                            if (slug.isNotBlank()) onOpenGroup(slug)
                                        },
                                    )
                                }
                            }
                            if (uiState.isLoadingMore) {
                                item(key = "__loading_more__") {
                                    Box(
                                        modifier = Modifier
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
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutocompleteDropdown(
    rows: List<FapFolderAutocompleteSuggestion>,
    loading: Boolean,
    onPick: (FapFolderAutocompleteSuggestion) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .heightIn(max = 360.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            if (loading && rows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            } else {
                rows.take(8).forEach { suggestion ->
                    AutocompleteRow(suggestion = suggestion, onClick = { onPick(suggestion) })
                }
            }
        }
    }
}

@Composable
private fun AutocompleteRow(
    suggestion: FapFolderAutocompleteSuggestion,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (suggestion) {
            is FapFolderAutocompleteSuggestion.User -> {
                AutocompleteAvatar(suggestion.avatarUrl, suggestion.label)
            }
            is FapFolderAutocompleteSuggestion.Community -> {
                AutocompleteAvatar(suggestion.avatarUrl, suggestion.label)
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when (suggestion) {
                is FapFolderAutocompleteSuggestion.User -> {
                    if (!suggestion.bio.isNullOrBlank()) {
                        Text(
                            text = suggestion.bio ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                is FapFolderAutocompleteSuggestion.Community -> {
                    suggestion.followerCount?.let { count ->
                        Text(
                            text = "$count followers",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is FapFolderAutocompleteSuggestion.Hashtag -> {
                    Text(
                        text = "Hashtag",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is FapFolderAutocompleteSuggestion.SearchFor -> {
                    Text(
                        text = if (suggestion.isHashtag) "Hashtag search" else "Search",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AutocompleteAvatar(avatar: String?, label: String) {
    if (!avatar.isNullOrBlank()) {
        AsyncImage(
            model = avatar,
            contentDescription = label,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
private fun SearchHint(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchError(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Search failed",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}
