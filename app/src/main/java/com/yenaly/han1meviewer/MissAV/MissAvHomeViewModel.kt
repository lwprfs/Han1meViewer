package com.yenaly.han1meviewer.MissAV

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HomeCategoryRowState(
    val category: MissAvHomeCategory,
    val videos: List<HanimeInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class MissAvHomeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MissAvHomeViewModel"
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
    }

    private val _homePageFlow = MutableStateFlow<WebsiteState<MissAvHomePage>>(WebsiteState.Loading)
    val homePageFlow = _homePageFlow.asStateFlow()

    private val _categoryRows = MutableStateFlow<List<HomeCategoryRowState>>(emptyList())
    val categoryRows = _categoryRows.asStateFlow()

    private val _allCategories = MutableStateFlow<List<MissAvHomeCategory>>(emptyList())
    val allCategories = _allCategories.asStateFlow()

    val hasUserOverride: Boolean get() = MissAvHomeCategoryRepo.hasOverride()

    private var lastHomeFetchAt = 0L
    private val lastCategoryFetchAt = mutableMapOf<String, Long>()

    private val categoryJobs = mutableMapOf<String, Job>()
    private var homeJob: Job? = null
    private var categoriesLoaded = false

    init {
        viewModelScope.launch {
            loadCategories()
            syncCategoryRows()
        }
    }

    private suspend fun loadCategories() {
        _allCategories.value = MissAvHomeCategoryRepo.load(getApplication())
        categoriesLoaded = true
    }

    private fun syncCategoryRows() {
        val visible = _allCategories.value.filterNot { it.hidden }
        val existing = _categoryRows.value.associateBy { it.category.key }

        val rebuilt = visible.map { category ->
            existing[category.key]?.copy(category = category)
                ?: HomeCategoryRowState(category = category)
        }
        _categoryRows.value = rebuilt

        if (categoriesLoaded) {
            rebuilt.filter { it.videos.isEmpty() && !it.isLoading }
                .forEach { fetchCategory(it.category, force = false) }
        }
    }

    fun addCategory(title: String, genrePath: String, sort: String?) {
        val trimmedTitle = title.trim()
        val trimmedPath = genrePath.trim().removePrefix("/")
        if (trimmedTitle.isBlank() || trimmedPath.isBlank()) return
        val key = generateKey(trimmedTitle, _allCategories.value.map { it.key }.toSet())
        val newCategory = MissAvHomeCategory(
            key = key,
            title = trimmedTitle,
            genrePath = trimmedPath,
            sort = sort?.trim()?.takeIf { it.isNotEmpty() },
            hidden = false,
        )
        updateCategories(_allCategories.value + newCategory)
    }

    fun updateCategory(updated: MissAvHomeCategory) {
        updateCategories(
            _allCategories.value.map { if (it.key == updated.key) updated else it }
        )

        val row = _categoryRows.value.find { it.category.key == updated.key }
        if (row != null) fetchCategory(updated, force = true)
    }

    fun deleteCategory(key: String) {
        categoryJobs.remove(key)?.cancel()
        lastCategoryFetchAt.remove(key)
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
            MissAvHomeCategoryRepo.resetToDefaults()
            categoryJobs.values.forEach { it.cancel() }
            categoryJobs.clear()
            lastCategoryFetchAt.clear()
            _categoryRows.value = emptyList()
            loadCategories()
            syncCategoryRows()
        }
    }

    private fun updateCategories(newList: List<MissAvHomeCategory>) {
        _allCategories.value = newList
        viewModelScope.launch { MissAvHomeCategoryRepo.save(newList) }
        syncCategoryRows()
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

    fun getHomePage(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastHomeFetchAt < CACHE_TTL_MS &&
            _homePageFlow.value is WebsiteState.Success
        ) return

        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            runCatching {
                _homePageFlow.value = WebsiteState.Loading
                MissAvNetworkRepo.getHomePage().collect { state ->
                    if (!isActive) return@collect
                    _homePageFlow.value = state
                    if (state is WebsiteState.Success) {
                        lastHomeFetchAt = System.currentTimeMillis()
                    }
                }
            }.onFailure { e ->
                Log.e(TAG, "Home page exception", e)
                _homePageFlow.value = WebsiteState.Error(e)
            }
        }
    }

    fun refreshAllCategories() {
        lastCategoryFetchAt.clear()
        _categoryRows.value.forEach { row -> fetchCategory(row.category, force = true) }
    }

    fun retryCategory(category: MissAvHomeCategory) {
        fetchCategory(category, force = true)
    }

    private fun fetchCategory(category: MissAvHomeCategory, force: Boolean) {
        val now = System.currentTimeMillis()
        val last = lastCategoryFetchAt[category.key] ?: 0L
        if (!force && now - last < CACHE_TTL_MS) return

        categoryJobs[category.key]?.cancel()
        categoryJobs[category.key] = viewModelScope.launch {
            updateRow(category.key) { it.copy(isLoading = true, error = null) }
            runCatching {
                MissAvNetworkRepo.getGenreVideosSync(
                    genrePath = category.genrePath,
                    page = 1,
                    sort = category.sort,
                    filter = null,
                )
            }.onSuccess { videos ->
                updateRow(category.key) {
                    it.copy(videos = videos, isLoading = false, error = null)
                }
                lastCategoryFetchAt[category.key] = System.currentTimeMillis()
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
        transform: (HomeCategoryRowState) -> HomeCategoryRowState,
    ) {
        _categoryRows.update { rows ->
            rows.map { row ->
                if (row.category.key == key) transform(row) else row
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        categoryJobs.values.forEach { it.cancel() }
        homeJob?.cancel()
    }
}
