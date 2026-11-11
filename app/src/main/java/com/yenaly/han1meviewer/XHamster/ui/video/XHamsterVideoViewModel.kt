package com.yenaly.han1meviewer.XHamster.ui.video

import com.yenaly.han1meviewer.ui.component.player.SitePlayerState
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.XHamster.common.XHamsterSettings
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterComment
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterDetail
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterStream
import com.yenaly.han1meviewer.XHamster.data.remote.XHamsterRepo
import com.yenaly.yenaly_libs.utils.applicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

typealias XHamsterPlayerState = SitePlayerState

sealed interface XHamsterDetailState {
    data object Loading : XHamsterDetailState
    data class Success(val detail: XHamsterDetail) : XHamsterDetailState
    data class Error(val message: String) : XHamsterDetailState
}

data class XHamsterCommentsState(
    val items: List<XHamsterComment> = emptyList(),
    val total: Long? = null,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val writeDenied: Boolean = false,
)

data class XHamsterSubscribeState(
    val subscribed: Boolean = false,
    val subscribers: Long? = null,
    val busy: Boolean = false,

    val message: SubscribeMessage? = null,
)

enum class SubscribeMessage { LoginRequired, Failed }

class XHamsterVideoViewModel : ViewModel() {

    private companion object {
        const val TAG = "XHamsterVideoVM"
    }

    private val _detailState = MutableStateFlow<XHamsterDetailState>(XHamsterDetailState.Loading)
    val detailState: StateFlow<XHamsterDetailState> = _detailState.asStateFlow()

    private val _playerState = MutableStateFlow(XHamsterPlayerState())
    val playerState: StateFlow<XHamsterPlayerState> = _playerState.asStateFlow()

    private val _streamError = MutableStateFlow<String?>(null)
    val streamError: StateFlow<String?> = _streamError.asStateFlow()

    private val _comments = MutableStateFlow(XHamsterCommentsState())
    val comments: StateFlow<XHamsterCommentsState> = _comments.asStateFlow()

    private val _subscribe = MutableStateFlow(XHamsterSubscribeState())
    val subscribe: StateFlow<XHamsterSubscribeState> = _subscribe.asStateFlow()

    private var currentId = ""
    private var loadJob: Job? = null
    private var commentsJob: Job? = null
    private var subscribeJob: Job? = null

    fun load(id: String, force: Boolean = false) {
        if (!force && currentId == id && _detailState.value is XHamsterDetailState.Success) return
        currentId = id
        loadJob?.cancel()
        commentsJob?.cancel()
        subscribeJob?.cancel()
        _comments.value = XHamsterCommentsState()
        _subscribe.value = XHamsterSubscribeState()
        _detailState.value = XHamsterDetailState.Loading
        _streamError.value = null
        _playerState.update {
            it.copy(url = "", quality = "", qualityOptions = emptyMap(), isReady = false, isPlaying = false)
        }
        loadJob = viewModelScope.launch {
            XHamsterRepo.detail(id)
                .onSuccess { detail ->
                    _detailState.value = XHamsterDetailState.Success(detail)
                    _subscribe.value = XHamsterSubscribeState(
                        subscribed = detail.uploaderInfo?.subscribed == true,
                        subscribers = detail.uploaderInfo?.subscribers,
                    )
                    seedComments(detail)
                    loadMoreComments()
                    val streams = XHamsterRepo.resolveStreams(detail)
                    if (streams.isEmpty()) {
                        _streamError.value =
                            applicationContext.getString(R.string.xhamster_err_no_stream)
                    } else {
                        applyStreams(streams)
                    }
                }
                .onFailure {
                    Log.e(TAG, "detail failed", it)
                    _detailState.value =
                        XHamsterDetailState.Error(it.message ?: it.javaClass.simpleName)
                }
        }
    }

    private fun seedComments(detail: XHamsterDetail) {
        _comments.value = XHamsterCommentsState(
            items = detail.comments,
            total = detail.commentsTotal ?: detail.comments.size.toLong(),
            page = 0,
            hasMore = detail.entityId != null,
        )
    }

    fun loadMoreComments() {
        val detail = (_detailState.value as? XHamsterDetailState.Success)?.detail ?: return
        val entityId = detail.entityId ?: return
        val state = _comments.value
        if (state.loading || (state.page > 0 && !state.hasMore)) return
        val nextPage = state.page + 1
        commentsJob?.cancel()
        _comments.update { it.copy(loading = true, error = null) }
        commentsJob = viewModelScope.launch {
            XHamsterRepo.comments(entityId, detail.code, nextPage)
                .onSuccess { page ->
                    _comments.update { cur ->
                        val merged = if (nextPage == 1) {
                            page.items
                        } else {
                            val seen = cur.items.mapTo(HashSet()) { it.id }
                            cur.items + page.items.filter { it.id !in seen }
                        }
                        cur.copy(
                            items = merged,
                            total = page.total,
                            page = page.page,
                            hasMore = page.hasMore,
                            loading = false,
                            error = null,
                            writeDenied = page.writeDenied,
                        )
                    }
                }
                .onFailure { e ->
                    Log.e(TAG, "comments failed", e)
                    _comments.update {
                        it.copy(loading = false, error = e.message ?: e.javaClass.simpleName)
                    }
                }
        }
    }

    fun retryComments() = loadMoreComments()

    fun toggleSubscribe() {
        val detail = (_detailState.value as? XHamsterDetailState.Success)?.detail ?: return
        val uploader = detail.uploaderInfo ?: return
        val current = _subscribe.value
        if (current.busy) return
        val target = !current.subscribed
        _subscribe.value = current.copy(busy = true, message = null)
        subscribeJob?.cancel()
        subscribeJob = viewModelScope.launch {
            XHamsterRepo.setSubscribed(uploader, detail.code, target)
                .onSuccess {
                    _subscribe.update {
                        it.copy(
                            subscribed = target,
                            subscribers = it.subscribers?.plus(if (target) 1 else -1)
                                ?.coerceAtLeast(0),
                            busy = false,
                        )
                    }
                }
                .onFailure { e ->
                    Log.e(TAG, "subscribe failed", e)
                    _subscribe.update {
                        it.copy(
                            busy = false,
                            message = if (e.message == "LOGIN_REQUIRED") {
                                SubscribeMessage.LoginRequired
                            } else SubscribeMessage.Failed,
                        )
                    }
                }
        }
    }

    fun consumeSubscribeMessage() = _subscribe.update { it.copy(message = null) }

    private fun applyStreams(streams: List<XHamsterStream>) {
        val chosen = pick(streams)
        val options = LinkedHashMap<String, String>()
        streams.forEach { options.putIfAbsent(it.label, it.url) }
        _playerState.update {
            it.copy(
                url = chosen.url,
                quality = chosen.label,
                qualityOptions = options,
                isReady = true,
            )
        }
    }

    private fun pick(streams: List<XHamsterStream>): XHamsterStream {
        val auto = streams.firstOrNull { it.height == 0 }
        val preferred = XHamsterSettings.preferredQuality
        val target = preferred.removeSuffix("p").toIntOrNull()
        if (target == null) {
            return auto ?: streams.maxByOrNull { it.height } ?: streams.first()
        }
        return streams.filter { it.height in 1..target }.maxByOrNull { it.height }
            ?: auto
            ?: streams.minByOrNull { if (it.height > 0) it.height else Int.MAX_VALUE }
            ?: streams.first()
    }

    fun setQuality(label: String) {
        val url = _playerState.value.qualityOptions[label] ?: return
        _playerState.update { it.copy(url = url, quality = label) }
    }

    fun setSpeed(speed: Float) = _playerState.update { it.copy(speed = speed) }

    fun setPlaying(playing: Boolean) = _playerState.update { it.copy(isPlaying = playing) }

    fun setPosition(position: Long, duration: Long) = _playerState.update {
        it.copy(position = position, duration = if (duration > 0) duration else it.duration)
    }

    fun setFullscreen(fullscreen: Boolean) = _playerState.update { it.copy(isFullscreen = fullscreen) }

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
        commentsJob?.cancel()
        subscribeJob?.cancel()
    }
}
