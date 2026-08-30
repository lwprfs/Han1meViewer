package com.yenaly.han1meviewer.MissAV

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.ui.component.VideoCardItem
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SORT_OPTIONS = listOf(
    null to "Default",
    "released_at" to "Release Date",
    "published_at" to "Recent Update",
    "today_views" to "Today Views",
    "weekly_views" to "Weekly Views",
    "monthly_views" to "Monthly Views",
    "views" to "Total Views",
)

private val FILTER_OPTIONS = listOf(
    null to "All",
    "individual" to "Single Actress",
    "multiple" to "Multiple Actress",
    "english-subtitle" to "English Subtitle",
    "jav" to "Japan AV",
    "asiaav" to "Asia AV",
    "uncensored" to "Uncensored",
    "uncensored-leak" to "Uncensored Leak",
)

private val GENRE_OPTIONS = listOf(
    "en/release" to "All",
    "en/english-subtitle" to "English Subtitle",
    "en/uncensored-leak" to "Uncensored Leak",
    "en/genres/Hd" to "Hd",
    "en/genres/Exclusive" to "Exclusive",
    "en/genres/Creampie" to "Creampie",
    "en/genres/Big%20Breasts" to "Big Breasts",
    "en/genres/Individual" to "Individual",
    "en/genres/Wife" to "Wife",
    "en/genres/Mature%20Woman" to "Mature Woman",
    "en/genres/Ordinary%20Person" to "Ordinary Person",
    "en/genres/Pretty%20Girl" to "Pretty Girl",
    "en/genres/Ride" to "Ride",
    "en/genres/Oral%20Sex" to "Oral Sex",
    "en/genres/Orgy" to "Orgy",
    "en/genres/Slim%20Pixelated" to "Slim Pixelated",
    "en/genres/4%20Hours%20Or%20More" to "4 Hours Or More",
    "en/genres/Slut" to "Slut",
    "en/genres/Collection" to "Collection",
    "en/genres/High%20School%20Girl" to "High School Girl",
    "en/genres/Squirting" to "Squirting",
    "en/genres/Fetish" to "Fetish",
    "en/genres/Selfie" to "Selfie",
    "en/genres/Tit%20Job" to "Tit Job",
    "en/genres/Planning" to "Planning",
    "en/genres/Incest" to "Incest",
    "en/genres/Hit%20On%20Girls" to "Hit On Girls",
    "en/genres/Sneak%20Shots" to "Sneak Shots",
    "en/genres/Slim" to "Slim",
    "en/genres/Bukkake" to "Bukkake",
    "en/genres/Beautiful%20Breasts" to "Beautiful Breasts",
    "en/genres/Masturbate" to "Masturbate",
    "en/genres/Masturbation" to "Masturbation",
    "en/genres/Restraint" to "Restraint",
    "en/genres/Promiscuous" to "Promiscuous",
    "en/genres/Lesbian" to "Lesbian",
    "en/genres/Ntr" to "Ntr",
    "en/genres/Sister" to "Sister",
    "en/genres/Plot" to "Plot",
    "en/genres/Cosplay" to "Cosplay",
    "en/genres/Humiliation" to "Humiliation",
    "en/genres/Documentary" to "Documentary",
    "en/genres/Hot%20Girl" to "Hot Girl",
    "en/genres/Ol" to "Ol",
    "en/genres/Uniform" to "Uniform",
    "en/genres/Fingering" to "Fingering",
    "en/genres/Vibrator" to "Vibrator",
    "en/genres/Adultery" to "Adultery",
    "en/genres/Cunnilingus" to "Cunnilingus",
    "en/genres/Delusion" to "Delusion",
    "en/genres/Female%20College%20Student" to "Female College Student",
    "en/genres/Sm" to "Sm",
    "en/genres/Shame" to "Shame",
    "en/genres/Anus" to "Anus",
    "en/genres/Petite" to "Petite",
    "en/genres/Shaving" to "Shaving",
    "en/genres/Subjective%20Perspective" to "Subjective Perspective",
    "en/genres/Prostitute" to "Prostitute",
    "en/genres/Various%20Occupations" to "Various Occupations",
    "en/genres/Mother" to "Mother",
    "en/genres/Toy" to "Toy",
    "en/genres/Promiscuity" to "Promiscuity",
    "en/genres/Outdoor%20Exposure" to "Outdoor Exposure",
    "en/genres/Butt%20Fetish" to "Butt Fetish",
    "en/genres/Pantyhose" to "Pantyhose",
    "en/genres/Debut" to "Debut",
    "en/genres/Urinate" to "Urinate",
    "en/genres/Dirty%20Talk" to "Dirty Talk",
    "en/genres/Massage" to "Massage",
    "en/genres/Underwear" to "Underwear",
    "en/genres/Big%20Ass" to "Big Ass",
    "en/genres/Forced%20Blowjob" to "Forced Blowjob",
    "en/genres/Sailor%20Suit" to "Sailor Suit",
    "en/genres/Swimsuit" to "Swimsuit",
    "en/genres/Delivery%20Only" to "Delivery Only",
    "en/genres/Female%20Teacher" to "Female Teacher",
    "en/genres/Kimono" to "Kimono",
    "en/genres/Swallow%20Sperm" to "Swallow Sperm",
    "en/genres/69" to "69",
    "en/genres/Small%20Breasts" to "Small Breasts",
    "en/genres/Elder%20Sister" to "Elder Sister",
    "en/genres/Young%20Wife" to "Young Wife",
    "en/genres/Nurse" to "Nurse",
    "en/genres/Massage%20Oil" to "Massage Oil",
    "en/genres/Group%20Bukkake" to "Group Bukkake",
    "en/genres/Tied%20Up" to "Tied Up",
    "en/genres/Fat%20Girl" to "Fat Girl",
    "en/genres/Rejuvenation%20Massage" to "Rejuvenation Massage",
    "en/genres/Short%20Skirt" to "Short Skirt",
    "en/genres/Ultra%20Slim%20Pixelated" to "Ultra Slim Pixelated",
    "en/genres/Contribution" to "Contribution",
    "en/genres/Nice%20Ass" to "Nice Ass",
    "en/genres/Foot%20Fetish" to "Foot Fetish",
    "en/genres/Full%20Hd%20%28Fhd%29" to "Full Hd (Fhd)",
    "en/genres/Glasses%20Girl" to "Glasses Girl",
    "en/genres/Kiss" to "Kiss",
    "en/genres/4K" to "4K",
    "en/genres/Close%20Up" to "Close Up",
    "en/genres/Big%20Breast%20Fetish" to "Big Breast Fetish",
    "en/genres/Sportswear" to "Sportswear",
    "en/genres/Virgin" to "Virgin",
    "en/genres/Vibrating%20Egg" to "Vibrating Egg",
    "en/genres/Aphrodisiac" to "Aphrodisiac",
    "en/genres/Lesbian%20Kiss" to "Lesbian Kiss",
    "en/genres/Mini%20Skirt" to "Mini Skirt",
    "en/genres/White%20Skin" to "White Skin",
    "en/genres/M%20Male" to "M Male",
    "en/genres/Couple" to "Couple",
    "en/genres/Hot%20Spring" to "Hot Spring",
    "en/genres/Maid" to "Maid",
    "en/genres/Face%20Ride" to "Face Ride",
    "en/genres/Imprisonment" to "Imprisonment",
    "en/genres/Footjob" to "Footjob",
    "en/genres/Fighting" to "Fighting",
    "en/genres/Tall%20Lady" to "Tall Lady",
    "en/genres/Female%20Warrior" to "Female Warrior",
    "en/genres/Artist" to "Artist",
    "en/genres/Science%20Fiction" to "Science Fiction",
    "en/genres/Mischief" to "Mischief",
    "en/genres/Actress%20Collection" to "Actress Collection",
    "en/genres/Married%20Woman" to "Married Woman",
    "en/genres/Sweating" to "Sweating",
    "en/genres/Black%20Male%20Actor" to "Black Male Actor",
    "en/genres/Stepmother" to "Stepmother",
    "en/genres/Beautiful%20Legs" to "Beautiful Legs",
    "en/genres/Private%20Teacher" to "Private Teacher",
    "en/genres/Big%20Pennis" to "Big Pennis",
    "en/genres/Super%20Breasts" to "Super Breasts",
    "en/genres/Advertising%20Idol" to "Advertising Idol",
    "en/genres/Torture" to "Torture",
    "en/genres/Emmanuel" to "Emmanuel",
    "en/genres/Anal%20Sex" to "Anal Sex",
    "en/genres/Black%20Hair" to "Black Hair",
    "en/genres/Erotic%20Photo" to "Erotic Photo",
    "en/genres/Widow" to "Widow",
    "en/genres/Gym%20Suit" to "Gym Suit",
    "en/genres/Cruel" to "Cruel",
    "en/genres/Sexy" to "Sexy",
    "en/genres/Car%20Sex" to "Car Sex",
    "en/genres/Multiple%20Stories" to "Multiple Stories",
    "en/genres/Campus%20Story" to "Campus Story",
    "en/genres/3P,%204P" to "3P, 4P",
    "en/genres/Transgender" to "Transgender",
    "en/genres/Female%20Doctor" to "Female Doctor",
    "en/genres/In%20Love" to "In Love",
    "en/genres/Fighter" to "Fighter",
    "en/genres/Fantasy" to "Fantasy",
    "en/genres/Pure" to "Pure",
    "en/genres/Instant%20Sex" to "Instant Sex",
    "en/genres/Missy" to "Missy",
    "en/genresenema" to "Enema",
    "en/genres/Dance" to "Dance",
    "en/genres/Feminine" to "Feminine",
    "en/genres/Best,%20Omnibus" to "Best, Omnibus",
    "en/genres/Whites" to "Whites",
    "en/genres/Flight%20Attendant" to "Flight Attendant",
    "en/genres/Harem" to "Harem",
    "en/genres/Foreign%20Actress" to "Foreign Actress",
    "en/genres/Physical%20Education" to "Physical Education",
    "en/genres/Bronze" to "Bronze",
    "en/genres/Female%20Investigator" to "Female Investigator",
    "en/genres/Transsexuals" to "Transsexuals",
    "en/genres/Model" to "Model",
    "en/genres/Baby%20Face" to "Baby Face",
    "en/genres/Doggy%20Style" to "Doggy Style",
    "en/genres/Bitch" to "Bitch",
    "en/genres/Bloomers" to "Bloomers",
    "en/genres/One%20Piece%20Dress" to "One Piece Dress",
    "en/genres/Knee%20Socks" to "Knee Socks",
    "en/genres/Thanks%20Offering" to "Thanks Offering",
    "en/genres/Cute%20Little%20Boy" to "Cute Little Boy",
    "en/genres/Delivery-Only%20Amateur" to "Delivery-Only Amateur",
    "en/genres/Other" to "Other",
    "en/genres/Bubble%20Bath" to "Bubble Bath",
    "en/genres/Tickle" to "Tickle",
    "en/genres/Extreme%20Orgasm" to "Extreme Orgasm",
    "en/genres/Breast%20Milk" to "Breast Milk",
    "en/genres/M%20Female" to "M Female",
    "en/genres/Pregnant%20Woman" to "Pregnant Woman",
    "en/genres/Indie" to "Indie",
    "en/genres/Homosexual" to "Homosexual",
    "en/genres/Vr" to "Vr",
    "en/genres/Drink%20Urine" to "Drink Urine",
    "en/genres/Racing%20Girl" to "Racing Girl",
    "en/genres/Femdom%20Slave" to "Femdom Slave",
    "en/genres/Heaven%20Tv" to "Heaven Tv",
    "en/genres/Secretary" to "Secretary",
    "en/genres/Insult" to "Insult",
    "en/genres/Rape" to "Rape",
    "en/genres/Thirty" to "Thirty",
    "en/genres/Lolita" to "Lolita",
    "en/genres/Female%20Boss" to "Female Boss",
    "en/genres/Foreign%20Object%20Penetration" to "Foreign Object Penetration",
    "en/genres/Hit%20On%20Boys" to "Hit On Boys",
    "en/genres/Stool" to "Stool",
    "en/genres/Hysteroscope" to "Hysteroscope",
    "en/genres/Defecation" to "Defecation",
    "en/genres/Gang%20Rape" to "Gang Rape",
    "en/genres/Anchorwoman" to "Anchorwoman",
    "en/genres/High%20Quality%20Vr" to "High Quality Vr",
    "en/genres/Similar" to "Similar",
    "en/genres/Catwoman" to "Catwoman",
    "en/genres/Bathtub" to "Bathtub",
    "en/genres/Dildo" to "Dildo",
    "en/genres/Limited%20Time" to "Limited Time",
    "en/genres/Fist" to "Fist",
    "en/genres/Dating" to "Dating",
    "en/genres/Cuckold" to "Cuckold",
    "en/genres/Original" to "Original",
    "en/genres/Lecturer" to "Lecturer",
    "en/genres/Esthetic%20Massage" to "Esthetic Massage",
    "en/genres/Childhood" to "Childhood",
    "en/genres/Uterus" to "Uterus",
    "en/genres/Pregnant" to "Pregnant",
    "en/genresentertainer" to "Entertainer",
    "en/genres/Long%20Hair" to "Long Hair",
    "en/genres/First%20Shot" to "First Shot",
    "en/genres/Muscle" to "Muscle",
    "en/genres/Outdoors" to "Outdoors",
    "en/genres/Naked%20Apron" to "Naked Apron",
    "en/genres/Male%20Squirting" to "Male Squirting",
    "en/genres/Hotel%20Owner" to "Hotel Owner",
    "en/genres/Molester" to "Molester",
    "en/genres/Bunny%20Girl" to "Bunny Girl",
    "en/genres/Travel" to "Travel",
    "en/genres/Asian%20Actress" to "Asian Actress",
    "en/genres/Tentacle" to "Tentacle",
    "en/genres/Proud%20Pussy" to "Proud Pussy",
    "en/genres/Subordinate%20Or%20Colleague" to "Subordinate Or Colleague",
    "en/genres/With%20Bonus%20Video%20Only%20For%20Mgs" to "With Bonus Video Only For Mgs",
    "en/genres/Business%20Clothing" to "Business Clothing",
    "en/genres/Premature%20Ejaculation" to "Premature Ejaculation",
    "en/genres/Friend" to "Friend",
    "en/genres/Shame%20And%20Humiliation" to "Shame And Humiliation",
    "en/genres/Short%20Hair" to "Short Hair",
    "en/genres/Waitress" to "Waitress",
    "en/genres/Clinic" to "Clinic",
    "en/genres/Exposure" to "Exposure",
    "en/genres/Kimono%20/%20Yukata" to "Kimono / Yukata",
    "en/genres/Lewd%20Nasty%20Lady" to "Lewd Nasty Lady",
    "en/genres/Bubble%20Socks" to "Bubble Socks",
    "en/genres/Idol" to "Idol",
    "en/genres/Time%20Stops" to "Time Stops"
)

data class ActiveFilters(
    val sort: String? = null,
    val filter: String? = null,
    val genre: String? = null,
) {
    val isNotEmpty: Boolean = sort != null || filter != null || genre != null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissAvSearchScreen(
    initialQuery: String?,
    onBack: () -> Unit,
    onNavigateToVideo: (String, String) -> Unit,
    viewModel: MissAvViewModel = viewModel(),
) {
    var searchQuery by remember { mutableStateOf(initialQuery ?: "") }
    var selectedSort by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf<String?>(null) }
    var selectedGenre by remember { mutableStateOf("en/release") }
    var currentPage by remember { mutableIntStateOf(1) }
    var allVideos by remember { mutableStateOf<List<HanimeInfo>>(emptyList()) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var initialLoadDone by remember { mutableStateOf(false) }
    var hasMorePages by remember { mutableStateOf(true) }
    var showFilters by remember { mutableStateOf(false) }
    
    val searchState by viewModel.searchFlow.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    val activeFilters = remember(selectedSort, selectedFilter, selectedGenre) {
        ActiveFilters(
            sort = selectedSort,
            filter = selectedFilter,
            genre = selectedGenre.takeIf { it != "en/release" }
        )
    }

    fun doSearch(resetPage: Boolean = true) {
        if (resetPage) {
            currentPage = 1
            allVideos = emptyList()
            hasMorePages = true
            coroutineScope.launch {
                gridState.scrollToItem(0)
            }
        }
        hasSearched = true
        isLoadingMore = true
        viewModel.searchVideosWithGenre(searchQuery, currentPage, selectedSort, selectedGenre, selectedFilter)
    }

    fun clearFilter(filterType: String) {
        when (filterType) {
            "sort" -> selectedSort = null
            "filter" -> selectedFilter = null
            "genre" -> selectedGenre = "en/release"
        }
        if (hasSearched) doSearch(resetPage = true)
    }

    fun clearAllFilters() {
        selectedSort = null
        selectedFilter = null
        selectedGenre = "en/release"
        if (hasSearched) doSearch(resetPage = true)
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery != null && initialQuery.isNotEmpty() && !initialLoadDone) {
            searchQuery = initialQuery
            initialLoadDone = true
            currentPage = 1
            allVideos = emptyList()
            hasSearched = false
            delay(100)
            doSearch(resetPage = true)
        }
    }

    LaunchedEffect(searchState) {
        when (val state = searchState) {
            is PageLoadingState.Success -> {
                val newVideos = state.info
                if (currentPage == 1) {
                    allVideos = newVideos
                } else {
                    // Prevent duplicates by checking existing videoCodes
                    val existingCodes = allVideos.map { it.videoCode }.toSet()
                    val uniqueNewVideos = newVideos.filter { it.videoCode !in existingCodes }
                    allVideos = allVideos + uniqueNewVideos
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
        }
    }

    // Simplified infinite scroll using a derived state
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf false
            
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            
            lastVisible >= totalItems - 4 &&
            totalItems > 4 &&
            hasMorePages &&
            !isLoadingMore &&
            hasSearched &&
            allVideos.isNotEmpty()
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            isLoadingMore = true
            val nextPage = currentPage + 1
            currentPage = nextPage
            viewModel.searchVideosWithGenre(
                searchQuery,
                nextPage,
                selectedSort,
                selectedGenre,
                selectedFilter
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search MissAV") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            // Search box with Filter and Search icons inside
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text("Search videos...") },
                trailingIcon = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Filter button inside search box
                        IconButton(
                            onClick = { showFilters = !showFilters },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "Filters",
                                tint = if (activeFilters.isNotEmpty)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Search button inside search box
                        IconButton(
                            onClick = { doSearch() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        }
                    }
                }
            )

            // Filter UI with AnimatedVisibility
            AnimatedVisibility(
                visible = showFilters,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    // Active filter chips
                    if (activeFilters.isNotEmpty) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            activeFilters.sort?.let { sortValue ->
                                val label = SORT_OPTIONS.find { it.first == sortValue }?.second ?: sortValue
                                FilterChip(
                                    selected = true,
                                    onClick = { clearFilter("sort") },
                                    label = { Text("Sort: $label") },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                            activeFilters.filter?.let { filterValue ->
                                val label = FILTER_OPTIONS.find { it.first == filterValue }?.second ?: filterValue
                                FilterChip(
                                    selected = true,
                                    onClick = { clearFilter("filter") },
                                    label = { Text("Filter: $label") },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                            activeFilters.genre?.let { genreValue ->
                                val label = GENRE_OPTIONS.find { it.first == genreValue }?.second ?: genreValue
                                FilterChip(
                                    selected = true,
                                    onClick = { clearFilter("genre") },
                                    label = { Text("Genre: $label") },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                            AssistChip(
                                onClick = { clearAllFilters() },
                                label = { Text("Clear all") },
                                modifier = Modifier,
                            )
                        }
                    }

                    // Filter section
                    Text(
                        "Filter",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FILTER_OPTIONS.forEach { (value, label) ->
                            FilterChip(
                                selected = selectedFilter == value,
                                onClick = {
                                    selectedFilter = if (selectedFilter == value) null else value
                                    if (hasSearched) doSearch(resetPage = true)
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Text(
                        "Genre",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GENRE_OPTIONS.forEach { (value, label) ->
                            FilterChip(
                                selected = selectedGenre == value,
                                onClick = {
                                    selectedGenre = if (selectedGenre == value) "en/release" else value
                                    if (hasSearched) doSearch(resetPage = true)
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Text(
                        "Sort by",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SORT_OPTIONS.forEach { (value, label) ->
                            FilterChip(
                                selected = selectedSort == value,
                                onClick = {
                                    selectedSort = if (selectedSort == value) null else value
                                    if (hasSearched) doSearch(resetPage = true)
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            when (val state = searchState) {
                is PageLoadingState.Loading -> {
                    if (allVideos.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        DisplayResults(
                            allVideos = allVideos,
                            isLoadingMore = isLoadingMore,
                            gridState = gridState,
                            onNavigateToVideo = onNavigateToVideo
                        )
                    }
                }
                is PageLoadingState.Success, is PageLoadingState.NoMoreData -> {
                    if (allVideos.isEmpty() && hasSearched) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No results found", style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        DisplayResults(
                            allVideos = allVideos,
                            isLoadingMore = isLoadingMore,
                            gridState = gridState,
                            onNavigateToVideo = onNavigateToVideo
                        )
                    }
                }
                is PageLoadingState.Error -> {
                    if (allVideos.isEmpty()) {
                        ErrorContent(
                            message = state.throwable.message ?: "Failed to load results",
                            onRetry = { doSearch(resetPage = true) }
                        )
                    } else {
                        Column {
                            DisplayResults(
                                allVideos = allVideos,
                                isLoadingMore = isLoadingMore,
                                gridState = gridState,
                                onNavigateToVideo = onNavigateToVideo
                            )
                            Text(
                                text = "Failed to load more results: ${state.throwable.message}",
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
    onNavigateToVideo: (String, String) -> Unit
) {
    // Deduplicate videos by videoCode to prevent duplicate keys
    val uniqueVideos = allVideos.distinctBy { it.videoCode }
    
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        state = gridState,
        modifier = Modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Use items() with count and key for better performance
        items(
            count = uniqueVideos.size,
            key = { index -> uniqueVideos[index].videoCode }
        ) { index ->
            val video = uniqueVideos[index]
            VideoCardItem(
                videoItem = video,
                onClickVideosItem = { onNavigateToVideo(video.videoCode, "/en/${video.videoCode}") },
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