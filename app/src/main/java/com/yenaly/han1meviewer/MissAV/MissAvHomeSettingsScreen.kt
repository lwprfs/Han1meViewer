package com.yenaly.han1meviewer.MissAV

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissAvHomeSettingsScreen(
    viewModel: MissAvHomeViewModel,
    onBack: () -> Unit,
) {
    val categories by viewModel.allCategories.collectAsStateWithLifecycle()

    var showResetDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<MissAvHomeCategory?>(null) }
    var deletingCategory by remember { mutableStateOf<MissAvHomeCategory?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home Categories") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Reset to defaults") },
                                onClick = {
                                    showMenu = false
                                    showResetDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Restore, contentDescription = null)
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add category") },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Text(
                text = "Tap a row to toggle visibility. Use the edit icon to change title, path or sort. Reorder with the up/down arrows.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            if (categories.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No categories. Tap + to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 4.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(
                        items = categories,
                        key = { _, c -> c.key },
                    ) { index, category ->
                        CategoryEditorRow(
                            category = category,
                            isFirst = index == 0,
                            isLast = index == categories.lastIndex,
                            onToggleHidden = { visible ->
                                viewModel.setCategoryHidden(category.key, !visible)
                            },
                            onEdit = { editingCategory = category },
                            onDelete = { deletingCategory = category },
                            onMoveUp = {
                                viewModel.reorderCategory(index, index - 1)
                            },
                            onMoveDown = {
                                viewModel.reorderCategory(index, index + 1)
                            },
                        )
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset categories") },
            text = { Text("This will discard your customizations and restore the built-in category list. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetCategoriesToDefaults()
                    showResetDialog = false
                }) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showAddDialog) {
        CategoryEditDialog(
            title = "Add category",
            initial = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, genrePath, sort ->
                viewModel.addCategory(title, genrePath, sort)
                showAddDialog = false
            },
        )
    }

    editingCategory?.let { category ->
        CategoryEditDialog(
            title = "Edit category",
            initial = category,
            onDismiss = { editingCategory = null },
            onConfirm = { title, genrePath, sort ->
                viewModel.updateCategory(
                    category.copy(
                        title = title.trim(),
                        genrePath = genrePath.trim().removePrefix("/"),
                        sort = sort.trim().takeIf { it.isNotEmpty() },
                    )
                )
                editingCategory = null
            },
        )
    }

    deletingCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text("Delete category") },
            text = { Text("Remove \"${category.title}\" from the home screen? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCategory(category.key)
                    deletingCategory = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingCategory = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun CategoryEditorRow(
    category: MissAvHomeCategory,
    isFirst: Boolean,
    isLast: Boolean,
    onToggleHidden: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val visible = !category.hidden
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = category.genrePath +
                            (category.sort?.let { " • sort=$it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "key: ${category.key}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = !isFirst,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = "Move up",
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = !isLast,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = "Move down",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    modifier = Modifier.size(18.dp),
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }

            Switch(
                checked = visible,
                onCheckedChange = onToggleHidden,
            )
        }
    }
}

@Composable
private fun CategoryEditDialog(
    title: String,
    initial: MissAvHomeCategory?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, genrePath: String, sort: String) -> Unit,
) {
    var titleValue by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var genrePath by remember(initial) { mutableStateOf(initial?.genrePath.orEmpty()) }
    var sortValue by remember(initial) { mutableStateOf(initial?.sort.orEmpty()) }

    val canConfirm = titleValue.trim().isNotEmpty() && genrePath.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EditField(
                    label = "Title",
                    value = titleValue,
                    onValueChange = { titleValue = it },
                )
                EditField(
                    label = "Genre path (e.g. en/genres/Sister)",
                    value = genrePath,
                    onValueChange = { genrePath = it },
                    supportingText = "Remove any leading \"/\". Case matters.",
                )
                EditField(
                    label = "Sort (optional)",
                    value = sortValue,
                    onValueChange = { sortValue = it },
                    supportingText = "e.g. today_views, published_at, weekly_views",
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(titleValue, genrePath, sortValue)
                },
                enabled = canConfirm,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun EditField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    supportingText: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
