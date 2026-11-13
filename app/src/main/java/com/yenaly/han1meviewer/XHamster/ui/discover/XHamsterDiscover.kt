package com.yenaly.han1meviewer.XHamster.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.XHamster.common.XHamsterConstants
import com.yenaly.han1meviewer.XHamster.common.XHamsterImageLoader
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterChannel
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterCreator
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterFeedItem
import com.yenaly.han1meviewer.XHamster.data.remote.XHamsterAuth
import com.yenaly.han1meviewer.XHamster.data.remote.XHamsterRepo
import com.yenaly.han1meviewer.XHamster.data.remote.XHamsterUserRepo
import com.yenaly.han1meviewer.ui.screen.RetryableImage
import com.yenaly.han1meviewer.ui.component.SiteVideoCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Navigation hooks used by the "top creators / channels / subscriptions" strips. */
data class XHamsterDiscoverActions(
    val onOpenCreator: (path: String, title: String) -> Unit,
    val onOpenChannel: (path: String, title: String) -> Unit,
    val onOpenVideo: (String) -> Unit,
    val onSeeCreators: () -> Unit,
    val onSeeChannels: () -> Unit,
    val onSeeSubscriptions: () -> Unit,
)

data class XHamsterDiscoverState(
    val creators: List<XHamsterCreator> = emptyList(),
    val channels: List<XHamsterChannel> = emptyList(),
    val feed: List<XHamsterFeedItem> = emptyList(),
)

class XHamsterDiscoverViewModel : ViewModel() {
    private val _state = MutableStateFlow(cache ?: XHamsterDiscoverState())
    val state: StateFlow<XHamsterDiscoverState> = _state.asStateFlow()

    init {
        if (cache == null) {
            viewModelScope.launch {
                val creators = XHamsterRepo.creators(XHamsterConstants.listUrl("/creators"))
                    .getOrNull()?.items.orEmpty().take(24)
                _state.value = _state.value.copy(creators = creators)
            }
            viewModelScope.launch {
                val channels = XHamsterRepo.channels(XHamsterConstants.listUrl("/channels"))
                    .getOrNull()?.items.orEmpty().take(24)
                _state.value = _state.value.copy(channels = channels)
            }
            if (XHamsterAuth.isLoggedIn) viewModelScope.launch {
                val feed = XHamsterUserRepo.newsFeed().getOrNull().orEmpty().take(12)
                _state.value = _state.value.copy(feed = feed)
                cache = _state.value
            }
        }
    }

    private companion object {
        @Volatile
        var cache: XHamsterDiscoverState? = null
    }
}

@Composable
private fun StripTitle(text: String, onSeeAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = onSeeAll) { Text(stringResource(R.string.xhamster_see_all)) }
    }
}

/** Top creators, channels and (when signed in) the subscriptions feed, for any page header. */
@Composable
fun XHamsterDiscoverStrips(
    actions: XHamsterDiscoverActions,
    showFeed: Boolean = true,
    viewModel: XHamsterDiscoverViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loader = XHamsterImageLoader.get(LocalContext.current)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.creators.isNotEmpty()) {
            StripTitle(stringResource(R.string.xhamster_top_creators), actions.onSeeCreators)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.creators, key = { it.path }) { c ->
                    Column(
                        modifier = Modifier
                            .width(64.dp)
                            .clickable { actions.onOpenCreator(c.path, c.name) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        RetryableImage(
                            model = c.avatarUrl,
                            modifier = Modifier.size(56.dp).clip(CircleShape),
                            placeholder = painterResource(R.drawable.h_chan_loading),
                            error = painterResource(R.drawable.h_chan_load_failed),
                            contentScale = ContentScale.Crop,
                            imageLoader = loader,
                        )
                        Text(
                            c.name, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        if (state.channels.isNotEmpty()) {
            StripTitle(stringResource(R.string.xhamster_channels), actions.onSeeChannels)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.channels, key = { it.path }) { ch ->
                    Column(
                        modifier = Modifier
                            .width(64.dp)
                            .clickable { actions.onOpenChannel(ch.path, ch.name) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        RetryableImage(
                            model = ch.logoUrl,
                            modifier = Modifier.size(56.dp).clip(CircleShape),
                            placeholder = painterResource(R.drawable.h_chan_loading),
                            error = painterResource(R.drawable.h_chan_load_failed),
                            contentScale = ContentScale.Crop,
                            imageLoader = loader,
                        )
                        Text(
                            ch.name, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        if (showFeed && state.feed.isNotEmpty()) {
            StripTitle(stringResource(R.string.xhamster_subscriptions_feed), actions.onSeeSubscriptions)
            LazyRow(
                contentPadding = PaddingValues(end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.feed, key = { it.video.videoCode }) { item ->
                    SiteVideoCard(
                        title = item.video.title,
                        coverUrl = item.video.coverUrl,
                        onClick = { actions.onOpenVideo(item.video.videoCode) },
                        modifier = Modifier.width(200.dp),
                        views = item.video.views,
                        duration = item.video.duration,
                        subtitle = item.authorName,
                        imageLoader = loader,
                    )
                }
            }
        }
    }
}
