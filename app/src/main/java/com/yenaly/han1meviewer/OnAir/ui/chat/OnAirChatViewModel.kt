package com.yenaly.han1meviewer.OnAir.ui.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.OnAir.data.model.OnAirChatMessage
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirChatRepo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnAirChatUiState(
    val messages: List<OnAirChatMessage> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSending: Boolean = false,
    val sendError: String? = null,
    val loadError: String? = null,
    val hasCsrfToken: Boolean = false,
    val lastUpdatedAt: Long = 0L,
)

class OnAirChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "OnAirChatViewModel"
    }

    private val _uiState = MutableStateFlow(OnAirChatUiState())
    val uiState = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var sendJob: Job? = null

    init {
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = initial || it.messages.isEmpty(),
                    isRefreshing = !initial && it.messages.isNotEmpty(),
                    loadError = null,
                )
            }
            val result = OnAirChatRepo.fetchChatPage(1)
            result.onSuccess { page ->
                _uiState.update {
                    it.copy(
                        messages = page.messages,
                        isLoading = false,
                        isRefreshing = false,
                        hasCsrfToken = page.csrfToken.isNotBlank(),
                        loadError = page.formError,
                        lastUpdatedAt = System.currentTimeMillis(),
                    )
                }
            }.onFailure { e ->
                Log.e(TAG, "refresh failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        loadError = e.message ?: "Failed to load chat",
                    )
                }
            }
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        if (_uiState.value.isSending) return
        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, sendError = null) }
            when (val result = OnAirChatRepo.postChatMessage(trimmed)) {
                is OnAirChatRepo.ChatPostResult.Success -> {
                    _uiState.update { it.copy(isSending = false, sendError = null) }
                    refresh(initial = false)
                }
                is OnAirChatRepo.ChatPostResult.FormError -> {
                    _uiState.update { it.copy(isSending = false, sendError = result.message) }
                }
                is OnAirChatRepo.ChatPostResult.LoginRequired -> {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            sendError = "Your session expired. Sign in again.",
                        )
                    }
                }
                is OnAirChatRepo.ChatPostResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            sendError = result.throwable.message ?: "Network error",
                        )
                    }
                }
            }
        }
    }

    fun clearSendError() {
        _uiState.update { it.copy(sendError = null) }
    }

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
        sendJob?.cancel()
    }
}
