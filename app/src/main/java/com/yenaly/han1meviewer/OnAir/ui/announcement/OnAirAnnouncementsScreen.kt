package com.yenaly.han1meviewer.OnAir.ui.announcement

import androidx.compose.ui.res.stringResource
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import com.yenaly.han1meviewer.ui.component.SiteEmptyContent
import com.yenaly.han1meviewer.ui.component.SiteLoadingContent
import com.yenaly.han1meviewer.ui.component.SiteErrorContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.OnAir.data.model.OnAirAnnouncementEntry
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetworkRepo
import com.yenaly.han1meviewer.logic.state.WebsiteState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnAirAnnouncementsScreen(
    onBack: () -> Unit,
) {
    var state by remember { mutableStateOf<WebsiteState<List<OnAirAnnouncementEntry>>>(WebsiteState.Loading) }

    LaunchedEffect(Unit) {
        OnAirNetworkRepo.getAnnouncements().collect { s -> state = s }
    }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text(stringResource(R.string.onair_announcements_title)) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when (val s = state) {
                is WebsiteState.Loading -> SiteLoadingContent()
                is WebsiteState.Error -> SiteErrorContent(
                    message = s.throwable.message ?: stringResource(R.string.site_failed),
                    onRetry = { },
                )
                is WebsiteState.Success -> {
                    if (s.info.isEmpty()) {
                        SiteEmptyContent(hint = stringResource(R.string.onair_no_announcements))
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(s.info, key = { "${it.title}-${it.author}" }) { entry ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    ),
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row {
                                            if (entry.isPinned) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(6.dp),
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.onair_pinned),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(
                                                            horizontal = 6.dp,
                                                            vertical = 2.dp,
                                                        ),
                                                    )
                                                }
                                                Spacer(Modifier.padding(4.dp))
                                            }
                                            Text(
                                                text = entry.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        entry.author?.let {
                                            Spacer(Modifier.padding(2.dp))
                                            Text(
                                                text = stringResource(R.string.fap_by_user, it),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Spacer(Modifier.padding(4.dp))
                                        Text(
                                            text = entry.body,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
