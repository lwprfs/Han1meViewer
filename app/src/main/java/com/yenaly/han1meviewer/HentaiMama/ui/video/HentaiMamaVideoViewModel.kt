package com.yenaly.han1meviewer.HentaiMama.ui.video

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.HentaiMama.data.model.EpisodeDetailPage
import com.yenaly.han1meviewer.HentaiMama.data.model.HentaiMamaEpisode
import com.yenaly.han1meviewer.HentaiMama.data.model.HentaiMamaVideoInfo
import com.yenaly.han1meviewer.HentaiMama.data.model.HentaiMamaVideoLink
import com.yenaly.han1meviewer.HentaiMama.data.model.Mirror
import com.yenaly.han1meviewer.HentaiMama.data.model.SeriesDetailPage
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetwork
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetworkRepo
import com.yenaly.han1meviewer.HentaiMama.settings.HentaiMamaVideoSettings
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HentaiMamaPlayerState(
    val url: String = "",
    val quality: String = "",
    val qualityOptions: Map<String, String> = emptyMap(),
    val availableSpeeds: List<Float> =
        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f),
    val speed: Float = 1.0f,
    val isPlaying: Boolean = false,
    val isReady: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val hasSubtitle: Boolean = false,
    val isFullscreen: Boolean = false,
)

sealed interface HentaiMamaExtractionState {
    data object Idle : HentaiMamaExtractionState
    data object Loading : HentaiMamaExtractionState
    data class Failed(val reason: String) : HentaiMamaExtractionState
    data object Success : HentaiMamaExtractionState
}

class HentaiMamaVideoViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HentaiMamaVideoVM"
        private const val MAX_PAGE_LOAD_ATTEMPTS = 3
    }

    private val _pageState =
        MutableStateFlow<VideoLoadingState<HentaiMamaVideoInfo>>(VideoLoadingState.Loading)
    val pageState = _pageState.asStateFlow()

    private val _seriesState =
        MutableStateFlow<VideoLoadingState<SeriesDetailPage>>(VideoLoadingState.Loading)
    val seriesState = _seriesState.asStateFlow()

    private val _playerState = MutableStateFlow(HentaiMamaPlayerState())
    val playerState = _playerState.asStateFlow()

    private val _extractionState =
        MutableStateFlow<HentaiMamaExtractionState>(HentaiMamaExtractionState.Idle)
    val extractionState = _extractionState.asStateFlow()

    private val _selectedMirrorIndex = MutableStateFlow(0)
    val selectedMirrorIndex = _selectedMirrorIndex.asStateFlow()

    private val _selectedMirrorLabel = MutableStateFlow<String?>(null)
    val selectedMirrorLabel = _selectedMirrorLabel.asStateFlow()

    private val _mirrorLinks = MutableStateFlow<List<HentaiMamaVideoLink>>(emptyList())
    val mirrorLinks = _mirrorLinks.asStateFlow()

    private val _currentPageUrl = MutableStateFlow("")
    val currentPageUrl = _currentPageUrl.asStateFlow()

    private var loadJob: Job? = null
    private var extractionJob: Job? = null

    fun loadEpisodePage(url: String, force: Boolean = false) {
        if (!force && _currentPageUrl.value == url &&
            _pageState.value is VideoLoadingState.Success
        ) {
            Log.d(TAG, "loadEpisodePage skipped, already loaded: $url")
            return
        }

        Log.d(TAG, "loadEpisodePage start: $url (force=$force)")
        _currentPageUrl.value = url
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _pageState.value = VideoLoadingState.Loading
            _seriesState.value = VideoLoadingState.Loading
            _extractionState.value = HentaiMamaExtractionState.Idle
            _mirrorLinks.value = emptyList()
            _selectedMirrorIndex.value = 0
            _selectedMirrorLabel.value = null
            _playerState.update { state: HentaiMamaPlayerState ->
                state.copy(
                    isReady = false,
                    url = "",
                    quality = "",
                    qualityOptions = emptyMap(),
                )
            }

            val isSeriesUrl: Boolean =
                !url.contains("/episodes/", ignoreCase = true) &&
                        !url.contains("-episode-", ignoreCase = true)

            if (isSeriesUrl) {
                Log.d(TAG, "loadEpisodePage: detected series URL, resolving first episode")
                val resolved: Pair<String, String>? = resolveSeriesFirstEpisode(url)
                if (resolved != null) {
                    Log.d(
                        TAG,
                        "loadEpisodePage: resolved series to episode slug=${resolved.first}, url=${resolved.second}"
                    )
                    _currentPageUrl.value = resolved.second
                    loadEpisodeDetail(resolved.second)
                    return@launch
                }
                Log.w(TAG, "loadEpisodePage: could not resolve series to episode, showing series page")
                HentaiMamaNetworkRepo.getSeriesDetail(url).collect { s ->
                    _seriesState.value = s
                }
                return@launch
            }

            loadEpisodeDetail(url)
        }
    }

    private suspend fun loadEpisodeDetail(url: String) {
        var attempt = 0
        while (attempt < MAX_PAGE_LOAD_ATTEMPTS) {
            attempt++
            var terminal = false
            HentaiMamaNetworkRepo.getVideoDetail(url).collect { state ->
                _pageState.value = state
                when (state) {
                    is VideoLoadingState.Success -> {
                        terminal = true
                        val page: EpisodeDetailPage? = state.info.page
                        Log.d(
                            TAG,
                            "detail loaded: page=${page != null}, " +
                                    "infoTitle=${state.info.title.take(40)}, " +
                                    "videoUrls=${state.info.videoUrls.size}"
                        )
                        if (page != null) {
                            val htmlApplied: Boolean = applyHtmlLinks(page)
                            applyInitialMirror(page)

                            if (!htmlApplied) {
                                Log.d(
                                    TAG,
                                    "loadEpisodeDetail: no HTML URL, falling back to AJAX extraction"
                                )
                                fetchMirrorLinks(_selectedMirrorIndex.value, page)
                            }
                        }
                    }

                    is VideoLoadingState.Error -> {
                        terminal = true
                    }

                    is VideoLoadingState.NoContent -> {
                        terminal = true
                    }

                    is VideoLoadingState.Loading -> Unit
                }
            }

            if (terminal) return

            Log.w(TAG, "loadEpisodeDetail: attempt $attempt produced no terminal state, retrying")
            kotlinx.coroutines.delay(750L)
        }
        Log.e(TAG, "loadEpisodeDetail: exhausted $MAX_PAGE_LOAD_ATTEMPTS attempts for $url")
        _pageState.value = VideoLoadingState.Error(
            IllegalStateException("Could not load episode page after $MAX_PAGE_LOAD_ATTEMPTS attempts")
        )
    }

    private suspend fun resolveSeriesFirstEpisode(url: String): Pair<String, String>? {
        return try {
            val seriesUrl: String = when {
                url.startsWith("http") -> url
                url.contains("/tvshows/") -> HentaiMamaNetwork.normalizeUrl(url)
                else -> "${HentaiMamaNetwork.baseUrl.trimEnd('/')}/tvshows/${url.trim('/')}/"
            }
            Log.d(TAG, "resolveSeriesFirstEpisode: fetching $seriesUrl")

            val body: String = withContext(Dispatchers.IO) {
                val resp = HentaiMamaNetwork.service.getVideoDetail(seriesUrl)
                if (resp.isSuccessful) resp.body()?.string().orEmpty() else ""
            }
            if (body.isBlank()) {
                Log.w(TAG, "resolveSeriesFirstEpisode: empty response")
                return null
            }

            val state: VideoLoadingState<SeriesDetailPage> = withContext(Dispatchers.IO) {
                HentaiMamaNetworkRepo.parseFullSeriesPage(body, seriesUrl)
            }
            if (state !is VideoLoadingState.Success) {
                Log.w(TAG, "resolveSeriesFirstEpisode: parse failed")
                return null
            }
            val episodes: List<HentaiMamaEpisode> = state.info.episodes
            if (episodes.isEmpty()) {
                Log.w(TAG, "resolveSeriesFirstEpisode: no episodes in series")
                return null
            }
            val first: HentaiMamaEpisode = episodes.minByOrNull {
                it.episodeNumber ?: Float.MAX_VALUE
            } ?: episodes.first()
            val episodeUrl: String = if (first.url.startsWith("http")) first.url
            else HentaiMamaNetwork.normalizeUrl(first.url)
            first.slug to episodeUrl
        } catch (e: Exception) {
            Log.e(TAG, "resolveSeriesFirstEpisode failed", e)
            null
        }
    }

    private fun applyHtmlLinks(page: EpisodeDetailPage): Boolean {
        val qualityMap: Map<String, String> = page.player.mp4UrlByQuality
        val single: String? = page.player.mp4Url
        Log.d(
            TAG,
            "applyHtmlLinks: qualityMapKeys=${qualityMap.keys}, " +
                    "mp4Url=${single?.take(80)}, " +
                    "mirrors=${page.player.mirrors.size}"
        )

        if (qualityMap.isEmpty()) {
            if (single.isNullOrBlank()) {
                Log.w(TAG, "applyHtmlLinks: no video URL and no quality map")
                return false
            }
            _playerState.update { state: HentaiMamaPlayerState ->
                state.copy(
                    qualityOptions = mapOf("Default" to single),
                    quality = "Default",
                    url = single,
                    isReady = true,
                )
            }
            _extractionState.value = HentaiMamaExtractionState.Success
            Log.d(TAG, "applyHtmlLinks: used single mp4Url")
            return true
        }

        val preferred: String = HentaiMamaVideoSettings.preferredQuality
        val chosen: String = when {
            preferred == HentaiMamaVideoSettings.QUALITY_AUTO ->
                qualityMap.keys.first()
            qualityMap.keys.any { it.equals(preferred, ignoreCase = true) } ->
                qualityMap.keys.first { it.equals(preferred, ignoreCase = true) }
            else -> qualityMap.keys.first()
        }
        _playerState.update { state: HentaiMamaPlayerState ->
            state.copy(
                qualityOptions = qualityMap,
                quality = chosen,
                url = qualityMap[chosen].orEmpty(),
                isReady = true,
            )
        }
        _extractionState.value = HentaiMamaExtractionState.Success
        Log.d(TAG, "applyHtmlLinks: chosen=$chosen, url=${qualityMap[chosen]?.take(80)}")
        return true
    }

    private fun applyInitialMirror(page: EpisodeDetailPage) {
        val mirrors: List<Mirror> = page.player.mirrors
        if (mirrors.isEmpty()) {
            Log.d(TAG, "applyInitialMirror: no mirrors")
            return
        }
        val preferred: String = HentaiMamaVideoSettings.preferredServer
        val index: Int = if (preferred == HentaiMamaVideoSettings.SERVER_AUTO) {
            mirrors.indexOfFirst { it.isActive }.coerceAtLeast(0)
        } else {
            mirrors.indexOfFirst { it.label.contains(preferred, ignoreCase = true) }
                .takeIf { it >= 0 } ?: 0
        }
        _selectedMirrorIndex.value = index
        _selectedMirrorLabel.value = mirrors[index].label
        Log.d(TAG, "applyInitialMirror: index=$index, label=${mirrors[index].label}")
    }

    fun selectMirror(index: Int) {
        val videoInfo: HentaiMamaVideoInfo =
            (_pageState.value as? VideoLoadingState.Success)?.info ?: return
        val page: EpisodeDetailPage = videoInfo.page ?: return
        val mirrors: List<Mirror> = page.player.mirrors
        if (index !in mirrors.indices) return

        _selectedMirrorIndex.value = index
        _selectedMirrorLabel.value = mirrors[index].label
        _mirrorLinks.value = emptyList()
        fetchMirrorLinks(index, page)
    }

    fun retryExtraction() {
        val videoInfo: HentaiMamaVideoInfo =
            (_pageState.value as? VideoLoadingState.Success)?.info ?: return
        val page: EpisodeDetailPage = videoInfo.page ?: return
        fetchMirrorLinks(_selectedMirrorIndex.value, page)
    }

    private fun fetchMirrorLinks(index: Int, pageOverride: EpisodeDetailPage? = null) {
        val resolvedPage: EpisodeDetailPage = pageOverride
            ?: run {
                val videoInfo: HentaiMamaVideoInfo? =
                    (_pageState.value as? VideoLoadingState.Success)?.info
                videoInfo?.page
            }
            ?: return

        val mirrors: List<Mirror> = resolvedPage.player.mirrors
        val fallbackOptionId: String = "option-${index + 1}"
        val mirror: Mirror = mirrors.getOrNull(index)
            ?: Mirror(label = "mi-${index + 1}", optionId = fallbackOptionId, isActive = true)

        extractionJob?.cancel()
        extractionJob = viewModelScope.launch {
            _extractionState.value = HentaiMamaExtractionState.Loading
            try {
                val pageUrl: String = _currentPageUrl.value
                val fullUrl: String = if (pageUrl.isNotBlank()) {
                    pageUrl
                } else {
                    "${HentaiMamaNetwork.baseUrl.trimEnd('/')}/episodes/${resolvedPage.info.slug}/"
                }
                Log.d(TAG, "fetchMirrorLinks: fetching detail from $fullUrl")

                val body: String = withContext(Dispatchers.IO) {
                    val resp = HentaiMamaNetwork.service.getVideoDetail(fullUrl)
                    if (resp.isSuccessful) resp.body()?.string().orEmpty() else ""
                }
                val optionNumber: Int =
                    mirror.optionId.removePrefix("option-").toIntOrNull() ?: (index + 1)
                Log.d(
                    TAG,
                    "fetchMirrorLinks: index=$index, mirror=${mirror.label}, " +
                            "optionNumber=$optionNumber, bodyLen=${body.length}"
                )
                val links: List<HentaiMamaVideoLink> = withContext(Dispatchers.IO) {
                    HentaiMamaNetworkRepo.extractVideoLinks(body, optionNumber)
                }
                Log.d(TAG, "fetchMirrorLinks: links=${links.size}")
                if (links.isEmpty()) {
                    _extractionState.value = HentaiMamaExtractionState.Failed(
                        "No links from mirror ${mirror.label}"
                    )
                    return@launch
                }
                _mirrorLinks.value = links
                val pref: String = HentaiMamaVideoSettings.preferredQuality
                val chosen: HentaiMamaVideoLink = when {
                    pref == HentaiMamaVideoSettings.QUALITY_AUTO -> links.first()
                    else -> links.firstOrNull { link: HentaiMamaVideoLink ->
                        link.quality.equals(pref, ignoreCase = true) ||
                                link.label.equals(pref, ignoreCase = true)
                    } ?: links.first()
                }
                _playerState.update { state: HentaiMamaPlayerState ->
                    state.copy(
                        url = chosen.url,
                        quality = chosen.quality,
                        qualityOptions = links.associate { link: HentaiMamaVideoLink ->
                            link.quality to link.url
                        },
                        isReady = true,
                    )
                }
                _extractionState.value = HentaiMamaExtractionState.Success
            } catch (e: Exception) {
                Log.e(TAG, "fetchMirrorLinks failed", e)
                _extractionState.value = HentaiMamaExtractionState.Failed(
                    e.message ?: "Extraction failed"
                )
            }
        }
    }

    fun setQuality(quality: String) {
        val options: Map<String, String> = _playerState.value.qualityOptions
        val url: String = options[quality] ?: return
        _playerState.update { state: HentaiMamaPlayerState ->
            state.copy(quality = quality, url = url)
        }
    }

    fun setSpeed(speed: Float) {
        _playerState.update { state: HentaiMamaPlayerState ->
            state.copy(speed = speed)
        }
    }

    fun setPlaying(playing: Boolean) {
        _playerState.update { state: HentaiMamaPlayerState ->
            state.copy(isPlaying = playing)
        }
    }

    fun setPosition(position: Long, duration: Long) {
        _playerState.update { state: HentaiMamaPlayerState ->
            state.copy(position = position, duration = duration)
        }
    }

    fun setFullscreen(fullscreen: Boolean) {
        _playerState.update { state: HentaiMamaPlayerState ->
            state.copy(isFullscreen = fullscreen)
        }
    }

    fun setHasSubtitle(has: Boolean) {
        _playerState.update { state: HentaiMamaPlayerState ->
            state.copy(hasSubtitle = has)
        }
    }

    fun clear() {
        loadJob?.cancel()
        extractionJob?.cancel()
        _pageState.value = VideoLoadingState.Loading
        _seriesState.value = VideoLoadingState.Loading
        _playerState.value = HentaiMamaPlayerState()
        _extractionState.value = HentaiMamaExtractionState.Idle
        _mirrorLinks.value = emptyList()
        _selectedMirrorIndex.value = 0
        _selectedMirrorLabel.value = null
        _currentPageUrl.value = ""
    }

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
        extractionJob?.cancel()
    }
}
