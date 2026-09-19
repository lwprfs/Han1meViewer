package com.yenaly.han1meviewer.MissAV.ui.history
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.yenaly.han1meviewer.MissAV.data.local.MissAvDatabase
import com.yenaly.han1meviewer.MissAV.data.repository.MissAvHistoryRepo
data class MissAvHistoryItem(
    val videoInfo: HanimeInfo,
    val watchDate: Long,
    val watchDuration: Long,
    val lastPosition: Long,
    val totalDuration: Long,
    val watchCount: Int,
    val playCount: Int,
    val isPlayed: Boolean,
    val lastPlayedDate: Long?
) {
    val formattedWatchDate: String
        get() = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
            .format(Date(watchDate))

    val formattedWatchDuration: String
        get() {
            val m = watchDuration / 60000
            val s = (watchDuration % 60000) / 1000
            return if (m > 0) { if (s > 0) "${m}m ${s}s" else "${m}m" } else "${s}s"
        }

    val formattedLastPosition: String
        get() {
            val m = lastPosition / 60000
            val s = (lastPosition % 60000) / 1000
            return if (m > 0) { if (s > 0) "${m}m ${s}s" else "${m}m" } else "${s}s"
        }

    val progressPercentage: Float
        get() = if (totalDuration > 0) lastPosition.toFloat() / totalDuration else 0f
}

class MissAvHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val _historyState =
        MutableStateFlow<PageLoadingState<List<MissAvHistoryItem>>>(PageLoadingState.NoMoreData)
    val historyState = _historyState.asStateFlow()

    private val _historyItems = MutableStateFlow<List<MissAvHistoryItem>>(emptyList())
    val historyItems = _historyItems.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _loadedPageCount = MutableStateFlow(0)
    val loadedPageCount = _loadedPageCount.asStateFlow()

    private var currentPage = 1
    private var hasMore = true
    private val pageSize = 20
    private var totalCount = 0

    init { MissAvHistoryRepo.init(application) }

    fun loadHistory() {
        viewModelScope.launch {
            _historyState.value = PageLoadingState.Loading
            loadHistoryPage(1)
        }
    }

    suspend fun loadHistoryPage(page: Int) {
        try {
            val dao = MissAvDatabase.getInstance(getApplication()).missAvHistoryDao()

            if (page == 1) {
                totalCount = withContext(Dispatchers.IO) { dao.getTotalCount() }
                if (totalCount == 0) {
                    hasMore = false
                    _historyState.value = PageLoadingState.NoMoreData
                    return
                }
            }

            val offset = (page - 1) * pageSize
            val histories = withContext(Dispatchers.IO) { dao.getPage(pageSize, offset) }

            if (histories.isEmpty()) {
                hasMore = false
                if (page == 1) _historyState.value = PageLoadingState.NoMoreData
                _isLoadingMore.value = false
                return
            }

            val pageItems = histories.map { h ->
                MissAvHistoryItem(
                    videoInfo = HanimeInfo(
                        title = h.title,
                        coverUrl = h.coverUrl,
                        videoCode = h.videoCode,
                        duration = "",
                        views = "",
                        uploadTime = "",
                        itemType = HanimeInfo.NORMAL,
                        currentArtist = "",
                        reviews = "",
                    ),
                    watchDate = h.watchDate,
                    watchDuration = h.watchDuration,
                    lastPosition = h.lastPosition,
                    totalDuration = h.totalDuration,
                    watchCount = h.watchCount,
                    playCount = h.playCount,
                    isPlayed = h.isPlayed,
                    lastPlayedDate = h.lastPlayedDate,
                )
            }

            _historyItems.update { cur -> if (page == 1) pageItems else cur + pageItems }
            currentPage = page
            _loadedPageCount.value = page
            _isLoadingMore.value = false
            if (pageItems.isNotEmpty()) _historyState.value = PageLoadingState.Success(pageItems)

            if (_historyItems.value.size >= totalCount) {
                hasMore = false
                _historyState.value = PageLoadingState.NoMoreData
            }
        } catch (e: Exception) {
            _historyState.value = PageLoadingState.Error(e)
            _isLoadingMore.value = false
        }
    }

    fun refresh() {
        viewModelScope.launch {
            currentPage = 1
            hasMore = true
            _historyItems.value = emptyList()
            _loadedPageCount.value = 0
            _historyState.value = PageLoadingState.Loading
            loadHistoryPage(1)
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || !hasMore || _historyState.value is PageLoadingState.Loading) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            loadHistoryPage(currentPage + 1)
        }
    }

    fun deleteAllHistory() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { MissAvHistoryRepo.deleteAll() }
                _historyItems.value = emptyList()
                _historyState.value = PageLoadingState.NoMoreData
                totalCount = 0
                hasMore = false
            }
        }
    }

    fun deleteHistoryItem(videoCode: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { MissAvHistoryRepo.deleteByVideoCode(videoCode) }
                _historyItems.update { list -> list.filter { it.videoInfo.videoCode != videoCode } }
                totalCount = _historyItems.value.size
                if (_historyItems.value.isEmpty()) {
                    _historyState.value = PageLoadingState.NoMoreData
                }
            }
        }
    }

    fun updateWatchHistory(
        videoCode: String,
        title: String,
        coverUrl: String,
        currentPosition: Long,
        totalDuration: Long,
        isPlaying: Boolean,
        wasPlayed: Boolean,
    ) {
        viewModelScope.launch {
            runCatching {
                MissAvHistoryRepo.upsertWatchProgress(
                    videoCode = videoCode,
                    title = title,
                    coverUrl = coverUrl,
                    currentPosition = currentPosition,
                    totalDuration = totalDuration,
                    isPlaying = isPlaying,
                    wasPlayed = wasPlayed,
                )
            }
        }
    }
}