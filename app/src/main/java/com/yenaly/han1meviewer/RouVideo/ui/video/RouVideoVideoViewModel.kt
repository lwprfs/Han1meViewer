package com.yenaly.han1meviewer.RouVideo.ui.video

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.RouVideo.common.RouVideoConstants
import com.yenaly.han1meviewer.RouVideo.common.RouVideoSettings
import com.yenaly.han1meviewer.RouVideo.data.model.RouVideoDetail
import com.yenaly.han1meviewer.RouVideo.data.remote.RouVideoRepo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RouVideoPlayerState(
    val url: String = "",
    val quality: String = "",
    val qualityOptions: Map<String, String> = emptyMap(),
    val availableSpeeds: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f),
    val speed: Float = 1.0f,
    val isPlaying: Boolean = false,
    val isReady: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val hasSubtitle: Boolean = false,
    val isFullscreen: Boolean = false,
)

sealed interface RouVideoDetailState {
    data object Loading : RouVideoDetailState
    data class Success(val detail: RouVideoDetail) : RouVideoDetailState
    data class Error(val message: String) : RouVideoDetailState
}

class RouVideoVideoViewModel : ViewModel() {

    private companion object {
        const val TAG = "RouVideoVideoVM"
    }

    private val _detailState = MutableStateFlow<RouVideoDetailState>(RouVideoDetailState.Loading)
    val detailState: StateFlow<RouVideoDetailState> = _detailState.asStateFlow()

    private val _playerState = MutableStateFlow(RouVideoPlayerState())
    val playerState: StateFlow<RouVideoPlayerState> = _playerState.asStateFlow()

    private var currentId = ""
    private var loadJob: Job? = null

    fun load(id: String, force: Boolean = false) {
        if (!force && currentId == id && _detailState.value is RouVideoDetailState.Success) return
        currentId = id
        loadJob?.cancel()
        _detailState.value = RouVideoDetailState.Loading
        _playerState.update {
            it.copy(url = "", quality = "", qualityOptions = emptyMap(), isReady = false, isPlaying = false)
        }
        loadJob = viewModelScope.launch {
            RouVideoRepo.detail(id)
                .onSuccess { detail ->
                    _detailState.value = RouVideoDetailState.Success(detail)

                    val url = RouVideoConstants.absolute(detail.streamPath)
                    _playerState.update {
                        it.copy(
                            url = url,
                            quality = "",
                            qualityOptions = emptyMap(),
                            isReady = true,
                        )
                    }
                    if (RouVideoSettings.reportPlays) {
                        launch { RouVideoRepo.reportPlay(detail.id, detail.tags) }
                    }
                }
                .onFailure {
                    Log.e(TAG, "detail failed", it)
                    _detailState.value =
                        RouVideoDetailState.Error(it.message ?: it.javaClass.simpleName)
                }
        }
    }

    fun reloadStream() {
        val detail = (_detailState.value as? RouVideoDetailState.Success)?.detail ?: return
        _playerState.update { it.copy(url = "", isReady = false) }
        viewModelScope.launch {
            _playerState.update {
                it.copy(url = RouVideoConstants.absolute(detail.streamPath), isReady = true)
            }
        }
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
