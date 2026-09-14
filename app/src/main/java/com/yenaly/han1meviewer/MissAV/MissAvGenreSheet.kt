package com.yenaly.han1meviewer.MissAV

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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

/**
 * Two-level genre picker:
 *  1. If no group is selected → show list of groups (Quality, Relationship, …)
 *  2. If a group is selected → show its genres with a back button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissAvGenreSheet(
    initialGenre: String?,
    genreOptions: List<MissAvTag> = MissAvOptions.genreOptions,
    onDismiss: () -> Unit,
    onApply: (genre: String?) -> Unit,
    onReset: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedGenre by remember { mutableStateOf(initialGenre) }
    // Which group we're viewing. null = group list, non-null = inside that group.
    var currentGroup by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    // Auto-open the group containing the currently selected genre
    val initialSelected = remember(initialGenre, genreOptions) {
        genreOptions.firstOrNull { it.searchKey == initialGenre }?.group
    }
    // If a genre is already selected, start inside its group
    if (currentGroup == null && initialSelected != null && query.isBlank()) {
        // no-op — we let the user decide; open in group list for clarity
    }

    val groupNames = remember(genreOptions) {
        val present = genreOptions.mapNotNull { it.group }.distinct()
        val ordered = present.filter { it != MissAvOptions.OTHER_GROUP }
        ordered + listOfNotNull(
            MissAvOptions.OTHER_GROUP.takeIf { it in present }
        )
    }

    // Genres currently in view (either within a group, or search results)
    val visibleGenres = remember(currentGroup, query, genreOptions) {
        val base = if (currentGroup != null) {
            genreOptions.filter { it.group == currentGroup }
        } else {
            genreOptions
        }
        if (query.isBlank()) base.sortedBy { it.name }
        else base.filter { it.name.contains(query, ignoreCase = true) }.sortedBy { it.name }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            // ── Header ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (currentGroup != null) {
                    IconButton(onClick = {
                        currentGroup = null
                        query = ""
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to groups",
                        )
                    }
                }
                Text(
                    text = currentGroup ?: "Browse Genre",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    selectedGenre = null
                    currentGroup = null
                    query = ""
                    onReset()
                }) {
                    Text("Reset")
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Search box (always visible) ──────────────────────────────
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    // If the user starts typing, search across all groups
                    if (it.isNotBlank()) currentGroup = null
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search genres…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
            )

            Spacer(Modifier.height(12.dp))

            // ── Body ─────────────────────────────────────────────────────
            when {
                // Case 1: searching — flat list, grouped headers inline
                query.isNotBlank() -> {
                    if (visibleGenres.isEmpty()) {
                        EmptyHint("No matching genres")
                    } else {
                        GenreLazyList(
                            genres = visibleGenres,
                            selectedKey = selectedGenre,
                            showGroupInLabel = true,
                            onPick = { tag ->
                                selectedGenre = if (selectedGenre == tag.searchKey) null else tag.searchKey
                            },
                        )
                    }
                }

                // Case 2: inside a group — flat list of genres in that group
                currentGroup != null -> {
                    if (visibleGenres.isEmpty()) {
                        EmptyHint("No genres in this group")
                    } else {
                        GenreLazyList(
                            genres = visibleGenres,
                            selectedKey = selectedGenre,
                            showGroupInLabel = false,
                            onPick = { tag ->
                                selectedGenre = if (selectedGenre == tag.searchKey) null else tag.searchKey
                            },
                        )
                    }
                }

                // Case 3: group list — one row per group with count + selected indicator
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(groupNames, key = { it }) { group ->
                            val genresInGroup = genreOptions.filter { it.group == group }
                            val selectedInGroup = genresInGroup.any { it.searchKey == selectedGenre }
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = group,
                                        fontWeight = if (selectedInGroup) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedInGroup) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = "${genresInGroup.size} genres",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                trailingContent = {
                                    if (selectedInGroup) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Has selection",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    } else {
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = if (selectedInGroup) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        currentGroup = group
                                        query = ""
                                    },
                            )
                        }
                    }
                }
            }

            // ── Show currently selected genre ────────────────────────────
            selectedGenre?.let { key ->
                val label = MissAvOptions.genreLabel(key) ?: key
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Selected: ",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { selectedGenre = null },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear selection",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ── Action bar ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onApply(selectedGenre) },
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("Apply", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun GenreLazyList(
    genres: List<MissAvTag>,
    selectedKey: String?,
    showGroupInLabel: Boolean,
    onPick: (MissAvTag) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(genres, key = { it.searchKey }) { tag ->
            val selected = tag.searchKey == selectedKey
            FilterChip(
                selected = selected,
                onClick = { onPick(tag) },
                label = {
                    Text(
                        text = if (showGroupInLabel && tag.group != null) {
                            "${tag.group} › ${tag.name}"
                        } else {
                            tag.name
                        }
                    )
                },
                leadingIcon = if (selected) {
                    {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}