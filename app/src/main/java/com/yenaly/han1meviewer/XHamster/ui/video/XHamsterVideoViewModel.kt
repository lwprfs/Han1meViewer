package com.yenaly.han1meviewer.XHamster.ui.video

import com.yenaly.han1meviewer.ui.component.player.SitePlayerState
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.XHamster.common.XHamsterSettings
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

    private var currentId = ""
    private var loadJob: Job? = null

    fun load(id: String, force: Boolean = false) {
        if (!force && currentId == id && _detailState.value is XHamsterDetailState.Success) return
        currentId = id
        loadJob?.cancel()
        _detailState.value = XHamsterDetailState.Loading
        _streamError.value = null
        _playerState.update {
            it.copy(url = "", quality = "", qualityOptions = emptyMap(), isReady = false, isPlaying = false)
        }
        loadJob = viewModelScope.launch {
            XHamsterRepo.detail(id)
                .onSuccess { detail ->
                    _detailState.value = XHamsterDetailState.Success(detail)
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
    }
}
