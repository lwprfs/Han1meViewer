package com.yenaly.han1meviewer.SupJav.ui.search

import com.yenaly.han1meviewer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableIntStateOf
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.ui.component.CrossSiteHit
import com.yenaly.han1meviewer.ui.component.CrossSiteResultsGrid
import com.yenaly.han1meviewer.ui.component.CrossSiteSearch
import com.yenaly.han1meviewer.ui.component.CrossSiteState
import com.yenaly.han1meviewer.ui.component.CrossSiteToggle
import com.yenaly.han1meviewer.ui.navigation.PendingVideoOpen
import com.yenaly.han1meviewer.SupJav.common.SupJavConstants
import com.yenaly.han1meviewer.SupJav.ui.home.SupJavListViewModel
import com.yenaly.han1meviewer.SupJav.ui.home.SupJavSource
import com.yenaly.han1meviewer.SupJav.ui.home.SupJavVideoGrid
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar

@Composable
fun SupJavSearchScreen(
    initialQuery: String?,
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    onOpenOtherSite: (PendingVideoOpen) -> Unit = {},
    viewModel: SupJavListViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(initialQuery.orEmpty()) }
    var submitted by rememberSaveable { mutableStateOf(initialQuery.orEmpty()) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }

    val context = LocalContext.current
    var crossEnabled by remember { mutableStateOf(false) }
    var crossState by remember { mutableStateOf<CrossSiteState>(CrossSiteState.Idle) }
    var crossRetry by remember { mutableIntStateOf(0) }
    val queryIsCode = CrossSiteSearch.looksLikeJavCode(query)
    val crossActive = crossEnabled && CrossSiteSearch.looksLikeJavCode(submitted)

    LaunchedEffect(queryIsCode) { if (!queryIsCode) crossEnabled = false }

    LaunchedEffect(crossEnabled, submitted, crossRetry) {
        if (!crossEnabled || !CrossSiteSearch.looksLikeJavCode(submitted)) {
            crossState = CrossSiteState.Idle
            return@LaunchedEffect
        }
        crossState = CrossSiteState.Loading
        crossState = CrossSiteSearch.search(context, SiteType.MISSAV, submitted)
    }

    LaunchedEffect(submitted) {
        if (submitted.isBlank()) return@LaunchedEffect

        val code = SupJavConstants.parseVideoReference(submitted)
        if (code != null) {
            onNavigateToVideo(code)
            submitted = ""
        } else {
            viewModel.setSource(SupJavSource.Search(submitted))
        }
    }
    LaunchedEffect(Unit) {
        if (initialQuery.isNullOrBlank()) focus.requestFocus()
    }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.supjav_search_hint)) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            submitted = query.trim()
                            keyboard?.hide()
                        }),
                    )
                },
                navigationIcon = { SiteBackButton(onClick = onBack) },
                actions = {
                    IconButton(onClick = {
                        submitted = query.trim()
                        keyboard?.hide()
                    }) {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            if (queryIsCode) {
                CrossSiteToggle(
                    otherSite = SiteType.MISSAV,
                    enabled = crossEnabled,
                    state = crossState,
                    onToggle = { crossEnabled = it },
                )
            }
            if (submitted.isBlank()) {
                Text(
                    text = stringResource(R.string.supjav_search_prompt),
                    modifier = Modifier.padding(16.dp),
                )
            } else if (crossActive && !(state.items.isEmpty() && state.isLoading)) {
                CrossSiteResultsGrid(
                    ownSite = SiteType.SUPJAV,
                    ownHits = state.items.map {
                        CrossSiteHit(SiteType.SUPJAV, it.title, it.coverUrl, it.videoCode)
                    },
                    otherSite = SiteType.MISSAV,
                    otherState = crossState,
                    isLoadingMore = state.isLoadingMore,
                    onReachEnd = { viewModel.loadMore() },
                    onOpen = { hit ->
                        if (hit.site == SiteType.SUPJAV) onNavigateToVideo(hit.videoCode)
                        else onOpenOtherSite(hit.toOpenRequest())
                    },
                    onRetryOther = { crossRetry++ },
                )
            } else {
                SupJavVideoGrid(
                    state = state,
                    onRetry = { viewModel.refresh() },
                    onLoadMore = { viewModel.loadMore() },
                    onOpenVideo = onNavigateToVideo,
                    emptyHint = stringResource(R.string.supjav_no_results),
                )
            }
        }
    }
}
