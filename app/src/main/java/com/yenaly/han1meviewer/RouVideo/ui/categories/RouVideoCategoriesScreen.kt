package com.yenaly.han1meviewer.RouVideo.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.RouVideo.common.RouVideoTagLabels
import com.yenaly.han1meviewer.RouVideo.common.RouVideoImageLoader
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoTagCount
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoTaxonomy
import com.yenaly.han1meviewer.RouVideo.data.remote.RouVideoRepo
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteErrorContent
import com.yenaly.han1meviewer.ui.component.SiteLoadingContent
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import com.yenaly.han1meviewer.ui.component.SiteVideoSectionHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RouVideoCategoriesState {
    data object Loading : RouVideoCategoriesState
    data class Success(val taxonomy: RouVideoTaxonomy) : RouVideoCategoriesState
    data class Error(val message: String) : RouVideoCategoriesState
}

class RouVideoCategoriesViewModel : ViewModel() {

    private val _state = MutableStateFlow<RouVideoCategoriesState>(RouVideoCategoriesState.Loading)
    val state: StateFlow<RouVideoCategoriesState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = RouVideoCategoriesState.Loading
        viewModelScope.launch {
            RouVideoRepo.categories()
                .onSuccess { _state.value = RouVideoCategoriesState.Success(it) }
                .onFailure {
                    _state.value =
                        RouVideoCategoriesState.Error(it.message ?: it.javaClass.simpleName)
                }
        }
    }
}

private const val STUDIOS_COLLAPSED = 24

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouVideoCategoriesScreen(
    onBack: () -> Unit,
    onOpenTag: (String) -> Unit,
    onSearch: (String) -> Unit,
    viewModel: RouVideoCategoriesViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text(stringResource(R.string.rouvideo_categories)) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        when (val s = state) {
            RouVideoCategoriesState.Loading -> SiteLoadingContent(Modifier.padding(padding))
            is RouVideoCategoriesState.Error -> SiteErrorContent(
                message = s.message,
                onRetry = { viewModel.load() },
                modifier = Modifier.padding(padding),
            )

            is RouVideoCategoriesState.Success -> {
                val tax = s.taxonomy
                val needle = filter.trim()
                fun matches(tag: String) = needle.isEmpty() || RouVideoTagLabels.matches(tag, needle)

                val cats = tax.cats.filter { matches(it.id) }
                val genres = tax.genre.filter { matches(it.id) }
                val parents = tax.byParent.mapValues { (_, list) -> list.filter { matches(it.id) } }
                    .filterValues { it.isNotEmpty() }
                val nothing = cats.isEmpty() && genres.isEmpty() && parents.isEmpty()

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        OutlinedTextField(
                            value = filter,
                            onValueChange = { filter = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.rouvideo_filter_tags)) },
                            trailingIcon = {
                                if (filter.isNotEmpty()) {
                                    IconButton(onClick = { filter = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = null)
                                    }
                                }
                            },
                        )
                    }
                    if (nothing) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column {
                                Text(
                                    text = stringResource(R.string.rouvideo_no_matching_tags),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                TextButton(onClick = { onSearch(needle) }) {
                                    Text(stringResource(R.string.rouvideo_search_for, needle))
                                }
                            }
                        }
                    }
                    if (cats.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SiteVideoSectionHeader(title = stringResource(R.string.rouvideo_main_categories))
                        }
                        items(cats, key = { "cat_" + it.id }) { cat ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                tonalElevation = 2.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 10f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onOpenTag(cat.id) },
                            ) {
                                Box {
                                    if (!cat.coverUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = cat.coverUrl,
                                            contentDescription = RouVideoTagLabels.label(cat.id),
                                            imageLoader = RouVideoImageLoader.get(context),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .fillMaxWidth()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                                                )
                                            )
                                            .padding(10.dp),
                                    ) {
                                        Text(
                                            text = RouVideoTagLabels.label(cat.id),
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text = if (cat.kids > 0) {
                                                stringResource(R.string.rouvideo_category_count_kids, cat.count, cat.kids)
                                            } else {
                                                stringResource(R.string.rouvideo_category_count, cat.count)
                                            },
                                            color = Color.White.copy(alpha = 0.85f),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (genres.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SiteVideoSectionHeader(title = stringResource(R.string.rouvideo_genres))
                                TagChips(genres, onOpenTag)
                            }
                        }
                    }
                    parents.forEach { (parent, studios) ->
                        item(key = "parent_$parent", span = { GridItemSpan(maxLineSpan) }) {
                            val open = parent in expanded || needle.isNotEmpty()
                            val shown = if (open) studios else studios.take(STUDIOS_COLLAPSED)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SiteVideoSectionHeader(
                                    title = stringResource(R.string.rouvideo_studios_of, RouVideoTagLabels.label(parent)),
                                    actionText = if (!open && studios.size > STUDIOS_COLLAPSED) {
                                        stringResource(R.string.rouvideo_show_all, studios.size)
                                    } else null,
                                    onActionClick = { expanded = expanded + parent },
                                )
                                TagChips(shown, onOpenTag)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagChips(tags: List<RouVideoTagCount>, onOpenTag: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEach { tag ->
            AssistChip(
                onClick = { onOpenTag(tag.id) },
                label = {
                    val name = RouVideoTagLabels.label(tag.id)
                    Text(if (tag.count > 0) "$name · ${tag.count}" else name)
                },
            )
        }
    }
}
