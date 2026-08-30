package com.yenaly.han1meviewer.HentaiMama

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.ui.component.VideoCardItem
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HentaiMamaSearchScreen(
    initialQuery: String?,
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    viewModel: HentaiMamaViewModel = viewModel(),
) {
    var searchQuery by remember { mutableStateOf(initialQuery ?: "") }
    var currentPage by remember { mutableIntStateOf(1) }
    var allVideos by remember { mutableStateOf<List<HanimeInfo>>(emptyList()) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var hasMorePages by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }

    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val selectedProducer by viewModel.selectedProducer.collectAsStateWithLifecycle()
    val selectedOrder by viewModel.selectedOrder.collectAsStateWithLifecycle()

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    fun doSearch(resetPage: Boolean = true) {
        if (resetPage) {
            currentPage = 1
            allVideos = emptyList()
            hasMorePages = true
            isLoading = true
            coroutineScope.launch { gridState.scrollToItem(0) }
        }
        hasSearched = true
        isLoadingMore = true
        
        // If query is empty, use filter mode; otherwise use search
        if (searchQuery.isNotBlank()) {
            viewModel.searchVideos(currentPage, searchQuery)
        } else {
            viewModel.filterVideos(currentPage)
        }
    }

    fun applyFilters() {
        currentPage = 1
        allVideos = emptyList()
        hasMorePages = true
        hasSearched = true
        isLoadingMore = true
        isLoading = true
        coroutineScope.launch { gridState.scrollToItem(0) }
        viewModel.filterVideos(currentPage)
    }

    // Initial search from navigation
    LaunchedEffect(initialQuery) {
        if (initialQuery != null && initialQuery.isNotEmpty() && !hasSearched) {
            searchQuery = initialQuery
            doSearch()
        }
    }

    // Handle search results
    LaunchedEffect(searchState) {
        isLoading = false
        when (val state = searchState) {
            is PageLoadingState.Success -> {
                val newVideos = state.info
                if (currentPage == 1) {
                    allVideos = newVideos
                } else {
                    allVideos = allVideos + newVideos
                }
                isLoadingMore = false
                if (newVideos.isEmpty()) {
                    hasMorePages = false
                }
            }
            is PageLoadingState.Error -> {
                isLoadingMore = false
            }
            is PageLoadingState.NoMoreData -> {
                isLoadingMore = false
                hasMorePages = false
            }
            is PageLoadingState.Loading -> {}
            else -> {}
        }
    }

    // Load more when scrolling
    LaunchedEffect(gridState) {
        coroutineScope.launch {
            while (true) {
                delay(200)
                if (isLoadingMore || !hasMorePages || !hasSearched || allVideos.isEmpty()) continue
                
                val layoutInfo = gridState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                if (totalItems == 0) continue
                
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: continue
                if (lastVisible >= totalItems - 4 && totalItems > 4) {
                    isLoadingMore = true
                    currentPage++
                    if (searchQuery.isNotBlank()) {
                        viewModel.searchVideos(currentPage, searchQuery)
                    } else {
                        viewModel.filterVideos(currentPage)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search HentaiMama") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Filter toggle button - always visible
                    IconButton(
                        onClick = { showFilters = !showFilters }
                    ) {
                        Icon(
                            Icons.Default.FilterList, 
                            contentDescription = "Filters",
                            tint = if (showFilters || selectedGenre != null || selectedProducer != null || selectedOrder != null) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            // Search input with Search icon - fixed
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text("Search videos...") },
                trailingIcon = {
                    Row {
                        // Clear button
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { 
                                searchQuery = "" 
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                        // Search icon button - always visible
                        IconButton(onClick = { doSearch() }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doSearch() })
            )

            // Filter Section
            if (showFilters) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Order Filter
                        Column {
                            Text("Order By", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = selectedOrder == null,
                                    onClick = { 
                                        viewModel.setOrder(null)
                                        applyFilters()
                                    },
                                    label = { Text("Default") }
                                )
                                getOrders().forEach { order ->
                                    FilterChip(
                                        selected = selectedOrder == order.id,
                                        onClick = { 
                                            viewModel.setOrder(if (selectedOrder == order.id) null else order.id)
                                            applyFilters()
                                        },
                                        label = { Text(order.name) }
                                    )
                                }
                            }
                        }

                        HorizontalDivider()

                        // Genre Filter
                        Column {
                            Text("Genre", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FilterChip(
                                    selected = selectedGenre == null,
                                    onClick = { 
                                        viewModel.setGenre(null)
                                        applyFilters()
                                    },
                                    label = { Text("All") }
                                )
                                getGenres().forEach { genre ->
                                    FilterChip(
                                        selected = selectedGenre == genre,
                                        onClick = { 
                                            viewModel.setGenre(if (selectedGenre == genre) null else genre)
                                            applyFilters()
                                        },
                                        label = { Text(genre) }
                                    )
                                }
                            }
                        }

                        HorizontalDivider()

                        // Producer Filter
                        Column {
                            Text("Producer", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FilterChip(
                                    selected = selectedProducer == null,
                                    onClick = { 
                                        viewModel.setProducer(null)
                                        applyFilters()
                                    },
                                    label = { Text("All") }
                                )
                                getProducers().forEach { producer ->
                                    FilterChip(
                                        selected = selectedProducer == producer,
                                        onClick = { 
                                            viewModel.setProducer(if (selectedProducer == producer) null else producer)
                                            applyFilters()
                                        },
                                        label = { Text(producer.take(15)) }
                                    )
                                }
                            }
                        }

                        // Clear all filters button
                        if (selectedGenre != null || selectedProducer != null || selectedOrder != null) {
                            TextButton(
                                onClick = {
                                    viewModel.clearFilters()
                                    applyFilters()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Clear All Filters")
                            }
                        }
                    }
                }
            } else if (selectedGenre != null || selectedProducer != null || selectedOrder != null) {
                // Show active filters as chips even when filters are collapsed
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedOrder != null) {
                        val orderName = getOrders().find { it.id == selectedOrder }?.name ?: selectedOrder
                        InputChip(
                            selected = true,
                            onClick = { 
                                viewModel.setOrder(null)
                                applyFilters()
                            },
                            label = { Text("Order: $orderName") },
                            trailingIcon = { 
                                Icon(Icons.Default.Clear, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                    if (selectedGenre != null) {
                        InputChip(
                            selected = true,
                            onClick = { 
                                viewModel.setGenre(null)
                                applyFilters()
                            },
                            label = { Text("Genre: $selectedGenre") },
                            trailingIcon = { 
                                Icon(Icons.Default.Clear, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                    if (selectedProducer != null) {
                        InputChip(
                            selected = true,
                            onClick = { 
                                viewModel.setProducer(null)
                                applyFilters()
                            },
                            label = { Text("Producer: ${selectedProducer?.take(15)}") },
                            trailingIcon = { 
                                Icon(Icons.Default.Clear, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Results
            when (val state = searchState) {
                is PageLoadingState.Loading -> {
                    if (allVideos.isEmpty()) LoadingContent()
                    else DisplayResults(allVideos, isLoadingMore, gridState, onNavigateToVideo)
                }
                is PageLoadingState.Success, is PageLoadingState.NoMoreData -> {
                    if (allVideos.isEmpty() && hasSearched) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No results found", style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        DisplayResults(allVideos, isLoadingMore, gridState, onNavigateToVideo)
                    }
                }
                is PageLoadingState.Error -> {
                    if (allVideos.isEmpty()) {
                        ErrorContent(
                            message = state.throwable.message ?: "Failed to load results",
                            onRetry = { doSearch() }
                        )
                    } else {
                        Column {
                            DisplayResults(allVideos, isLoadingMore, gridState, onNavigateToVideo)
                            Text(
                                text = "Failed to load more: ${state.throwable.message}",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayResults(
    allVideos: List<HanimeInfo>,
    isLoadingMore: Boolean,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onNavigateToVideo: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        state = gridState,
        modifier = Modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(allVideos, key = { it.videoCode }) { video ->
            VideoCardItem(
                videoItem = video,
                onClickVideosItem = { onNavigateToVideo(video.videoCode) },
                onLongClickVideosItem = { _, _ -> }
            )
        }
        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

// Keep the existing filter data functions
private fun getGenres() = listOf(
    "3D", "Action", "Adventure", "Ahegao", "Anal", "Animal Ears", "Beastiality",
    "Blackmail", "Blowjob", "Bondage", "Brainwashed", "Bukakke", "Cat Girl",
    "Comedy", "Cosplay", "Creampie", "Cross-dressing", "Dark Skin", "DeepThroat",
    "Demons", "Doctor", "Double Penatration", "Drama", "Dubbed", "Ecchi",
    "Elf", "Eroge", "Facesitting", "Facial", "Fantasy", "Female Doctor",
    "Female Teacher", "Femdom", "Footjob", "Futanari", "Gangbang", "Gore",
    "Gyaru", "Harem", "Historical", "Horny Slut", "Housewife", "Humiliation",
    "Incest", "Inflation", "Internal Cumshot", "Lactation", "Large Breasts",
    "Lolicon", "Magical Girls", "Maid", "Martial Arts", "Megane", "MILF",
    "Mind Break", "Molestation", "Non-Japanese", "NTR", "Nuns", "Nurses",
    "Office Ladies", "Police", "POV", "Pregnant", "Princess", "Public Sex",
    "Rape", "Rim job", "Romance", "Scat", "School Girls", "Sci-Fi",
    "Shimapan", "Short", "Shoutacon", "Slaves", "Sports", "Squirting",
    "Stocking", "Strap-on", "Strapped On", "Succubus", "Super Power",
    "Supernatural", "Swimsuit", "Tentacles", "Three some", "Tits Fuck",
    "Torture", "Toys", "Train Molestation", "Tsundere", "Uncensored",
    "Urination", "Vampire", "Vanilla", "Virgins", "Widow", "X-Ray", "Yuri"
)

private fun getProducers() = listOf(
    "8bit", "Actas", "Active", "AIC", "AIC A.S.T.A.", "Alice Soft",
    "An DerCen", "Angelfish", "Animac", "AniMan", "Animax", "Antechinus",
    "APPP", "Armor", "Arms", "Asahi Production", "AT-2", "Blue Eyes",
    "BOMB! CUTE! BOMB!", "BOOTLEG", "Bunnywalker", "Central Park Media",
    "CherryLips", "ChiChinoya", "Chippai", "ChuChu", "Circle Tribute",
    "CLOCKUP", "Collaboration Works", "Comic Media", "Cosmic Ray", "Cosmo",
    "Cotton Doll", "Cranberry", "D3", "Daiei", "Digital Works", "Discovery",
    "Dream Force", "Dubbed", "Easy Film", "Echo", "EDGE",
    "Filmlink International", "Five Ways", "Front Line", "Frontier Works",
    "Godoy", "Gold Bear", "Green Bunny", "Himajin Planning", "Hokiboshi",
    "Hoods Entertainment", "Horipro", "Hot Bear", "HydraFXX",
    "Innocent Grey", "Jam", "JapanAnime", "King Bee", "Kitty Films",
    "Kitty Media", "Knack Productions", "KSS", "Lemon Heart",
    "Lune Pictures", "Majin", "Marvelous Entertainment", "Mary Jane",
    "Media", "Media Blasters", "Milkshake", "Mitsu", "Moonstone Cherry",
    "Mousou Senka", "MS Pictures", "Nihikime no Dozeu", "Nur",
    "NuTech Digital", "Obtain Future", "Office Take Off", "OLE-M",
    "Oriental Light and Magic", "Oz", "Pashmina", "Pink Pineapple",
    "Pixy", "PoRO", "Production I.G", "Queen Bee",
    "Sakura Purin Animation", "Schoolzone", "Selfish", "Seven", "Shelf",
    "Shinkuukan", "Shinyusha", "Shouten", "Silky's", "Soft Garage",
    "SoftCel Pictures", "SPEED", "Studio 9 Maiami", "Studio Eromatick",
    "Studio Fantasia", "Studio Jack", "Studio Kyuuma", "Studio Matrix",
    "Studio Sign", "Studio Tulip", "Studio Unicorn", "Suzuki Mirano",
    "T-Rex", "The Right Stuf International", "Toho Company",
    "Top-Marschal", "Toranoana", "Toshiba Entertainment",
    "Triangle Bitter", "Triple X", "Union Cho", "Valkyria", "White Bear",
    "Y.O.U.C", "ZIZ Entertainment", "Zyc"
)

private data class Order(val name: String, val id: String)
private fun getOrders() = listOf(
    Order("Weekly Views", "weekly"),
    Order("Monthly Views", "monthly"),
    Order("Alltime Views", "alltime"),
    Order("A-Z", "alphabet"),
    Order("Rating", "rating"),
)
