package com.yenaly.han1meviewer.SupJav.ui.video

import com.yenaly.han1meviewer.ui.component.player.SitePlayerState
import com.yenaly.yenaly_libs.utils.applicationContext
import com.yenaly.han1meviewer.R
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.SupJav.common.SupJavConstants
import com.yenaly.han1meviewer.SupJav.common.SupJavSettings
import com.yenaly.han1meviewer.SupJav.data.model.SupJavDetail
import com.yenaly.han1meviewer.SupJav.data.model.SupJavServer
import com.yenaly.han1meviewer.SupJav.data.remote.SupJavRepo
import com.yenaly.han1meviewer.SupJav.data.remote.SupJavStreamResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

typealias SupJavPlayerState = SitePlayerState

sealed interface SupJavDetailState {
    data object Loading : SupJavDetailState
    data class Success(val detail: SupJavDetail) : SupJavDetailState
    data class Error(val message: String) : SupJavDetailState
}

sealed interface SupJavStreamState {
    data object Idle : SupJavStreamState
    data object Loading : SupJavStreamState
    data class Failed(val reason: String) : SupJavStreamState
    data object Success : SupJavStreamState
}

class SupJavVideoViewModel : ViewModel() {

    private companion object {
        const val TAG = "SupJavVideoVM"
    }

    private val _detailState = MutableStateFlow<SupJavDetailState>(SupJavDetailState.Loading)
    val detailState: StateFlow<SupJavDetailState> = _detailState.asStateFlow()

    private val _playerState = MutableStateFlow(SupJavPlayerState())
    val playerState: StateFlow<SupJavPlayerState> = _playerState.asStateFlow()

    private val _streamState = MutableStateFlow<SupJavStreamState>(SupJavStreamState.Idle)
    val streamState: StateFlow<SupJavStreamState> = _streamState.asStateFlow()

    private val _selectedServer = MutableStateFlow(-1)
    val selectedServer: StateFlow<Int> = _selectedServer.asStateFlow()

    private val _referer = MutableStateFlow("")
    val referer: StateFlow<String> = _referer.asStateFlow()

    private var currentCode: String = ""
    private var loadJob: Job? = null
    private var streamJob: Job? = null

    fun load(code: String, force: Boolean = false) {
        if (!force && currentCode == code && _detailState.value is SupJavDetailState.Success) return
        currentCode = code
        loadJob?.cancel()
        streamJob?.cancel()
        _detailState.value = SupJavDetailState.Loading
        _streamState.value = SupJavStreamState.Idle
        _selectedServer.value = -1
        _playerState.update {
            it.copy(url = "", quality = "", qualityOptions = emptyMap(), isReady = false, isPlaying = false)
        }
        loadJob = viewModelScope.launch {
            SupJavRepo.detail(code)
                .onSuccess { detail ->
                    _detailState.value = SupJavDetailState.Success(detail)
                    resolveAuto(detail)
                }
                .onFailure {
                    Log.e(TAG, "detail failed", it)
                    _detailState.value =
                        SupJavDetailState.Error(it.message ?: it.javaClass.simpleName)
                }
        }
    }

    fun isSupported(server: SupJavServer): Boolean =
        SupJavConstants.SUPPORTED_SERVERS.any { it.equals(server.name, ignoreCase = true) }

    private fun orderedIndices(detail: SupJavDetail): List<Int> {
        val supported = detail.servers.indices.filter { isSupported(detail.servers[it]) }
        val enabled = supported.filter { SupJavSettings.isServerEnabled(detail.servers[it].name) }
        return (enabled.ifEmpty { supported })
            .sortedBy { SupJavSettings.serverRank(detail.servers[it].name) }
    }

    private fun resolveAuto(detail: SupJavDetail) {
        val order = orderedIndices(detail)
        if (order.isEmpty()) {
            _streamState.value =
                SupJavStreamState.Failed(applicationContext.getString(R.string.supjav_err_no_server))
            return
        }
        resolveFrom(detail, if (SupJavSettings.autoFallback) order else order.take(1))
    }

    fun selectServer(index: Int) {
        val detail = (_detailState.value as? SupJavDetailState.Success)?.detail ?: return
        if (index !in detail.servers.indices) return
        _selectedServer.value = index
        if (!isSupported(detail.servers[index])) {
            _streamState.value = SupJavStreamState.Failed(
                applicationContext.getString(R.string.supjav_err_unsupported_server, detail.servers[index].name)
            )
            return
        }
        resolveFrom(detail, listOf(index))
    }

    fun retry() {
        val detail = (_detailState.value as? SupJavDetailState.Success)?.detail ?: return
        val index = _selectedServer.value
        if (index in detail.servers.indices && isSupported(detail.servers[index])) {
            val order = if (SupJavSettings.autoFallback) {
                listOf(index) + orderedIndices(detail).filter { it != index }
            } else listOf(index)
            resolveFrom(detail, order)
        } else {
            resolveAuto(detail)
        }
    }

    private fun resolveFrom(detail: SupJavDetail, order: List<Int>) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            _streamState.value = SupJavStreamState.Loading
            _playerState.update { it.copy(url = "", isReady = false) }
            for (i in order) {
                val server = detail.servers[i]
                _selectedServer.value = i
                val streams = runCatching { SupJavStreamResolver.resolve(server) }
                    .onFailure { Log.e(TAG, "resolve ${server.name} failed", it) }
                    .getOrDefault(emptyList())
                val stream = streams.firstOrNull() ?: continue
                _referer.value = stream.referer
                com.yenaly.han1meviewer.SupJav.data.remote.SupJavNetwork
                    .rememberStreamReferer(currentCode, stream.referer)
                _playerState.update {
                    it.copy(
                        url = stream.url,
                        quality = stream.label,
                        qualityOptions = streams.associate { s -> s.label to s.url },
                        isReady = true,
                    )
                }
                _streamState.value = SupJavStreamState.Success
                return@launch
            }
            _streamState.value =
                SupJavStreamState.Failed(applicationContext.getString(R.string.supjav_err_no_link))
        }
    }

    fun setQuality(quality: String) {
        val url = _playerState.value.qualityOptions[quality] ?: return
        _playerState.update { it.copy(quality = quality, url = url) }
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
        streamJob?.cancel()
    }
}
