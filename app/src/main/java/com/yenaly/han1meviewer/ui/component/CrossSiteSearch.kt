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
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.SupJav.common.SupJavImageLoader
import com.yenaly.han1meviewer.SupJav.data.remote.SupJavRepo
import com.yenaly.han1meviewer.logic.exception.CloudFlareBlockedException
import com.yenaly.han1meviewer.ui.navigation.CrossSiteNavigator
import com.yenaly.han1meviewer.ui.navigation.PendingVideoOpen
import com.yenaly.han1meviewer.MissAV.data.remote.MissAvNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.jsoup.Jsoup
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** One search result that remembers which site it came from. */
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

/**
 * Searching MissAV and SupJav at the same time by JAV code. The feature is only offered between
 * those two sites; callers keep the on/off state locally so it is off by default and off again
 * as soon as the search screen is left.
 */
object CrossSiteSearch {

    private val CODE_REGEX = Regex("""^\s*(\d{0,3}[A-Za-z]{2,7})[-_ ]?(\d{2,5})\s*$""")

    fun looksLikeJavCode(query: String): Boolean = CODE_REGEX.matches(query)

    /** `fsdss585` / `FSDSS 585` -> `FSDSS-585`; null when [query] is not a JAV code. */
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

    private const val SUPJAV_TIMEOUT_MS = 150_000L // includes time to solve Cloudflare by hand
    private const val MISSAV_PROBE_TIMEOUT_MS = 15_000L

    /**
     * Searches [target] for the JAV code in [rawQuery]. Never loads forever: every request has a
     * timeout and ends in [CrossSiteState.Done] or [CrossSiteState.Error] (with a retry in the UI).
     * Cloudflare is solved on the way: SupJav's client opens its verification screen by itself,
     * MissAV's is opened here and the lookup is retried once afterwards.
     */
    suspend fun search(context: Context, target: SiteType, rawQuery: String): CrossSiteState {
        val code = normalize(rawQuery) ?: return CrossSiteState.Done(emptyList())
        return try {
            when (target) {
                SiteType.SUPJAV -> withTimeoutOrNull(SUPJAV_TIMEOUT_MS) { searchSupJav(code) }
                    ?: CrossSiteState.Error("SupJav took too long to answer")

                SiteType.MISSAV -> searchMissAv(context, code)
                else -> CrossSiteState.Done(emptyList())
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            CrossSiteState.Error(e.message ?: "Search failed")
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

    // ----------------------------------------------------------------------------- MissAV

    private sealed interface Probe {
        data class Found(val hit: CrossSiteHit) : Probe
        data object NotFound : Probe
        data object Cloudflare : Probe
        data class Failed(val message: String) : Probe
    }

    /**
     * MissAV's own search goes through a slow recommendation API (30 s timeouts, 3 retries), which
     * is what made the cross search seem to load forever. A JAV code maps straight onto the video
     * page URL (`/en/fsdss-585`, plus the uncensored / subtitled variants), so those few pages are
     * requested directly, in parallel and with a short timeout.
     */
    private suspend fun searchMissAv(context: Context, code: String): CrossSiteState {
        MissAvNetwork.init(context)
        val slug = code.lowercase()
        val variants = listOf("", "-uncensored-leak", "-chinese-subtitle", "-english-subtitle")

        suspend fun probeAll(): List<Probe> = coroutineScope {
            variants.map { suffix -> async(Dispatchers.IO) { probeMissAv(code, slug + suffix) } }
                .awaitAll()
        }

        var probes = probeAll()
        if (probes.any { it is Probe.Cloudflare } && probes.none { it is Probe.Found }) {
            if (MissAvCloudflareSolver.solve(context)) probes = probeAll()
        }
        val hits = probes.filterIsInstance<Probe.Found>().map { it.hit }
        return when {
            hits.isNotEmpty() -> CrossSiteState.Done(hits)
            probes.any { it is Probe.Cloudflare } ->
                CrossSiteState.Error("MissAV needs Cloudflare verification")

            probes.all { it is Probe.Failed } ->
                CrossSiteState.Error((probes.first() as Probe.Failed).message)

            else -> CrossSiteState.Done(emptyList())
        }
    }

    private suspend fun probeMissAv(code: String, slug: String): Probe {
        return try {
            withTimeoutOrNull(MISSAV_PROBE_TIMEOUT_MS) {
                val response = MissAvNetwork.missAvService.getVideoDetail("/en/$slug")
                when {
                    response.isSuccessful -> {
                        val doc = Jsoup.parse(response.body()?.string().orEmpty())
                        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
                            ?.takeIf { it.isNotBlank() }
                            ?: doc.title()
                        if (!matches(code, title)) return@withTimeoutOrNull Probe.NotFound
                        val cover = doc.selectFirst("meta[property=og:image]")?.attr("content")
                            ?.takeIf { it.isNotBlank() }
                            ?: "https://fourhoi.com/$slug/cover-t.jpg"
                        Probe.Found(
                            CrossSiteHit(
                                SiteType.MISSAV,
                                title.substringBefore(" - ").ifBlank { code },
                                cover,
                                slug,
                                "/en/$slug",
                            )
                        )
                    }

                    response.code() == 404 -> Probe.NotFound
                    response.code() == 403 || response.code() == 503 -> {
                        val body = response.errorBody()?.string().orEmpty()
                        if (body.contains("Just a moment") || body.contains("Cloudflare") ||
                            body.contains("cf_")
                        ) Probe.Cloudflare else Probe.Failed("HTTP ${response.code()}")
                    }

                    else -> Probe.Failed("HTTP ${response.code()}")
                }
            } ?: Probe.Failed("MissAV took too long to answer")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isCloudflare(e)) Probe.Cloudflare else Probe.Failed(e.message ?: "MissAV is not reachable")
        }
    }
}

/** The on/off trigger shown in the search screens when the query looks like a JAV code. */
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

/**
 * Results of both sites in one grid: a header per site and the site name on top of every
 * thumbnail. Tapping a card of the other site opens that site's video page directly.
 */
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
                item(key = "other_loading", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Searching ${CrossSiteNavigator.siteLabel(otherSite)}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

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
