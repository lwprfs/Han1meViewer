package com.yenaly.han1meviewer.ui.component

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.yenaly.han1meviewer.MissAV.cloudflare.CloudflareChallengeException
import com.yenaly.han1meviewer.MissAV.cloudflare.MissAvCloudflareSolver
import com.yenaly.han1meviewer.MissAV.data.remote.MissAvNetworkRepo
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.SupJav.common.SupJavImageLoader
import com.yenaly.han1meviewer.SupJav.data.remote.SupJavRepo
import com.yenaly.han1meviewer.logic.exception.CloudFlareBlockedException
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.ui.navigation.CrossSiteNavigator
import com.yenaly.han1meviewer.ui.navigation.PendingVideoOpen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

data class CrossSiteHit(
    val site: SiteType,
    val title: String,
    val coverUrl: String,
    val videoCode: String,
    val path: String? = null,
) {
    fun toOpenRequest() = PendingVideoOpen(site = site, videoCode = videoCode, path = path, title = title)
}

sealed interface CrossSiteState {
    data object Idle : CrossSiteState
    data object Loading : CrossSiteState
    data class Done(val hits: List<CrossSiteHit>) : CrossSiteState
    data class Error(val message: String) : CrossSiteState
}

object CrossSiteSearch {

    private val CODE_REGEX = Regex("""^\s*(\d{0,3}[A-Za-z]{2,7})[-_ ]?(\d{2,5})\s*$""")

    fun looksLikeJavCode(query: String): Boolean = CODE_REGEX.matches(query)

    fun normalize(query: String): String? {
        val m = CODE_REGEX.find(query) ?: return null
        return "${m.groupValues[1].uppercase()}-${m.groupValues[2]}"
    }

    fun otherSite(site: SiteType): SiteType? = when (site) {
        SiteType.MISSAV -> SiteType.SUPJAV
        SiteType.SUPJAV -> SiteType.MISSAV
        else -> null
    }

    private fun key(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun matches(code: String, vararg texts: String) =
        texts.any { key(it).contains(key(code)) }

    private fun isCloudflare(t: Throwable?): Boolean {
        var cur = t
        var depth = 0
        while (cur != null && depth < 6) {
            if (cur is CloudFlareBlockedException || cur is CloudflareChallengeException) return true
            if (cur.message?.contains("cloudflare", ignoreCase = true) == true) return true
            cur = cur.cause
            depth++
        }
        return false
    }

    suspend fun search(context: Context, target: SiteType, rawQuery: String): CrossSiteState {
        val code = normalize(rawQuery) ?: return CrossSiteState.Done(emptyList())
        return when (target) {
            SiteType.SUPJAV -> searchSupJav(code)
            SiteType.MISSAV -> searchMissAv(context, code)
            else -> CrossSiteState.Done(emptyList())
        }
    }

    private suspend fun searchSupJav(code: String): CrossSiteState =
        SupJavRepo.search(code, 1).fold(
            onSuccess = { page ->
                CrossSiteState.Done(
                    page.items
                        .filter { matches(code, it.title) }
                        .map {
                            CrossSiteHit(SiteType.SUPJAV, it.title, it.coverUrl, it.videoCode)
                        }
                )
            },
            onFailure = { CrossSiteState.Error(it.message ?: "SupJav is not reachable") },
        )

    private suspend fun searchMissAv(context: Context, code: String): CrossSiteState {
        suspend fun attempt(): PageLoadingState<List<HanimeInfo>> =
            MissAvNetworkRepo.searchVideos(code.lowercase(), 1)
                .first { it !is PageLoadingState.Loading }

        var result = attempt()
        if (result is PageLoadingState.Error && isCloudflare(result.throwable)) {
            if (MissAvCloudflareSolver.solve(context)) result = attempt()
        }
        return when (val r = result) {
            is PageLoadingState.Success -> CrossSiteState.Done(
                r.info.filter { matches(code, it.videoCode, it.title) }.map {
                    CrossSiteHit(SiteType.MISSAV, it.title, it.coverUrl, it.videoCode, "/en/${it.videoCode}")
                }
            )

            is PageLoadingState.Error -> CrossSiteState.Error(
                if (isCloudflare(r.throwable)) "MissAV needs Cloudflare verification"
                else r.throwable.message ?: "MissAV is not reachable"
            )

            else -> CrossSiteState.Done(emptyList())
        }
    }
}

@Composable
fun CrossSiteToggle(
    otherSite: SiteType,
    enabled: Boolean,
    state: CrossSiteState,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = CrossSiteNavigator.siteLabel(otherSite)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = enabled,
            onClick = { onToggle(!enabled) },
            label = { Text("Also search $label") },
            leadingIcon = {
                when {
                    enabled && state is CrossSiteState.Loading -> CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )

                    enabled -> Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    else -> Unit
                }
            },
        )
        Text(
            text = if (enabled) "Tap a $label result to open it there"
            else "Off. Search $label by this code too",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun imageLoaderFor(context: Context, site: SiteType): ImageLoader? =
    if (site == SiteType.SUPJAV) SupJavImageLoader.get(context) else null

private fun aspectFor(site: SiteType): Float =
    if (site == SiteType.SUPJAV) 320f / 216f else 16f / 9f

@Composable
fun CrossSiteResultsGrid(
    ownSite: SiteType,
    ownHits: List<CrossSiteHit>,
    otherSite: SiteType,
    otherState: CrossSiteState,
    isLoadingMore: Boolean,
    onReachEnd: () -> Unit,
    onOpen: (CrossSiteHit) -> Unit,
    onRetryOther: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val gridState = rememberLazyGridState()
    val nearEnd by remember(gridState) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(gridState, ownHits.size) {
        snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { onReachEnd() }
    }

    @Composable
    fun HitCard(hit: CrossSiteHit, modifier: Modifier = Modifier) {
        SiteVideoCard(
            modifier = modifier,
            title = hit.title,
            coverUrl = hit.coverUrl,
            onClick = { onOpen(hit) },
            aspectRatio = aspectFor(hit.site),
            topEndBadge = CrossSiteNavigator.siteLabel(hit.site),
            imageLoader = imageLoaderFor(context, hit.site),
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header_own", span = { GridItemSpan(maxLineSpan) }) {
            SiteHeader("${CrossSiteNavigator.siteLabel(ownSite)} (${ownHits.size})")
        }
        items(ownHits, key = { "own_${it.videoCode}" }) { HitCard(it) }
        if (isLoadingMore) {
            item(key = "own_more", span = { GridItemSpan(maxLineSpan) }) { CenteredSpinner() }
        }

        item(key = "header_other", span = { GridItemSpan(maxLineSpan) }) {
            val count = (otherState as? CrossSiteState.Done)?.hits?.size
            SiteHeader(
                CrossSiteNavigator.siteLabel(otherSite) + if (count != null) " ($count)" else ""
            )
        }
        when (otherState) {
            is CrossSiteState.Loading, CrossSiteState.Idle ->
                item(key = "other_loading", span = { GridItemSpan(maxLineSpan) }) { CenteredSpinner() }

            is CrossSiteState.Error -> item(key = "other_error", span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = otherState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetryOther) { Text("Retry") }
                }
            }

            is CrossSiteState.Done -> {
                if (otherState.hits.isEmpty()) {
                    item(key = "other_empty", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "No match on ${CrossSiteNavigator.siteLabel(otherSite)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(otherState.hits, key = { "other_${it.videoCode}" }) { HitCard(it) }
                }
            }
        }
    }
}

@Composable
private fun SiteHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun CenteredSpinner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) }
}
