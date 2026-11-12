package com.yenaly.han1meviewer.SupJav.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.ui.component.SitePullToRefresh
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.SupJav.common.SupJavConstants
import com.yenaly.han1meviewer.ui.component.SiteBarIconButton
import com.yenaly.han1meviewer.ui.component.SiteChipRow
import com.yenaly.han1meviewer.ui.component.SiteSearchTopBar
import com.yenaly.han1meviewer.ui.component.SiteSwitchButton

@Composable
fun SupJavHomeScreen(
    onNavigateToVideo: (String) -> Unit,
    onNavigateToSearch: (String?) -> Unit,
    onNavigateToHistory: () -> Unit,
    onOpenDrawer: () -> Unit,
    onSwitchSite: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SupJavListViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedKey by rememberSaveable { mutableStateOf(SupJavConstants.CATEGORIES.first().key) }
    val selected = SupJavConstants.CATEGORIES.first { it.key == selectedKey }

    LaunchedEffect(selectedKey) {
        viewModel.setSource(SupJavSource.Category(selected.path))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SiteSearchTopBar(
                hint = stringResource(R.string.supjav_search_hint),
                onSearchClick = { onNavigateToSearch(null) },
                leading = {
                    SiteBarIconButton(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(R.string.open_menu),
                        onClick = onOpenDrawer,
                    )
                },
                actions = {
                    SiteBarIconButton(
                        imageVector = Icons.Default.History,
                        contentDescription = stringResource(R.string.watch_history),
                        onClick = onNavigateToHistory,
                    )
                    SiteSwitchButton(onClick = onSwitchSite)
                },
            )
        },
    ) { padding ->
        Column(modifier = modifier
            .fillMaxSize()
            .padding(padding)) {
            SiteChipRow(
                options = SupJavConstants.CATEGORIES,
                selected = selected,
                label = { it.title },
                onSelect = { selectedKey = it.key },
                key = { it.key },
            )
            SitePullToRefresh(
                loading = state.isLoading,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                SupJavVideoGrid(
                    state = state,
                    onRetry = { viewModel.refresh() },
                    onLoadMore = { viewModel.loadMore() },
                    onOpenVideo = onNavigateToVideo,
                )
            }
        }
    }
}
