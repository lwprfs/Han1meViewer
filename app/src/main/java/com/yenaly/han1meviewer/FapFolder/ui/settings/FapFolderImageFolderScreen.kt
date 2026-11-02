package com.yenaly.han1meviewer.FapFolder.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import com.yenaly.han1meviewer.util.FapFolderImageLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class PhotoEntry(
    val docId: String,
    val name: String,
    val isDir: Boolean,
    val uri: Uri,
    val modified: Long,
)

private data class FolderCrumb(val docId: String, val name: String)

private fun listChildren(context: Context, tree: Uri, parentDocId: String): List<PhotoEntry> {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
    val out = ArrayList<PhotoEntry>()
    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )
    runCatching {
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1).orEmpty()
                val mime = c.getString(2).orEmpty()
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                if (!isDir && !mime.startsWith("image/")) continue
                out += PhotoEntry(
                    docId = id,
                    name = name,
                    isDir = isDir,
                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                    modified = c.getLong(3),
                )
            }
        }
    }
    return out
}

/** All images below [rootDocId], newest first, capped so a huge library can't freeze the screen. */
private fun listAllImages(context: Context, tree: Uri, rootDocId: String, cap: Int = 4000): List<PhotoEntry> {
    val result = ArrayList<PhotoEntry>()
    val queue = ArrayDeque<String>()
    queue.add(rootDocId)
    while (queue.isNotEmpty() && result.size < cap) {
        val dir = queue.removeFirst()
        for (e in listChildren(context, tree, dir)) {
            if (e.isDir) queue.add(e.docId) else result += e
            if (result.size >= cap) break
        }
    }
    return result.sortedByDescending { it.modified }
}

/**
 * Downloaded FapFolder photos: browse the download folder, view every photo, or flatten the whole
 * library. The download-folder settings live here too (gear icon).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FapFolderImageFolderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    val crumbs = remember { mutableStateListOf<FolderCrumb>() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { tree ->
        if (tree != null) {
            FapFolderImageLocation.persist(context, tree)
            FapFolderImageLocation.confirmedThisSession = true
            crumbs.clear()
            refresh++
        }
    }

    val ready = refresh >= 0 && FapFolderImageLocation.isReady(context)
    val treeUri = remember(refresh) { FapFolderImageLocation.uri?.let { runCatching { it.toUri() }.getOrNull() } }
    val rootDocId = remember(treeUri) {
        treeUri?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
    }
    val currentDocId = crumbs.lastOrNull()?.docId ?: rootDocId

    var loading by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<PhotoEntry>>(emptyList()) }

    LaunchedEffect(ready, treeUri, currentDocId, showAll, refresh) {
        if (!ready || treeUri == null || currentDocId == null) {
            entries = emptyList()
            return@LaunchedEffect
        }
        loading = true
        entries = withContext(Dispatchers.IO) {
            if (showAll) listAllImages(context, treeUri, currentDocId)
            else listChildren(context, treeUri, currentDocId)
                .sortedWith(compareByDescending<PhotoEntry> { it.isDir }.thenBy { it.name.lowercase() })
        }
        loading = false
    }

    val photos = remember(entries) { entries.filter { !it.isDir } }

    BackHandler(enabled = viewerIndex != null || crumbs.isNotEmpty()) {
        when {
            viewerIndex != null -> viewerIndex = null
            crumbs.isNotEmpty() -> crumbs.removeAt(crumbs.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = {
                    Text(
                        text = crumbs.lastOrNull()?.name ?: "Downloaded photos",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    SiteBackButton(onClick = {
                        if (crumbs.isNotEmpty()) crumbs.removeAt(crumbs.lastIndex) else onBack()
                    })
                },
                actions = {
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(Icons.Default.Settings, contentDescription = "Photo download settings")
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
            if (showSettings || !ready) {
                Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Photo download settings", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "FapFolder photos are saved to their own folder, separate from the " +
                                    "video download location.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = if (ready) "Current folder:\n${FapFolderImageLocation.displayName(context)}"
                            else if (FapFolderImageLocation.uri != null) "Access to the saved folder was lost. Choose it again."
                            else "No folder chosen yet. You will be asked when you download a photo.",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Button(
                            onClick = { picker.launch(null) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (ready) "Change folder" else "Choose folder") }
                        if (FapFolderImageLocation.uri != null) {
                            OutlinedButton(
                                onClick = {
                                    FapFolderImageLocation.uri = null
                                    FapFolderImageLocation.confirmedThisSession = false
                                    crumbs.clear()
                                    refresh++
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Forget folder") }
                        }
                    }
                }
            }

            if (ready) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !showAll,
                        onClick = { showAll = false },
                        label = { Text("Folders") },
                    )
                    FilterChip(
                        selected = showAll,
                        onClick = { showAll = true },
                        label = { Text("All photos") },
                    )
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    if (!loading && entries.isEmpty()) {
                        Text(
                            text = "No downloaded photos here yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(110.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (!showAll) {
                            items(
                                entries.filter { it.isDir },
                                key = { "d:" + it.docId },
                                span = { GridItemSpan(maxLineSpan) },
                            ) { dir ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { crumbs.add(FolderCrumb(dir.docId, dir.name)) }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null)
                                    Text(dir.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        items(photos, key = { "p:" + it.docId }) { photo ->
                            AsyncImage(
                                model = photo.uri,
                                contentDescription = photo.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { viewerIndex = photos.indexOf(photo) },
                            )
                        }
                    }
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(36.dp),
                        )
                    }
                }
            }
        }
    }

    val startIndex = viewerIndex
    if (startIndex != null && photos.isNotEmpty()) {
        Dialog(
            onDismissRequest = { viewerIndex = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            val pagerState = rememberPagerState(
                initialPage = startIndex.coerceIn(0, photos.lastIndex),
                pageCount = { photos.size },
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { viewerIndex = null },
            ) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    AsyncImage(
                        model = photos[page].uri,
                        contentDescription = photos[page].name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(
                    text = "${pagerState.currentPage + 1} / ${photos.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                )
            }
        }
    }
}
