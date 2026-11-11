package com.yenaly.han1meviewer.XHamster.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yenaly.han1meviewer.XHamster.data.remote.XHamsterRepo
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.XHamster.common.XHamsterConstants
import com.yenaly.han1meviewer.XHamster.ui.home.XHamsterListViewModel
import com.yenaly.han1meviewer.XHamster.ui.home.XHamsterSource
import com.yenaly.han1meviewer.XHamster.ui.home.XHamsterVideoGrid
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar

@Composable
fun XHamsterCategoriesScreen(
    onBack: () -> Unit,
    onOpenCategory: (path: String, title: String) -> Unit,
) {

    var categories by remember {
        mutableStateOf(XHamsterConstants.CATEGORIES.map { it.path to it.title })
    }
    LaunchedEffect(Unit) {
        XHamsterRepo.categories().onSuccess { live ->
            if (live.isNotEmpty()) categories = live.map { it.path to it.name }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SiteTopAppBar(
                title = { Text(stringResource(R.string.xhamster_categories)) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(categories, key = { it.first }) { (path, title) ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCategory(path, title) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun XHamsterCategoryScreen(
    path: String,
    title: String,
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    viewModel: XHamsterListViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(path) { viewModel.setSource(XHamsterSource.Path(path)) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SiteTopAppBar(
                title = { Text(title) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            XHamsterVideoGrid(
                state = state,
                onRetry = { viewModel.refresh() },
                onLoadMore = { viewModel.loadMore() },
                onOpenVideo = onNavigateToVideo,
            )
        }
    }
}
