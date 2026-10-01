package com.yenaly.han1meviewer.HentaiMama.ui.history
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.HentaiMama.data.local.HentaiMamaHistoryEntity
import com.yenaly.han1meviewer.HentaiMama.data.local.HentaiMamaHistoryRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HentaiMamaHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val _historyItems = MutableStateFlow<List<HentaiMamaHistoryEntity>>(emptyList())
    val historyItems = _historyItems.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore = _hasMore.asStateFlow()

    private val pageSize = 20
    private var currentPage = 1
    private var totalCount = 0

    init {
        HentaiMamaHistoryRepo.init(application)
    }

    fun refresh() {
        viewModelScope.launch {
            currentPage = 1
            _hasMore.value = true
            _historyItems.value = emptyList()
            totalCount = HentaiMamaHistoryRepo.getTotalCount()
            loadPage(1)
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            loadPage(currentPage + 1)
            _isLoadingMore.value = false
        }
    }

    private suspend fun loadPage(page: Int) {
        val offset = (page - 1) * pageSize
        val items = HentaiMamaHistoryRepo.getPage(pageSize, offset)
        if (items.isEmpty()) {
            _hasMore.value = false
            return
        }
        currentPage = page
        _historyItems.value = if (page == 1) items else _historyItems.value + items
        if (_historyItems.value.size >= totalCount) _hasMore.value = false
    }

    fun deleteItem(videoCode: String) {
        viewModelScope.launch {
            HentaiMamaHistoryRepo.deleteByVideoCode(videoCode)
            _historyItems.value = _historyItems.value.filterNot { it.videoCode == videoCode }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            HentaiMamaHistoryRepo.deleteAll()
            _historyItems.value = emptyList()
            _hasMore.value = false
        }
    }
}
