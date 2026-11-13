package com.yenaly.han1meviewer.ui.screen.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.local.LocalPlaylist
import com.yenaly.han1meviewer.logic.local.LocalPlaylistItem
import com.yenaly.han1meviewer.logic.local.LocalPlaylistStore
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import com.yenaly.han1meviewer.ui.navigation.PendingVideoOpen
import com.yenaly.han1meviewer.ui.screen.RetryableImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource

/** Local playlists screen: list of playlists, tap one to see and open its videos (any site). */
@Composable
fun LocalPlaylistsScreen(
    activity: MainActivity,
    onBack: () -> Unit,
) {
    val playlists by LocalPlaylistStore.playlists.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<LocalPlaylist?>(null) }
    var deleting by remember { mutableStateOf<LocalPlaylist?>(null) }
    val open = playlists.firstOrNull { it.id == openId }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SiteTopAppBar(
                title = { Text(open?.name ?: stringResource(R.string.local_playlists)) },
                navigationIcon = { SiteBackButton(onClick = { if (open != null) openId = null else onBack() }) },
            )
        },
    ) { padding ->
        if (open == null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "create") {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { creating = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Default.Add, null)
                            Text(stringResource(R.string.local_playlist_create))
                        }
                    }
                }
                if (playlists.isEmpty()) item(key = "empty") {
                    Text(
                        stringResource(R.string.local_playlist_empty),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(playlists, key = { it.id }) { p ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { openId = p.id },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    stringResource(R.string.local_playlist_count, p.items.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { renaming = p }) { Icon(Icons.Default.Edit, null) }
                            IconButton(onClick = { deleting = p }) { Icon(Icons.Default.Delete, null) }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (open.items.isEmpty()) item(key = "empty") {
                    Text(
                        stringResource(R.string.local_playlist_empty_items),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(open.items, key = { it.key }) { item ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            val site = item.siteType ?: return@clickable
                            activity.requestCrossSiteOpen(
                                PendingVideoOpen(site = site, videoCode = item.videoCode, title = item.title),
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RetryableImage(
                                model = item.coverUrl ?: "",
                                modifier = Modifier.size(width = 112.dp, height = 63.dp),
                                placeholder = painterResource(R.drawable.h_chan_loading),
                                error = painterResource(R.drawable.h_chan_load_failed),
                                contentScale = ContentScale.Crop,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                Text(item.site, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { LocalPlaylistStore.remove(open.id, item) }) {
                                Icon(Icons.Default.Delete, null)
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) NameDialog(null, { creating = false }) { LocalPlaylistStore.create(it) }
    renaming?.let { p -> NameDialog(p.name, { renaming = null }) { LocalPlaylistStore.rename(p.id, it) } }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(p.name) },
            confirmButton = { TextButton(onClick = { LocalPlaylistStore.delete(p.id); deleting = null }) { Text(stringResource(R.string.confirm)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun NameDialog(initial: String?, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.local_playlist_create else R.string.local_playlist_rename)) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.local_playlist_name)) },
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text); onDismiss() }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** "Add to playlist" dialog used by every site's video page. */
@Composable
fun LocalPlaylistPickerDialog(
    item: LocalPlaylistItem,
    onDismiss: () -> Unit,
) {
    val playlists by LocalPlaylistStore.playlists.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(LocalPlaylistStore.containing(item)) }
    var creating by remember { mutableStateOf(false) }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.local_playlist_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (playlists.isEmpty()) Text(stringResource(R.string.local_playlist_empty))
                playlists.forEach { p ->
                    val checked = p.id in selected
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = if (checked) selected - p.id else selected + p.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { selected = if (checked) selected - p.id else selected + p.id })
                        Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Default.Add, null)
                    Text(stringResource(R.string.local_playlist_create))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                LocalPlaylistStore.setMembership(item, selected)
                android.widget.Toast.makeText(context, R.string.local_playlist_saved, android.widget.Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (creating) NameDialog(null, { creating = false }) { name ->
        selected = selected + LocalPlaylistStore.create(name)
    }
}
