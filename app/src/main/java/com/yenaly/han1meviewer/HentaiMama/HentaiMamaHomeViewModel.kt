package com.yenaly.han1meviewer.HentaiMama

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HentaiMamaCategoryRowState(
    val category: HentaiMamaHomeCategory,
    val videos: List<HanimeInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class HentaiMamaViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "HentaiMamaVM"

    private val _homeState =
        MutableStateFlow<WebsiteState<HentaiMamaHomePage>>(WebsiteState.Loading)
    val homeState = _homeState.asStateFlow()

    private val _categoryRows =
        MutableStateFlow<List<HentaiMamaCategoryRowState>>(emptyList())
    val categoryRows = _categoryRows.asStateFlow()

    private val _allCategories =
        MutableStateFlow<List<HentaiMamaHomeCategory>>(emptyList())
    val allCategories = _allCategories.asStateFlow()

    val hasUserCategoryOverride: Boolean
        get() = HentaiMamaHomeCategoryRepo.hasOverride()

    private val categoryJobs = mutableMapOf<String, Job>()
    private val categoryFetchedAt = mutableMapOf<String, Long>()
    private var categoriesLoaded = false

    private val _searchState =
        MutableStateFlow<PageLoadingState<List<HanimeInfo>>>(PageLoadingState.Loading)
    val searchState = _searchState.asStateFlow()

    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre = _selectedGenre.asStateFlow()

    private val _selectedProducer = MutableStateFlow<String?>(null)
    val selectedProducer = _selectedProducer.asStateFlow()

    private val _selectedYear = MutableStateFlow<String?>(null)
    val selectedYear = _selectedYear.asStateFlow()

    private val _selectedOrder = MutableStateFlow<String?>(null)
    val selectedOrder = _selectedOrder.asStateFlow()

    private val _videoState =
        MutableStateFlow<VideoLoadingState<HentaiMamaVideoInfo>>(VideoLoadingState.Loading)
    val videoState = _videoState.asStateFlow()

    companion object {
        private const val CATEGORY_TTL_MS = 5 * 60 * 1000L
    }

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
        val now = System.currentTimeMillis()
        val last = categoryFetchedAt[category.key] ?: 0L
        if (!force && now - last < CATEGORY_TTL_MS) return

        categoryJobs[category.key]?.cancel()
        categoryJobs[category.key] = viewModelScope.launch {
            updateRow(category.key) { it.copy(isLoading = true, error = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    HentaiMamaNetworkRepo.getCategoryVideos(category)
                }
            }.onSuccess { videos ->
                updateRow(category.key) {
                    it.copy(videos = videos, isLoading = false, error = null)
                }
                categoryFetchedAt[category.key] = System.currentTimeMillis()
            }.onFailure { e ->
                Log.e(TAG, "Category '${category.key}' failed: ${e.message}", e)
                updateRow(category.key) {
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

    fun getHomePage() {
        viewModelScope.launch {
            HentaiMamaNetworkRepo.getHomePage().collect { _homeState.value = it }
        }
    }

    fun searchVideos(page: Int, query: String) {
        viewModelScope.launch {
            HentaiMamaNetworkRepo.searchVideos(page, query).collect { _searchState.value = it }
        }
    }

    fun filterVideos(page: Int) {
        viewModelScope.launch {
            HentaiMamaNetworkRepo.filterVideos(
                page = page,
                genre = _selectedGenre.value,
                producer = _selectedProducer.value,
                year = _selectedYear.value,
                order = _selectedOrder.value,
            ).collect { _searchState.value = it }
        }
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

    fun getVideoDetail(url: String) {
        viewModelScope.launch {
            HentaiMamaNetworkRepo.getVideoDetail(url).collect { _videoState.value = it }
        }
    }

    suspend fun fetchDetailBody(url: String): String = withContext(Dispatchers.IO) {
        try {
            val full = if (url.startsWith("http")) url else HentaiMamaNetwork.normalizeUrl(url)
            val response = HentaiMamaNetwork.service.getVideoDetail(full)
            if (response.isSuccessful) response.body()?.string().orEmpty() else ""
        } catch (e: Exception) {
            Log.e(TAG, "fetchDetailBody error: ${e.message}")
            ""
        }
    }

    suspend fun fetchHosterTabs(url: String): List<Pair<String, Int>> =
        withContext(Dispatchers.IO) {
            try {
                val body = fetchDetailBody(url)
                if (body.isBlank()) emptyList()
                else HentaiMamaNetworkRepo.extractHosterTabs(body)
            } catch (e: Exception) {
                Log.e(TAG, "fetchHosterTabs error: ${e.message}")
                emptyList()
            }
        }

    override fun onCleared() {
        super.onCleared()
        categoryJobs.values.forEach { it.cancel() }
    }
}
