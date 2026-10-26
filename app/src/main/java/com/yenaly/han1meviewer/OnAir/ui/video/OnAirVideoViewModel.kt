package com.yenaly.han1meviewer.OnAir.ui.video

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.OnAir.data.local.OnAirHistoryRepo
import com.yenaly.han1meviewer.OnAir.data.model.OnAirVideoDetail
import com.yenaly.han1meviewer.OnAir.data.model.StreamResult
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetworkRepo
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirStreamFetcher
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class PlaybackIntent { NotStarted, Playing, Paused }

enum class StreamState { Loading, Ready, Error }

data class QualityStream(
    val quality: String,
    val url: String = "",
    val mediaType: String? = null,
    val isHls: Boolean = false,
    val state: StreamState = StreamState.Loading,
    val errorMessage: String? = null,
)

data class OnAirPlayerState(
    val currentQuality: String = "",
    val pendingQuality: String? = null,
    val streamsByQuality: Map<String, QualityStream> = emptyMap(),
    val availableSpeeds: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f),
    val speed: Float = 1.0f,
    val isPlaying: Boolean = false,
    val isReady: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val isFullscreen: Boolean = false,
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val errorMessage: String? = null,
    val playbackIntent: PlaybackIntent = PlaybackIntent.NotStarted,
) {
    val activeStream: QualityStream? get() = streamsByQuality[currentQuality]
    val activeUrl: String? get() = activeStream?.url?.takeIf { it.isNotBlank() }
    val activeMediaType: String? get() = activeStream?.mediaType
    val isSwitchLoading: Boolean get() = pendingQuality != null
}

class OnAirVideoViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "OnAirVideoViewModel"
        private const val RESUME_THRESHOLD_MS = 5_000L
        private const val COMPLETE_GRACE_MS = 3_000L
        private const val QUALITY_FETCH_TIMEOUT_MS = 45_000L
        private val QUALITY_PRIORITY = listOf("original", "480", "360", "240", "144")
    }

    private val _pageState =
        MutableStateFlow<VideoLoadingState<OnAirVideoDetail>>(VideoLoadingState.Loading)
    val pageState = _pageState.asStateFlow()

    private val _playerState = MutableStateFlow(OnAirPlayerState())
    val playerState = _playerState.asStateFlow()

    private val _resumePosition = MutableStateFlow(0L)
    val resumePosition = _resumePosition.asStateFlow()

    private val _showResumeBanner = MutableStateFlow(false)
    val showResumeBanner = _showResumeBanner.asStateFlow()

    private var loadJob: Job? = null
    private var extractJob: Job? = null
    private var recoverJob: Job? = null
    private var fallbackJob: Job? = null
    private var currentVideoId: String = ""
    private var currentDetail: OnAirVideoDetail? = null
    private val qualityCache = mutableMapOf<String, QualityStream>()
    private val recoverAttempts = mutableMapOf<String, Int>()

    init {
        OnAirHistoryRepo.init(application)
    }

    fun loadVideo(videoId: String, preferredQuality: String? = null) {
        if (videoId.isBlank()) return
        if (currentVideoId == videoId && _pageState.value is VideoLoadingState.Success) return

        extractJob?.cancel(); extractJob = null
        recoverJob?.cancel(); recoverJob = null
        fallbackJob?.cancel(); fallbackJob = null
        qualityCache.clear()
        recoverAttempts.clear()

        currentVideoId = videoId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _pageState.value = VideoLoadingState.Loading
            _playerState.value = OnAirPlayerState(isLoading = true)
            _showResumeBanner.value = false
            _resumePosition.value = 0L

            OnAirNetworkRepo.getVideoDetail(videoId, quality = "original").collect { state ->
                _pageState.value = state
                when (state) {
                    is VideoLoadingState.Success -> {
                        var detail = state.info
                        if (detail.videoId.isBlank()) {
                            detail = detail.copy(videoId = videoId)
                            _pageState.value = VideoLoadingState.Success(detail)
                        }
                        currentDetail = detail
                        val history = OnAirHistoryRepo.getByVideoId(videoId)
                        val preferred = preferredQuality ?: history?.lastQuality
                        applyInitialDetail(detail, preferred)
                        loadResumePosition(videoId)
                        if (_playerState.value.activeStream?.state != StreamState.Ready) {
                            scheduleFallback()
                        }
                    }
                    is VideoLoadingState.Error -> {
                        _playerState.update {
                            it.copy(
                                isLoading = false,
                                hasError = true,
                                errorMessage = state.throwable.message,
                            )
                        }
                    }
                    is VideoLoadingState.NoContent -> {
                        _playerState.update {
                            it.copy(
                                isLoading = false,
                                hasError = true,
                                errorMessage = "Video not found",
                            )
                        }
                    }
                    is VideoLoadingState.Loading -> Unit
                }
            }
        }
    }

    private fun applyInitialDetail(detail: OnAirVideoDetail, preferred: String?) {
        val streamMap = detail.streamUrlByQuality
        val initialMap = linkedMapOf<String, QualityStream>()

        streamMap.forEach { (quality, url) ->
            if (url.isNotBlank()) {
                initialMap[quality] = QualityStream(
                    quality = quality,
                    url = url,
                    mediaType = detail.currentMediaType,
                    isHls = url.contains(".m3u8", ignoreCase = true),
                    state = StreamState.Ready,
                )
            }
        }

        val advertised = LinkedHashSet<String>()
        advertised.add("original")
        QUALITY_PRIORITY.forEach { advertised.add(it) }
        detail.qualityLinksByQuality.keys.forEach { advertised.add(it) }

        advertised.forEach { q ->
            if (q !in initialMap) {
                initialMap[q] = QualityStream(quality = q, state = StreamState.Loading)
            }
        }

        val chosen = when {
            initialMap["original"]?.state == StreamState.Ready -> "original"
            preferred != null && initialMap[preferred]?.state == StreamState.Ready -> preferred
            initialMap[detail.currentQuality]?.state == StreamState.Ready -> detail.currentQuality
            else -> initialMap.entries.firstOrNull { it.value.state == StreamState.Ready }?.key
                ?: QUALITY_PRIORITY.firstOrNull { initialMap[it]?.state == StreamState.Ready }
                ?: initialMap.keys.first()
        }

        initialMap[chosen]?.takeIf { it.state == StreamState.Ready }?.let {
            qualityCache[chosen] = it
        }

        val chosenReady = initialMap[chosen]?.state == StreamState.Ready

        _playerState.update {
            it.copy(
                currentQuality = chosen,
                streamsByQuality = initialMap,
                isLoading = false,
                hasError = false,
                errorMessage = null,
                isReady = chosenReady,
                playbackIntent = PlaybackIntent.NotStarted,
                pendingQuality = null,
            )
        }
    }

    private fun scheduleFallback() {
        fallbackJob?.cancel()
        fallbackJob = viewModelScope.launch { advanceFallback() }
    }

    private suspend fun advanceFallback() {
        val advertised = _playerState.value.streamsByQuality.keys
        if (advertised.isEmpty()) return
        val candidates = QUALITY_PRIORITY.filter { it in advertised }
        if (candidates.isEmpty()) return

        for (q in candidates) {
            val stream = _playerState.value.streamsByQuality[q]
            if (stream?.state == StreamState.Ready && !stream.url.isNullOrBlank()) {
                if (_playerState.value.currentQuality != q) {
                    _playerState.update {
                        it.copy(
                            currentQuality = q,
                            isReady = true,
                            hasError = false,
                            errorMessage = null,
                        )
                    }
                }
                return
            }
        }

        for (q in candidates) {
            val result = fetchQualityStreamOrNull(q) ?: continue
            val ready = QualityStream(
                quality = q,
                url = result.url,
                mediaType = result.mediaType,
                isHls = result.isHls,
                state = StreamState.Ready,
            )
            qualityCache[q] = ready
            _playerState.update {
                it.copy(
                    currentQuality = q,
                    isReady = true,
                    hasError = false,
                    errorMessage = null,
                    streamsByQuality = it.streamsByQuality + (q to ready),
                )
            }
            return
        }

        _playerState.update {
            it.copy(
                hasError = true,
                errorMessage = "No playable quality available for this video",
                isLoading = false,
            )
        }
    }

    private suspend fun fetchQualityStreamOrNull(quality: String): StreamResult? {
        val detail = currentDetail ?: return null
        _playerState.update {
            it.copy(
                pendingQuality = quality,
                streamsByQuality = it.streamsByQuality + (
                        quality to QualityStream(quality = quality, state = StreamState.Loading)
                        ),
            )
        }
        val result: StreamResult? = try {
            withTimeoutOrNull(QUALITY_FETCH_TIMEOUT_MS) {
                OnAirStreamFetcher.fetch(detail.videoId, quality)
            }
        } catch (e: CancellationException) {
            _playerState.update { it.copy(pendingQuality = null) }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "fetchQualityStreamOrNull failed for $quality", e)
            null
        }

        if (result == null) {
            _playerState.update {
                it.copy(
                    pendingQuality = null,
                    streamsByQuality = it.streamsByQuality + (
                            quality to QualityStream(
                                quality = quality,
                                state = StreamState.Error,
                                errorMessage = "Quality $quality unavailable",
                            )
                            ),
                )
            }
        } else {
            _playerState.update { it.copy(pendingQuality = null) }
        }
        return result
    }

    private suspend fun loadResumePosition(videoId: String) {
        val existing = OnAirHistoryRepo.getByVideoId(videoId) ?: return
        if (!existing.completed && existing.lastPosition > RESUME_THRESHOLD_MS) {
            _resumePosition.value = existing.lastPosition
            _showResumeBanner.value = true
        }
    }

    fun dismissResumeBanner() { _showResumeBanner.value = false }

    fun applyResume() {
        val pos = _resumePosition.value
        if (pos > 0) _playerState.update { it.copy(position = pos) }
        _showResumeBanner.value = false
    }

    fun startFromBeginning() {
        _resumePosition.value = 0L
        _playerState.update { it.copy(position = 0L) }
        _showResumeBanner.value = false
    }

    fun startPlayback() {
        val s = _playerState.value
        if (s.isSwitchLoading) return
        if (s.activeUrl.isNullOrBlank()) {
            val current = s.streamsByQuality[s.currentQuality]
            if (current == null || current.state == StreamState.Error) {
                selectQuality(s.currentQuality)
            }
            return
        }
        _playerState.update {
            it.copy(playbackIntent = PlaybackIntent.Playing, isPlaying = true)
        }
    }

    fun pausePlayback() {
        _playerState.update {
            it.copy(playbackIntent = PlaybackIntent.Paused, isPlaying = false)
        }
    }

    fun resumePlayback() {
        if (_playerState.value.playbackIntent == PlaybackIntent.NotStarted) {
            startPlayback()
            return
        }
        _playerState.update {
            it.copy(playbackIntent = PlaybackIntent.Playing, isPlaying = true)
        }
    }

    fun selectQuality(quality: String) {
        val s = _playerState.value
        if (quality.isBlank()) return
        if (quality == s.currentQuality && s.activeStream?.state == StreamState.Ready) return
        if (s.pendingQuality == quality) return

        recoverAttempts.remove(quality)

        val cached = qualityCache[quality]
        if (cached != null && cached.state == StreamState.Ready && cached.url.isNotBlank()) {
            val wasPlaying = s.playbackIntent == PlaybackIntent.Playing
            _playerState.update {
                it.copy(
                    currentQuality = quality,
                    isReady = true,
                    hasError = false,
                    errorMessage = null,
                    isPlaying = wasPlaying,
                    streamsByQuality = it.streamsByQuality + (quality to cached),
                )
            }
            return
        }

        if (currentDetail == null) return
        extractJob?.cancel()
        extractJob = viewModelScope.launch {
            val result = fetchQualityStreamOrNull(quality) ?: return@launch
            val ready = QualityStream(
                quality = quality,
                url = result.url,
                mediaType = result.mediaType,
                isHls = result.isHls,
                state = StreamState.Ready,
            )
            qualityCache[quality] = ready
            val wasPlaying = _playerState.value.playbackIntent == PlaybackIntent.Playing
            _playerState.update {
                it.copy(
                    pendingQuality = null,
                    currentQuality = quality,
                    isReady = true,
                    isPlaying = wasPlaying,
                    hasError = false,
                    errorMessage = null,
                    streamsByQuality = it.streamsByQuality + (quality to ready),
                )
            }
        }
    }

    fun markStreamUnavailable(quality: String, reason: String? = null) {
        if (quality.isBlank()) return
        val isOriginal = quality == "original"
        val maxAttempts = if (isOriginal) 2 else 1
        val attempts = (recoverAttempts[quality] ?: 0) + 1
        recoverAttempts[quality] = attempts
        qualityCache.remove(quality)

        if (attempts <= maxAttempts) {
            Log.w(TAG, "markStreamUnavailable($quality) attempt $attempts/$maxAttempts — re-fetching")
            recoverJob?.cancel()
            recoverJob = viewModelScope.launch {
                _playerState.update { s ->
                    s.copy(
                        pendingQuality = quality,
                        streamsByQuality = s.streamsByQuality + (
                                quality to QualityStream(quality = quality, state = StreamState.Loading)
                                ),
                    )
                }
                val detail = currentDetail
                if (detail == null) {
                    _finalizeUnavailable(quality, reason)
                    return@launch
                }
                val result: StreamResult? = try {
                    withTimeoutOrNull(QUALITY_FETCH_TIMEOUT_MS) {
                        OnAirStreamFetcher.fetch(detail.videoId, quality)
                    }
                } catch (e: CancellationException) {
                    _playerState.update { it.copy(pendingQuality = null) }
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "recover fetch failed for $quality", e)
                    null
                }
                if (result == null) {
                    _finalizeUnavailable(quality, reason)
                } else {
                    recoverAttempts.remove(quality)
                    val ready = QualityStream(
                        quality = quality,
                        url = result.url,
                        mediaType = result.mediaType,
                        isHls = result.isHls,
                        state = StreamState.Ready,
                    )
                    qualityCache[quality] = ready
                    _playerState.update { s ->
                        s.copy(
                            pendingQuality = null,
                            currentQuality = quality,
                            isReady = true,
                            hasError = false,
                            errorMessage = null,
                            streamsByQuality = s.streamsByQuality + (quality to ready),
                        )
                    }
                }
            }
            return
        }

        _finalizeUnavailable(quality, reason)
    }

    private fun _finalizeUnavailable(quality: String, reason: String?) {
        _playerState.update { s ->
            val updated = s.streamsByQuality.toMutableMap()
            updated[quality] = QualityStream(
                quality = quality,
                state = StreamState.Error,
                errorMessage = reason ?: "Quality $quality unavailable",
            )
            qualityCache.remove(quality)

            val isCurrent = s.currentQuality == quality
            s.copy(
                hasError = false,
                errorMessage = null,
                streamsByQuality = updated,
                pendingQuality = null,
                isPlaying = if (isCurrent) false else s.isPlaying,
                playbackIntent = if (isCurrent) PlaybackIntent.NotStarted else s.playbackIntent,
            )
        }

        val advertised = _playerState.value.streamsByQuality.keys
        val candidates = QUALITY_PRIORITY.filter { it in advertised && it != quality }
        val next = candidates.firstOrNull { q ->
            _playerState.value.streamsByQuality[q]?.state == StreamState.Error
        } ?: _playerState.value.streamsByQuality
            .filterValues { it.state == StreamState.Ready && it.url.isNotBlank() }
            .keys
            .firstOrNull { it != quality }

        if (next != null) {
            Log.d(TAG, "cascading $quality -> $next")
            selectQuality(next)
        } else {
            Log.d(TAG, "no more qualities after $quality")
            scheduleFallback()
        }
    }

    fun cancelPendingExtraction() {
        extractJob?.cancel(); extractJob = null
        recoverJob?.cancel(); recoverJob = null
        fallbackJob?.cancel(); fallbackJob = null
        _playerState.update { it.copy(pendingQuality = null) }
    }

    fun copyStreamUrl(): String? = _playerState.value.activeUrl
    fun currentQualityLabel(): String = _playerState.value.currentQuality

    fun setSpeed(speed: Float) { _playerState.update { it.copy(speed = speed) } }
    fun setFullscreen(fullscreen: Boolean) { _playerState.update { it.copy(isFullscreen = fullscreen) } }

    fun onPositionUpdate(position: Long, duration: Long) {
        _playerState.update {
            it.copy(
                position = position,
                duration = if (duration > 0) duration else it.duration,
            )
        }
    }

    fun persistProgress() {
        val detail = currentDetail ?: return
        val state = _playerState.value
        if (detail.videoId.isBlank()) return
        if (state.duration <= 0) return
        viewModelScope.launch {
            runCatching {
                OnAirHistoryRepo.saveProgress(
                    videoId = detail.videoId,
                    title = detail.title,
                    coverUrl = detail.recommendedVideos.firstOrNull()?.thumbUrl.orEmpty(),
                    uploaderName = detail.uploaderName,
                    position = state.position,
                    duration = state.duration,
                    isPlaying = state.isPlaying,
                    quality = state.currentQuality,
                )
            }.onFailure { Log.w(TAG, "persistProgress failed: ${it.message}") }
        }
    }

    fun markCompletedIfNeeded() {
        val detail = currentDetail ?: return
        val state = _playerState.value
        if (state.duration <= 0 || state.position < state.duration - COMPLETE_GRACE_MS) return
        viewModelScope.launch {
            runCatching {
                OnAirHistoryRepo.saveProgress(
                    videoId = detail.videoId,
                    title = detail.title,
                    coverUrl = "",
                    uploaderName = detail.uploaderName,
                    position = state.duration,
                    duration = state.duration,
                    isPlaying = false,
                    quality = state.currentQuality,
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
        extractJob?.cancel()
        recoverJob?.cancel()
        fallbackJob?.cancel()
        persistProgress()
    }
}
