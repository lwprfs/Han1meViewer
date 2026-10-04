package com.yenaly.han1meviewer.HentaiMama.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.HentaiMama.data.local.HentaiMamaSeriesRepo
import com.yenaly.han1meviewer.HentaiMama.data.model.GenreHeader
import com.yenaly.han1meviewer.HentaiMama.data.model.GenreLayout
import com.yenaly.han1meviewer.HentaiMama.data.model.GenrePage
import com.yenaly.han1meviewer.HentaiMama.data.model.GenrePaginator
import com.yenaly.han1meviewer.HentaiMama.data.model.GenreSeries
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
    val series: List<GenreSeries> = emptyList(),
    val genreHeader: GenreHeader? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isGenre: Boolean = false,
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

    private val _searchSeriesResults = MutableStateFlow<List<GenreSeries>>(emptyList())
    val searchSeriesResults = _searchSeriesResults.asStateFlow()

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

    private val _genreSlug = MutableStateFlow<String?>(null)
    val genreSlug = _genreSlug.asStateFlow()

    private val _genreHeader = MutableStateFlow<GenreHeader?>(null)
    val genreHeader = _genreHeader.asStateFlow()

    private val _genreSort = MutableStateFlow<String?>(null)
    val genreSort = _genreSort.asStateFlow()

    private val _genreLayout = MutableStateFlow(GenreLayout.DETAILS)
    val genreLayout = _genreLayout.asStateFlow()

    private val _genrePaginator = MutableStateFlow<GenrePaginator?>(null)
    val genrePaginator = _genrePaginator.asStateFlow()

    private val _searchNextUrl = MutableStateFlow<String?>(null)
    val searchNextUrl = _searchNextUrl.asStateFlow()

    private val _searchTotalPages = MutableStateFlow(1)
    val searchTotalPages = _searchTotalPages.asStateFlow()

    private val _searchCurrentPage = MutableStateFlow(1)
    val searchCurrentPage = _searchCurrentPage.asStateFlow()

    private val _seriesState =
        MutableStateFlow<VideoLoadingState<SeriesDetailPage>>(VideoLoadingState.Loading)
    val seriesState = _seriesState.asStateFlow()

    private val _genrePageState =
        MutableStateFlow<VideoLoadingState<GenrePage>>(VideoLoadingState.Loading)
    val genrePageState = _genrePageState.asStateFlow()

    private var searchJob: Job? = null
    private var seriesJob: Job? = null
    private var genreJob: Job? = null

    init {
        HentaiMamaSeriesRepo.init(application)
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
            val isGenre = HentaiMamaNetworkRepo.isGenrePath(cat.genrePath)
            existing[cat.key]?.copy(category = cat, isGenre = isGenre)
                ?: HentaiMamaCategoryRowState(category = cat, isGenre = isGenre)
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

        val isGenre = HentaiMamaNetworkRepo.isGenrePath(category.genrePath)

        categoryJobs[key] = viewModelScope.launch {
            updateRow(key) { it.copy(isLoading = true, error = null, isGenre = isGenre) }
            try {
                if (isGenre) {
                    val slug = HentaiMamaNetworkRepo.extractGenreSlug(category.genrePath)
                    val page: GenrePage? = withContext(Dispatchers.IO) {
                        HentaiMamaNetworkRepo.fetchGenrePage(
                            slug = slug,
                            sort = category.sort?.takeIf { it.isNotBlank() },
                            page = 1,
                            layout = GenreLayout.DETAILS,
                        )
                    }
                    if (page == null) {
                        updateRow(key) {
                            it.copy(
                                isLoading = false,
                                error = "Failed to load genre",
                                isGenre = true,
                            )
                        }
                        return@launch
                    }
                    withContext(Dispatchers.IO) {
                        runCatching {
                            HentaiMamaSeriesRepo.upsertAll(page.series.toSeriesCards(), "genre:$slug")
                        }
                    }
                    updateRow(key) {
                        it.copy(
                            series = page.series,
                            videos = page.series.map { s ->
                                HanimeInfo(
                                    title = s.title.ifBlank { s.altTitle.orEmpty() },
                                    coverUrl = s.posterFull.ifBlank { s.posterMid.orEmpty() },
                                    videoCode = s.slug,
                                    duration = s.episodeCount?.let { count -> "$count eps" },
                                    views = s.viewsRaw.takeIf { raw -> raw.isNotBlank() },
                                    uploadTime = s.year?.toString(),
                                    reviews = s.rating?.let { r -> "%.1f".format(r) },
                                    currentArtist = s.studios.firstOrNull(),
                                    itemType = HanimeInfo.NORMAL,
                                )
                            },
                            genreHeader = page.header,
                            isLoading = false,
                            error = null,
                            isGenre = true,
                        )
                    }
                } else {
                    val videos: List<HanimeInfo> = withContext(Dispatchers.IO) {
                        HentaiMamaNetworkRepo.getCategoryVideos(category)
                    }
                    updateRow(key) {
                        it.copy(
                            videos = videos,
                            series = emptyList(),
                            genreHeader = null,
                            isLoading = false,
                            error = null,
                            isGenre = false,
                        )
                    }
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

    private fun List<GenreSeries>.toSeriesCards() =
        map { s ->
            com.yenaly.han1meviewer.HentaiMama.data.model.SeriesCard(
                url = s.url,
                slug = s.slug,
                title = s.title,
                altTitles = listOfNotNull(s.altTitle),
                thumbSmall = s.posterSmall,
                thumbFull = s.posterFull,
                posterAlt = s.altText,
                rating = s.rating,
                favorites = s.favorites,
                postId = s.favoritePostId,
                nonce = s.favoriteNonce,
                studios = s.studios,
                studioUrls = s.studioUrls,
                year = s.year,
                viewsRaw = s.viewsRaw.takeIf { it.isNotBlank() },
                views = s.views,
                episodeCount = s.episodeCount,
                description = s.synopsis,
                hasLongDescription = false,
                genres = s.genres,
                genreSlugs = s.genreSlugs,
            )
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
        _searchSeriesResults.value = emptyList()
        _searchState.value = PageLoadingState.Loading
        _searchNextUrl.value = null
        _searchTotalPages.value = 1
        _searchCurrentPage.value = 1
    }

    fun clearGenreMode() {
        _genreSlug.value = null
        _genreHeader.value = null
        _genreSort.value = null
        _genreLayout.value = GenreLayout.DETAILS
        _genrePaginator.value = null
        _searchSeriesResults.value = emptyList()
    }

    fun resetAll() {
        clearSearch()
        clearFilters()
        clearGenreMode()
        seriesJob?.cancel()
        _seriesState.value = VideoLoadingState.Loading
        genreJob?.cancel()
        _genrePageState.value = VideoLoadingState.Loading
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

        val genre = _genreSlug.value
        if (!genre.isNullOrBlank()) {
            searchGenreVideos(genre, query, page)
            return
        }

        if (page <= 1) {
            _searchPage.value = 1
            _searchResults.value = emptyList()
            _searchSeriesResults.value = emptyList()
            _searchState.value = PageLoadingState.Loading
            _searchIsLoadingMore.value = false
            _searchNextUrl.value = null
            _searchHasMore.value = true
        } else {
            _searchIsLoadingMore.value = true
        }

        searchJob = viewModelScope.launch {
            HentaiMamaNetworkRepo.searchVideos(page, query, _selectedOrder.value)
                .collect { state ->
                    if (!isActive) return@collect
                    handleSearchState(state, page)
                }
        }
    }

    private fun searchGenreVideos(slug: String, query: String, page: Int) {
        if (page <= 1) {
            _searchPage.value = 1
            _searchResults.value = emptyList()
            _searchSeriesResults.value = emptyList()
            _searchState.value = PageLoadingState.Loading
            _searchIsLoadingMore.value = false
            _searchNextUrl.value = null
            _searchHasMore.value = true
        } else {
            _searchIsLoadingMore.value = true
        }

        searchJob = viewModelScope.launch {
            HentaiMamaNetworkRepo.searchGenreVideos(
                slug = slug,
                query = query,
                page = page,
                sort = _genreSort.value,
            ).collect { state ->
                if (!isActive) return@collect
                handleSearchState(state, page)
            }
        }
    }

    private fun handleSearchState(state: PageLoadingState<List<HanimeInfo>>, page: Int) {
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
                _searchCurrentPage.value = page
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

    fun filterVideos(page: Int) {
        searchJob?.cancel()
        _searchHasSearched.value = true
        _searchIsFilterMode.value = true
        _searchHasMore.value = true

        val genre = _genreSlug.value

        if (page <= 1) {
            _searchPage.value = 1
            _searchResults.value = emptyList()
            _searchSeriesResults.value = emptyList()
            _searchState.value = PageLoadingState.Loading
            _searchIsLoadingMore.value = false
            _searchNextUrl.value = null
        } else {
            _searchIsLoadingMore.value = true
        }

        if (!genre.isNullOrBlank()) {
            searchJob = viewModelScope.launch {
                loadGenrePage(genre, page)
            }
            return
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
                handleSearchState(state, page)
            }
        }
    }

    private suspend fun loadGenrePage(slug: String, page: Int) {
        try {
            val genrePage: GenrePage? = withContext(Dispatchers.IO) {
                HentaiMamaNetworkRepo.fetchGenrePage(
                    slug = slug,
                    sort = _genreSort.value,
                    page = page,
                    layout = _genreLayout.value,
                )
            }
            if (genrePage == null) {
                _searchState.value = PageLoadingState.Error(
                    IllegalStateException("Failed to load genre page")
                )
                _searchIsLoadingMore.value = false
                return
            }
            _genreHeader.value = genrePage.header
            _genrePaginator.value = genrePage.paginator
            _searchTotalPages.value = genrePage.paginator?.total ?: 1
            _searchCurrentPage.value = genrePage.paginator?.current ?: page

            withContext(Dispatchers.IO) {
                runCatching {
                    HentaiMamaSeriesRepo.upsertAll(
                        genrePage.series.toSeriesCards(),
                        "genre:$slug",
                    )
                }
            }

            val videos = genrePage.series.map { s ->
                HanimeInfo(
                    title = s.title.ifBlank { s.altTitle.orEmpty() },
                    coverUrl = s.posterFull.ifBlank { s.posterMid.orEmpty() },
                    videoCode = s.slug,
                    duration = s.episodeCount?.let { count -> "$count eps" },
                    views = s.viewsRaw.takeIf { raw -> raw.isNotBlank() },
                    uploadTime = s.year?.toString(),
                    reviews = s.rating?.let { r -> "%.1f".format(r) },
                    currentArtist = s.studios.firstOrNull(),
                    itemType = HanimeInfo.NORMAL,
                )
            }

            _searchSeriesResults.update { prev ->
                if (page <= 1) genrePage.series
                else (prev + genrePage.series).distinctBy { it.slug }
            }
            _searchResults.update { prev ->
                if (page <= 1) videos
                else (prev + videos).distinctBy(HanimeInfo::videoCode)
            }

            val hasMore = genrePage.paginator?.let {
                it.current < it.total || it.nextUrl?.isNotBlank() == true
            } ?: false

            _searchNextUrl.value = genrePage.paginator?.nextUrl
            _searchHasMore.value = hasMore
            _searchIsLoadingMore.value = false
            _searchState.value = if (videos.isEmpty()) {
                PageLoadingState.NoMoreData
            } else {
                PageLoadingState.Success(videos)
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadGenrePage failed", e)
            _searchState.value = PageLoadingState.Error(e)
            _searchIsLoadingMore.value = false
        }
    }

    fun setGenreSlug(slug: String?) {
        _genreSlug.value = slug?.takeIf { it.isNotBlank() }
    }

    fun setGenreSort(sort: String?) {
        _genreSort.value = sort?.takeIf { it.isNotBlank() }
        val slug = _genreSlug.value ?: return
        _searchPage.value = 1
        _searchResults.value = emptyList()
        _searchSeriesResults.value = emptyList()
        _searchNextUrl.value = null
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _searchState.value = PageLoadingState.Loading
            _searchIsLoadingMore.value = false
            loadGenrePage(slug, 1)
        }
    }

    fun setGenreLayout(layout: GenreLayout) {
        _genreLayout.value = layout
        val slug = _genreSlug.value ?: return
        _searchPage.value = 1
        _searchResults.value = emptyList()
        _searchSeriesResults.value = emptyList()
        _searchNextUrl.value = null
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _searchState.value = PageLoadingState.Loading
            _searchIsLoadingMore.value = false
            loadGenrePage(slug, 1)
        }
    }

    fun loadNextSearchPage() {
        if (_searchIsLoadingMore.value || !_searchHasMore.value) return
        val next = _searchPage.value + 1

        val genre = _genreSlug.value
        if (!genre.isNullOrBlank()) {
            searchJob?.cancel()
            _searchIsLoadingMore.value = true
            searchJob = viewModelScope.launch {
                loadGenrePage(genre, next)
            }
            return
        }

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

    fun getGenrePage(slug: String, sort: String? = null) {
        genreJob?.cancel()
        _genreSlug.value = slug
        _genreSort.value = sort
        genreJob = viewModelScope.launch {
            _genrePageState.value = VideoLoadingState.Loading
            HentaiMamaNetworkRepo.getGenrePageFlow(
                slug = slug,
                sort = sort,
                page = 1,
                layout = _genreLayout.value,
            ).collect { state ->
                if (!isActive) return@collect
                _genrePageState.value = state
                if (state is VideoLoadingState.Success) {
                    _genreHeader.value = state.info.header
                    _genrePaginator.value = state.info.paginator
                    withContext(Dispatchers.IO) {
                        runCatching {
                            HentaiMamaSeriesRepo.upsertAll(
                                state.info.series.toSeriesCards(),
                                "genre:$slug",
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        categoryJobs.values.forEach { it.cancel() }
        searchJob?.cancel()
        seriesJob?.cancel()
        genreJob?.cancel()
    }
}
