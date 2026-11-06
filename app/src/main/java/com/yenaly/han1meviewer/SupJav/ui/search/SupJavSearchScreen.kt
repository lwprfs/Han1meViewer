package com.yenaly.han1meviewer.SupJav.ui.search

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
    viewModel: SupJavListViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(initialQuery.orEmpty()) }
    var submitted by rememberSaveable { mutableStateOf(initialQuery.orEmpty()) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }

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
                        placeholder = { Text("Search SupJav") },
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
            if (submitted.isBlank()) {
                Text(
                    text = "Type a title, code or actress name, or paste a SupJav link / id:12345",
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                SupJavVideoGrid(
                    state = state,
                    onRetry = { viewModel.refresh() },
                    onLoadMore = { viewModel.loadMore() },
                    onOpenVideo = onNavigateToVideo,
                    emptyHint = "No results",
                )
            }
        }
    }
}
