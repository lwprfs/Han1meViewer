package com.yenaly.han1meviewer.MissAV

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
        get() {
            val format = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
            return format.format(Date(watchDate))
        }

    val formattedWatchDuration: String
        get() {
            val minutes = watchDuration / 60000
            val seconds = (watchDuration % 60000) / 1000
            return if (minutes > 0) {
                if (seconds > 0) "${minutes}m ${seconds}s" else "${minutes}m"
            } else {
                "${seconds}s"
            }
        }

    val formattedLastPosition: String
        get() {
            val minutes = lastPosition / 60000
            val seconds = (lastPosition % 60000) / 1000
            return if (minutes > 0) {
                if (seconds > 0) "${minutes}m ${seconds}s" else "${minutes}m"
            } else {
                "${seconds}s"
            }
        }

    val progressPercentage: Float
        get() = if (totalDuration > 0) lastPosition.toFloat() / totalDuration else 0f
}

class MissAvHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val _historyState = MutableStateFlow<PageLoadingState<List<MissAvHistoryItem>>>(PageLoadingState.NoMoreData)
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

    init {
        MissAvHistoryRepo.init(application)
    }

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
                totalCount = withContext(Dispatchers.IO) {
                    dao.getTotalCount()
                }
                if (totalCount == 0) {
                    hasMore = false
                    _historyState.value = PageLoadingState.NoMoreData
                    return
                }
            }
            
            val offset = (page - 1) * pageSize
            val histories = withContext(Dispatchers.IO) {
                dao.getPage(pageSize, offset)
            }
            
            if (histories.isEmpty()) {
                hasMore = false
                if (page == 1) {
                    _historyState.value = PageLoadingState.NoMoreData
                }
                _isLoadingMore.value = false
                return
            }
            
            val pageItems = histories.map { history ->
                MissAvHistoryItem(
                    videoInfo = HanimeInfo(
                        title = history.title,
                        coverUrl = history.coverUrl,
                        videoCode = history.videoCode,
                        duration = "",
                        views = "",
                        uploadTime = "",
                        itemType = HanimeInfo.NORMAL,
                        currentArtist = "",
                        reviews = "",
                    ),
                    watchDate = history.watchDate,
                    watchDuration = history.watchDuration,
                    lastPosition = history.lastPosition,
                    totalDuration = history.totalDuration,
                    watchCount = history.watchCount,
                    playCount = history.playCount,
                    isPlayed = history.isPlayed,
                    lastPlayedDate = history.lastPlayedDate
                )
            }
            
            _historyItems.update { current ->
                if (page == 1) pageItems else current + pageItems
            }
            
            currentPage = page
            _loadedPageCount.value = page
            _isLoadingMore.value = false
            
            if (pageItems.isNotEmpty()) {
                _historyState.value = PageLoadingState.Success(pageItems)
            }
            
            val loadedCount = _historyItems.value.size
            if (loadedCount >= totalCount) {
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
            try {
                currentPage = 1
                hasMore = true
                _historyItems.value = emptyList()
                _loadedPageCount.value = 0
                _historyState.value = PageLoadingState.Loading
                loadHistoryPage(1)
            } catch (e: Exception) {
                _historyState.value = PageLoadingState.Error(e)
            }
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
            try {
                withContext(Dispatchers.IO) {
                    MissAvHistoryRepo.deleteAll()
                }
                _historyItems.value = emptyList()
                _historyState.value = PageLoadingState.NoMoreData
                totalCount = 0
                hasMore = false
            } catch (e: Exception) {
                // Silent failure
            }
        }
    }

    fun deleteHistoryItem(videoCode: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    MissAvHistoryRepo.deleteByVideoCode(videoCode)
                }
                _historyItems.update { items ->
                    items.filter { it.videoInfo.videoCode != videoCode }
                }
                totalCount = _historyItems.value.size
                if (_historyItems.value.isEmpty()) {
                    _historyState.value = PageLoadingState.NoMoreData
                }
            } catch (e: Exception) {
                // Silent failure
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
        wasPlayed: Boolean
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val existing = MissAvHistoryRepo.getByVideoCode(videoCode)
                    val now = System.currentTimeMillis()

                    val history = if (existing != null) {
                        val newWatchCount = if (wasPlayed) existing.watchCount + 1 else existing.watchCount
                        val newPlayCount = if (wasPlayed && isPlaying) existing.playCount + 1 else existing.playCount
                        val newWatchDuration = if (isPlaying) {
                            existing.watchDuration + (currentPosition - existing.lastPosition).coerceAtLeast(0)
                        } else {
                            existing.watchDuration
                        }

                        existing.copy(
                            watchDate = now,
                            watchDuration = newWatchDuration,
                            lastPosition = currentPosition,
                            totalDuration = totalDuration,
                            watchCount = newWatchCount,
                            playCount = newPlayCount,
                            isPlayed = wasPlayed || existing.isPlayed,
                            lastPlayedDate = if (wasPlayed && isPlaying) now else existing.lastPlayedDate
                        )
                    } else {
                        MissAvHistoryEntity(
                            videoCode = videoCode,
                            title = title,
                            coverUrl = coverUrl,
                            watchDate = now,
                            watchDuration = 0,
                            lastPosition = currentPosition,
                            totalDuration = totalDuration,
                            watchCount = if (wasPlayed) 1 else 0,
                            playCount = if (wasPlayed && isPlaying) 1 else 0,
                            isPlayed = wasPlayed,
                            lastPlayedDate = if (wasPlayed && isPlaying) now else null
                        )
                    }

                    MissAvHistoryRepo.insertOrUpdate(history)
                }
            } catch (e: Exception) {
                // Silent failure - don't crash video playback
            }
        }
    }
}