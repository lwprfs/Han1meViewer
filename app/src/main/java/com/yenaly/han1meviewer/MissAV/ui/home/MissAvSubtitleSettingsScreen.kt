package com.yenaly.han1meviewer.MissAV.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar

data class MissAvSubtitleSourceOption(
    val value: String,
    val label: String,
    val description: String,
)

object MissAvSubtitleSettingsRepo {
    const val SOURCE_SUBTITLECAT = "subtitlecat"
    const val SOURCE_SUBDOG = "subdog"

    val sources = listOf(
        MissAvSubtitleSourceOption(
            SOURCE_SUBTITLECAT,
            "SubtitleCat (English)",
            "Search subtitlecat.com and download the English subtitle",
        ),
        MissAvSubtitleSourceOption(
            SOURCE_SUBDOG,
            "Sub-Dog (Chinese)",
            "Chinese subtitles from the Sub-Dog (字幕狗) library",
        ),
    )

    fun source(): String =
        sources.firstOrNull { it.value == Preferences.missAvSubtitleSource }?.value
            ?: SOURCE_SUBTITLECAT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissAvSubtitleSettingsScreen(onBack: () -> Unit) {
    var source by remember { mutableStateOf(MissAvSubtitleSettingsRepo.source()) }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text("Video Subtitle") },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionLabel("Subtitle source")
            MissAvSubtitleSettingsRepo.sources.forEach { option ->
                OptionRow(
                    title = option.label,
                    description = option.description,
                    selected = source == option.value,
                    onClick = {
                        source = option.value
                        Preferences.missAvSubtitleSource = option.value
                    },
                )
            }

        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun OptionRow(
    title: String,
    description: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
