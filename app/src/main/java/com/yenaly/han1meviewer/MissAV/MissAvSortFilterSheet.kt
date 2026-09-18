package com.yenaly.han1meviewer.MissAV

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissAvSortFilterSheet(
    initialSort: String?,
    initialFilter: String?,
    sortOptions: List<MissAvTag> = MissAvOptions.sortOptions,
    filterOptions: List<MissAvTag> = MissAvOptions.filterOptions,
    onDismiss: () -> Unit,
    onApply: (sort: String?, filter: String?) -> Unit,
    onReset: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(
            SheetValue.Hidden,
            SheetValue.PartiallyExpanded,
            SheetValue.Expanded,
        ),
    )

    var selectedTab by remember { mutableStateOf(MissAvGroup.SORT) }
    var sort by remember { mutableStateOf(initialSort) }
    var filter by remember { mutableStateOf(initialFilter) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Sort & Filter",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    sort = null
                    filter = null
                    onReset()
                }) {
                    Text("Reset")
                }
            }

            Spacer(Modifier.height(8.dp))

            PrimaryTabRow(selectedTabIndex = if (selectedTab == MissAvGroup.SORT) 0 else 1) {
                Tab(
                    selected = selectedTab == MissAvGroup.SORT,
                    onClick = { selectedTab = MissAvGroup.SORT },
                    text = {
                        Text(if (sort != null) "Sort •" else "Sort")
                    },
                )
                Tab(
                    selected = selectedTab == MissAvGroup.FILTER,
                    onClick = { selectedTab = MissAvGroup.FILTER },
                    text = {
                        Text(if (filter != null) "Filter •" else "Filter")
                    },
                )
            }

            Spacer(Modifier.height(12.dp))

            when (selectedTab) {
                MissAvGroup.SORT -> TagSingleSelectList(
                    options = sortOptions,
                    selectedKey = sort,
                    onSelect = { sort = if (sort == it) null else it },
                )
                MissAvGroup.FILTER -> TagSingleSelectList(
                    options = filterOptions,
                    selectedKey = filter,
                    onSelect = { filter = if (filter == it) null else it },
                )
                else -> Unit
            }

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
                    onClick = { onApply(sort, filter) },
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Apply", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
internal fun TagSingleSelectList(
    options: List<MissAvTag>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(options, key = { it.searchKey }) { option ->
            val selected = option.searchKey == selectedKey
            FilterChip(
                selected = selected,
                onClick = { onSelect(option.searchKey) },
                label = { Text(option.name) },
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
