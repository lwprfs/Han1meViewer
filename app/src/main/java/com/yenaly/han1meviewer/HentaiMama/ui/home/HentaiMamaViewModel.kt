package com.yenaly.han1meviewer.HentaiMama.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.HentaiMama.data.model.SeriesDetailPage
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetworkRepo
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class HentaiMamaCategoryRowState(
    val category: HentaiMamaHomeCategory,
    val videos: List<HanimeInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class HentaiMamaViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HentaiMamaVM"
        private const val CATEGORY_TTL_MS = 5 * 60 * 1000L
    }

    private val _homeState =
        MutableStateFlow<WebsiteState<com.yenaly.han1meviewer.HentaiMama.data.model.HentaiMamaHomePage>>(
            WebsiteState.Loading
        )
    val homeState = _homeState.asStateFlow()

    private val _categoryRows = MutableStateFlow<List<HentaiMamaCategoryRowState>>(emptyList())
    val categoryRows = _categoryRows.asStateFlow()

    private val _allCategories = MutableStateFlow<List<HentaiMamaHomeCategory>>(emptyList())
    val allCategories = _allCategories.asStateFlow()

    val hasUserCategoryOverride: Boolean
        get() = HentaiMamaHomeCategoryRepo.hasOverride()

    private val categoryJobs = ConcurrentHashMap<String, Job>()
    private val categoryFetchedAt = ConcurrentHashMap<String, Long>()
    @Volatile private var categoriesLoaded = false

    private val _searchState =
        MutableStateFlow<PageLoadingState<List<HanimeInfo>>>(PageLoadingState.Loading)
    val searchState = _searchState.asStateFlow()

    private val _searchResults = MutableStateFlow<List<HanimeInfo>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchInitialQuery = MutableStateFlow<String?>(null)
    val searchInitialQuery = _searchInitialQuery.asStateFlow()

    private val _searchPage = MutableStateFlow(1)
    val searchPage = _searchPage.asStateFlow()

    private val _searchHasMore = MutableStateFlow(true)
    val searchHasMore = _searchHasMore.asStateFlow()

    private val _searchHasSearched = MutableStateFlow(false)
    val searchHasSearched = _searchHasSearched.asStateFlow()

    private val _searchIsLoadingMore = MutableStateFlow(false)
    val searchIsLoadingMore = _searchIsLoadingMore.asStateFlow()

    private val _searchIsFilterMode = MutableStateFlow(false)
    val searchIsFilterMode = _searchIsFilterMode.asStateFlow()

    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre = _selectedGenre.asStateFlow()

    private val _selectedProducer = MutableStateFlow<String?>(null)
    val selectedProducer = _selectedProducer.asStateFlow()

    private val _selectedYear = MutableStateFlow<String?>(null)
    val selectedYear = _selectedYear.asStateFlow()

    private val _selectedOrder = MutableStateFlow<String?>(null)
    val selectedOrder = _selectedOrder.asStateFlow()

    private val _seriesState =
        MutableStateFlow<VideoLoadingState<SeriesDetailPage>>(VideoLoadingState.Loading)
    val seriesState = _seriesState.asStateFlow()

    private var searchJob: Job? = null
    private var seriesJob: Job? = null

    init {
        viewModelScope.launch {
            loadCategories()
            syncCategoryRows()
        }
    }

    private suspend fun loadCategories() {
        _allCategories.value = HentaiMamaHomeCategoryRepo.load(getApplication())
        categoriesLoaded = true
    }

    private fun syncCategoryRows() {
        val visible = _allCategories.value.filterNot { it.hidden }
        val existing = _categoryRows.value.associateBy { it.category.key }

        val rebuilt = visible.map { cat ->
            existing[cat.key]?.copy(category = cat)
                ?: HentaiMamaCategoryRowState(category = cat)
        }
        _categoryRows.value = rebuilt

        if (categoriesLoaded) {
            rebuilt.filter { it.videos.isEmpty() && !it.isLoading }
                .forEach { fetchCategory(it.category, force = false) }
        }
    }

    fun refreshAllCategories() {
        categoryFetchedAt.clear()
        _categoryRows.value.forEach { fetchCategory(it.category, force = true) }
    }

    fun retryCategory(category: HentaiMamaHomeCategory) {
        fetchCategory(category, force = true)
    }

    private fun fetchCategory(category: HentaiMamaHomeCategory, force: Boolean) {
        val key: String = category.key
        val existing: Job? = categoryJobs[key]

        if (!force && existing != null && existing.isActive) return
        if (!force) {
            val last: Long = categoryFetchedAt[key] ?: 0L
            if (System.currentTimeMillis() - last < CATEGORY_TTL_MS) return
        }

        if (force) existing?.cancel()

        categoryJobs[key] = viewModelScope.launch {
            updateRow(key) { it.copy(isLoading = true, error = null) }
            try {
                val videos: List<HanimeInfo> = withContext(Dispatchers.IO) {
                    HentaiMamaNetworkRepo.getCategoryVideos(category)
                }
                updateRow(key) {
                    it.copy(videos = videos, isLoading = false, error = null)
                }
                categoryFetchedAt[key] = System.currentTimeMillis()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Category '$key' failed: ${e.message}", e)
                updateRow(key) {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load")
                }
            }
        }
    }

    private inline fun updateRow(
        key: String,
        transform: (HentaiMamaCategoryRowState) -> HentaiMamaCategoryRowState,
    ) {
        _categoryRows.update { rows ->
            rows.map { if (it.category.key == key) transform(it) else it }
        }
    }

    private fun generateKey(title: String, existingKeys: Set<String>): String {
        val base = title.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "category" }
        if (base !in existingKeys) return base
        var i = 1
        while ("${base}_$i" in existingKeys) i++
        return "${base}_$i"
    }

    fun addCategory(title: String, genrePath: String, sort: String?) {
        val trimmedTitle = title.trim()
        val trimmedPath = genrePath.trim().removePrefix("/")
        if (trimmedTitle.isBlank() || trimmedPath.isBlank()) return

        val key = generateKey(trimmedTitle, _allCategories.value.map { it.key }.toSet())
        val newCategory = HentaiMamaHomeCategory(
            key = key,
            title = trimmedTitle,
            genrePath = trimmedPath,
            sort = sort?.trim()?.takeIf { it.isNotEmpty() },
            hidden = false,
        )
        updateCategories(_allCategories.value + newCategory)
    }

    fun updateCategory(updated: HentaiMamaHomeCategory) {
        updateCategories(
            _allCategories.value.map { if (it.key == updated.key) updated else it }
        )
        val row = _categoryRows.value.find { it.category.key == updated.key }
        if (row != null) fetchCategory(updated, force = true)
    }

    fun deleteCategory(key: String) {
        categoryJobs.remove(key)?.cancel()
        categoryFetchedAt.remove(key)
        updateCategories(_allCategories.value.filterNot { it.key == key })
    }

    fun setCategoryHidden(key: String, hidden: Boolean) {
        updateCategories(
            _allCategories.value.map { if (it.key == key) it.copy(hidden = hidden) else it }
        )
    }

    fun reorderCategory(fromIndex: Int, toIndex: Int) {
        val list = _allCategories.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices) return
        if (fromIndex == toIndex) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        updateCategories(list)
    }

    fun resetCategoriesToDefaults() {
        viewModelScope.launch {
            HentaiMamaHomeCategoryRepo.resetToDefaults()
            categoryJobs.values.forEach { it.cancel() }
            categoryJobs.clear()
            categoryFetchedAt.clear()
            _categoryRows.value = emptyList()
            loadCategories()
            syncCategoryRows()
        }
    }

    private fun updateCategories(newList: List<HentaiMamaHomeCategory>) {
        _allCategories.value = newList
        viewModelScope.launch { HentaiMamaHomeCategoryRepo.save(getApplication(), newList) }
        syncCategoryRows()
    }

    fun getHomePage(force: Boolean = false) {
        if (!force && _homeState.value is WebsiteState.Success) return
        viewModelScope.launch {
            HentaiMamaNetworkRepo.getHomePage().collect { _homeState.value = it }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearchInitialQuery(query: String?) {
        _searchInitialQuery.value = query
    }

    fun markSearchStarted() {
        _searchHasSearched.value = true
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchJob = null
        _searchQuery.value = ""
        _searchInitialQuery.value = null
        _searchPage.value = 1
        _searchHasMore.value = true
        _searchHasSearched.value = false
        _searchIsLoadingMore.value = false
        _searchIsFilterMode.value = false
        _searchResults.value = emptyList()
        _searchState.value = PageLoadingState.Loading
    }

    fun resetAll() {
        clearSearch()
        clearFilters()
        seriesJob?.cancel()
        _seriesState.value = VideoLoadingState.Loading
        categoryJobs.values.forEach { it.cancel() }
        categoryJobs.clear()
        categoryFetchedAt.clear()
        _categoryRows.value = emptyList()
        _homeState.value = WebsiteState.Loading
    }

    fun searchVideos(page: Int, query: String) {
        if (query.isBlank()) return
        searchJob?.cancel()
        _searchHasSearched.value = true
        _searchIsFilterMode.value = false
        _searchHasMore.value = true

        if (page <= 1) {
            _searchPage.value = 1
            _searchResults.value = emptyList()
            _searchState.value = PageLoadingState.Loading
            _searchIsLoadingMore.value = false
        } else {
            _searchIsLoadingMore.value = true
        }

        searchJob = viewModelScope.launch {
            HentaiMamaNetworkRepo.searchVideos(page, query).collect { state ->
                if (!isActive) return@collect
                _searchState.value = state
                when (state) {
                    is PageLoadingState.Success -> {
                        val incoming = state.info
                        _searchResults.update { prev ->
                            if (page <= 1) incoming
                            else (prev + incoming).distinctBy(HanimeInfo::videoCode)
                        }
                        if (incoming.isEmpty()) _searchHasMore.value = false
                        _searchPage.value = page
                        _searchIsLoadingMore.value = false
                    }
                    is PageLoadingState.NoMoreData -> {
                        _searchHasMore.value = false
                        _searchIsLoadingMore.value = false
                    }
                    is PageLoadingState.Error -> {
                        _searchIsLoadingMore.value = false
                    }
                    is PageLoadingState.Loading -> Unit
                }
            }
        }
    }

    fun filterVideos(page: Int) {
        searchJob?.cancel()
        _searchHasSearched.value = true
        _searchIsFilterMode.value = true
        _searchHasMore.value = true

        if (page <= 1) {
            _searchPage.value = 1
            _searchResults.value = emptyList()
            _searchState.value = PageLoadingState.Loading
            _searchIsLoadingMore.value = false
        } else {
            _searchIsLoadingMore.value = true
        }

        searchJob = viewModelScope.launch {
            HentaiMamaNetworkRepo.filterVideos(
                page = page,
                genre = _selectedGenre.value,
                producer = _selectedProducer.value,
                year = _selectedYear.value,
                order = _selectedOrder.value,
            ).collect { state ->
                if (!isActive) return@collect
                _searchState.value = state
                when (state) {
                    is PageLoadingState.Success -> {
                        val incoming = state.info
                        _searchResults.update { prev ->
                            if (page <= 1) incoming
                            else (prev + incoming).distinctBy(HanimeInfo::videoCode)
                        }
                        if (incoming.isEmpty()) _searchHasMore.value = false
                        _searchPage.value = page
                        _searchIsLoadingMore.value = false
                    }
                    is PageLoadingState.NoMoreData -> {
                        _searchHasMore.value = false
                        _searchIsLoadingMore.value = false
                    }
                    is PageLoadingState.Error -> {
                        _searchIsLoadingMore.value = false
                    }
                    is PageLoadingState.Loading -> Unit
                }
            }
        }
    }

    fun loadNextSearchPage() {
        if (_searchIsLoadingMore.value || !_searchHasMore.value) return
        val next = _searchPage.value + 1
        if (_searchIsFilterMode.value) filterVideos(next)
        else searchVideos(next, _searchQuery.value)
    }

    fun setGenre(genre: String?) { _selectedGenre.value = genre }
    fun setProducer(producer: String?) { _selectedProducer.value = producer }
    fun setYear(year: String?) { _selectedYear.value = year }
    fun setOrder(order: String?) { _selectedOrder.value = order }

    fun clearFilters() {
        _selectedGenre.value = null
        _selectedProducer.value = null
        _selectedYear.value = null
        _selectedOrder.value = null
    }

    fun hasActiveFilters(): Boolean =
        _selectedGenre.value != null ||
                _selectedProducer.value != null ||
                _selectedYear.value != null ||
                _selectedOrder.value != null

    fun getSeriesDetail(url: String) {
        seriesJob?.cancel()
        seriesJob = viewModelScope.launch {
            _seriesState.value = VideoLoadingState.Loading
            HentaiMamaNetworkRepo.getSeriesDetail(url).collect { state ->
                if (!isActive) return@collect
                _seriesState.value = state
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        categoryJobs.values.forEach { it.cancel() }
        searchJob?.cancel()
        seriesJob?.cancel()
    }
}