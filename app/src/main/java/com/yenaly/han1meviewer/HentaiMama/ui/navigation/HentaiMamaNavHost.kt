package com.yenaly.han1meviewer.HentaiMama.ui.navigation

import com.yenaly.han1meviewer.R
import androidx.compose.ui.res.stringResource
import com.yenaly.han1meviewer.ui.navigation.popBackStackSafely
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetwork
import com.yenaly.han1meviewer.HentaiMama.settings.HentaiMamaCardSettingsTarget
import com.yenaly.han1meviewer.HentaiMama.settings.HentaiMamaVideoSettingsScreen
import com.yenaly.han1meviewer.HentaiMama.ui.history.HentaiMamaHistoryScreen
import com.yenaly.han1meviewer.HentaiMama.ui.home.HentaiMamaHomeScreen
import com.yenaly.han1meviewer.HentaiMama.ui.home.HentaiMamaHomeSettingsScreen
import com.yenaly.han1meviewer.HentaiMama.ui.home.HentaiMamaViewModel
import com.yenaly.han1meviewer.HentaiMama.ui.playlist.HentaiMamaPlaylistDetailScreen
import com.yenaly.han1meviewer.HentaiMama.ui.playlist.HentaiMamaPlaylistsScreen
import com.yenaly.han1meviewer.HentaiMama.ui.recent.HentaiMamaRecentEpisodesScreen
import com.yenaly.han1meviewer.HentaiMama.ui.search.HentaiMamaSearchScreen
import com.yenaly.han1meviewer.HentaiMama.ui.series.HentaiMamaSeriesScreen
import com.yenaly.han1meviewer.HentaiMama.ui.upcoming.HentaiMamaUpcomingScreen
import com.yenaly.han1meviewer.HentaiMama.ui.video.HentaiMamaVideoScreen
import com.yenaly.han1meviewer.HentaiMama.ui.video.HentaiMamaVideoViewModel
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.navigation.NavigationManager
import com.yenaly.han1meviewer.ui.navigation.canNavigateSafely
import com.yenaly.han1meviewer.ui.navigation.navigateSafely
import com.yenaly.han1meviewer.ui.navigation.main.MainDestinationSpec
import com.yenaly.han1meviewer.ui.navigation.settings.HomeSettingsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.NetworkSettingsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.PlayerSettingsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.UnifiedHistoryRoute
import com.yenaly.han1meviewer.ui.navigation.settings.registerGlobalSettingsRoutes
import com.yenaly.han1meviewer.ui.screen.settings.SiteSettingsEntry
import com.yenaly.han1meviewer.ui.screen.settings.SiteSettingsHubScreen
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable
object HentaiMamaHomeRoute

@Serializable
data class HentaiMamaSearchRoute(
    val query: String? = null,
    val categoryKey: String? = null,
    val genre: String? = null,
    val order: String? = null,
    val genreSlug: String? = null,
)

@Serializable
data class HentaiMamaVideoRoute(
    val videoCode: String,
    val path: String,
    val resumePosition: Long = 0L,
)

@Serializable
data class HentaiMamaSeriesRoute(
    val slug: String,
)

@Serializable
object HentaiMamaHistoryRoute

@Serializable
object HentaiMamaUpcomingRoute

@Serializable
object HentaiMamaRecentEpisodesRoute

@Serializable
object HentaiMamaPlaylistsRoute

@Serializable
data class HentaiMamaPlaylistDetailRoute(
    val playlistId: String,
)

@Serializable
object HentaiMamaSettingsHubRoute

@Serializable
data class HentaiMamaVideoSettingsRoute(
    val target: String = "all",
)

@Serializable
object HentaiMamaHomeSettingsRoute

@Composable
fun HentaiMamaNavHost(
    activity: MainActivity,
    navController: NavHostController,
    isDrawerOpen: Boolean,
    onOpenDrawer: () -> Unit,
    onDestinationChanged: (MainDestinationSpec) -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(50)
        NavigationManager.initialize(navController, SiteType.HENTAIMAMA)
    }

    DisposableEffect(navController) {
        onDispose {
            val vm: HentaiMamaViewModel? = null
            vm?.resetAll()
        }
    }

    NavHost(
        navController = navController,
        startDestination = HentaiMamaHomeRoute,
    ) {

        composable<HentaiMamaHomeRoute> {
            HentaiMamaHomeScreen(
                onNavigateToVideo = { code, path ->
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(
                            HentaiMamaSeriesRoute(slug = code)
                        )
                    }
                },
                onNavigateToSearch = { query ->
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(HentaiMamaSearchRoute(query = query))
                    }
                },
                onNavigateToCategorySearch = { categoryKey ->
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(
                            HentaiMamaSearchRoute(categoryKey = categoryKey)
                        )
                    }
                },
                onNavigateToGenre = { slug ->
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(
                            HentaiMamaSearchRoute(genreSlug = slug)
                        )
                    }
                },
                onNavigateToSettings = onOpenDrawer,
                onNavigateToHistory = {
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(HentaiMamaHistoryRoute)
                    }
                },
                onNavigateToHome = {
                    navController.popBackStack(HentaiMamaHomeRoute, inclusive = false)
                },
                onSwitchSite = { activity.requestSiteSwitch() },
            )
        }

        composable<HentaiMamaSearchRoute> {
            val route = it.toRoute<HentaiMamaSearchRoute>()
            HentaiMamaSearchScreen(
                initialQuery = route.query,
                initialCategoryKey = route.categoryKey,
                initialGenre = route.genre,
                initialOrder = route.order,
                initialGenreSlug = route.genreSlug,
                onBack = { navController.popBackStackSafely() },
                onNavigateToVideo = { code ->
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(
                            HentaiMamaSeriesRoute(slug = code)
                        )
                    }
                },
            )
        }

        composable<HentaiMamaVideoRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<HentaiMamaVideoRoute>()
            val videoViewModel: HentaiMamaVideoViewModel = viewModel(
                key = route.videoCode,
            )
            HentaiMamaVideoScreen(
                videoCode = route.videoCode,
                path = route.path,
                resumePosition = route.resumePosition,
                onBack = { navController.popBackStackSafely() },
                onNavigateToVideo = { code, path ->
                    if (code != route.videoCode && navController.canNavigateSafely()) {
                        navController.navigateSafely(
                            HentaiMamaVideoRoute(videoCode = code, path = path)
                        )
                    }
                },
                onNavigateToSearch = { query ->
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(HentaiMamaSearchRoute(query = query))
                    }
                },
                onNavigateToSeries = { slug ->
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(HentaiMamaSeriesRoute(slug = slug))
                    }
                },
                viewModel = videoViewModel,
            )
        }

        composable<HentaiMamaSeriesRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<HentaiMamaSeriesRoute>()
            HentaiMamaSeriesScreen(
                slug = route.slug,
                onBack = { navController.popBackStackSafely() },
                onOpenEpisode = { code, url ->
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(
                            HentaiMamaVideoRoute(videoCode = code, path = url)
                        )
                    }
                },
                onOpenRelatedSeries = { relatedSlug ->
                    if (relatedSlug != route.slug && navController.canNavigateSafely()) {
                        navController.navigateSafely(
                            HentaiMamaSeriesRoute(slug = relatedSlug)
                        )
                    }
                },
                onGenreClick = { genre ->
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(HentaiMamaSearchRoute(query = genre))
                    }
                },
            )
        }

        composable<HentaiMamaHistoryRoute> {
            HentaiMamaHistoryScreen(
                onBack = { navController.popBackStackSafely() },
                onNavigateToSeries = { videoCode, lastEpisodeUrl, resumePosition ->
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(
                            HentaiMamaVideoRoute(
                                videoCode = videoCode,
                                path = lastEpisodeUrl,
                                resumePosition = resumePosition,
                            )
                        )
                    }
                },
                onBrowseHome = {
                    navController.popBackStack(HentaiMamaHomeRoute, inclusive = false)
                },
            )
        }

        composable<HentaiMamaUpcomingRoute> {
            HentaiMamaUpcomingScreen(
                onBack = { navController.popBackStackSafely() },
                onOpenEpisode = { slug, url ->
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(
                            HentaiMamaVideoRoute(videoCode = slug, path = url)
                        )
                    }
                },
                onOpenSeries = { slug ->
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(
                            HentaiMamaSeriesRoute(slug = slug)
                        )
                    }
                },
                onOpenStudio = { studioSlug ->
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(
                            HentaiMamaSearchRoute(query = studioSlug)
                        )
                    }
                },
                onNavigateToCardSettings = {
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(
                            HentaiMamaVideoSettingsRoute(target = "upcoming")
                        )
                    }
                },
            )
        }

        composable<HentaiMamaRecentEpisodesRoute> {
            HentaiMamaRecentEpisodesScreen(
                onBack = { navController.popBackStackSafely() },
                onOpenEpisode = { slug, url ->
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(
                            HentaiMamaVideoRoute(videoCode = slug, path = url)
                        )
                    }
                },
                onNavigateToCardSettings = {
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(
                            HentaiMamaVideoSettingsRoute(target = "recent")
                        )
                    }
                },
            )
        }

        composable<HentaiMamaPlaylistsRoute> {
            HentaiMamaPlaylistsScreen(
                onBack = { navController.popBackStackSafely() },
                onOpenPlaylist = { playlistId ->
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(
                            HentaiMamaPlaylistDetailRoute(playlistId = playlistId)
                        )
                    }
                },
                onPlayFirstEpisode = { _: String, episodeUrl: String ->
                    if (navController.canNavigateSafely()) {
                        val slug = episodeUrl.trimEnd('/').substringAfterLast('/')
                        navController.navigateSafely(
                            HentaiMamaVideoRoute(
                                videoCode = slug,
                                path = episodeUrl,
                            )
                        )
                    }
                },
            )
        }

        composable<HentaiMamaPlaylistDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<HentaiMamaPlaylistDetailRoute>()
            HentaiMamaPlaylistDetailScreen(
                playlistId = route.playlistId,
                onBack = { navController.popBackStackSafely() },
                onOpenEpisode = { _: String, episodeUrl: String, _: Int ->
                    if (navController.canNavigateSafely()) {
                        val slug = episodeUrl.trimEnd('/').substringAfterLast('/')
                        navController.navigateSafely(
                            HentaiMamaVideoRoute(
                                videoCode = slug,
                                path = episodeUrl,
                            )
                        )
                    }
                },
                onPlayAll = { episodeUrl ->
                    if (navController.canNavigateSafely()) {
                        val slug = episodeUrl.trimEnd('/').substringAfterLast('/')
                        navController.navigateSafely(
                            HentaiMamaVideoRoute(
                                videoCode = slug,
                                path = episodeUrl,
                            )
                        )
                    }
                },
                onShuffle = { episodeUrl ->
                    if (navController.canNavigateSafely()) {
                        val slug = episodeUrl.trimEnd('/').substringAfterLast('/')
                        navController.navigateSafely(
                            HentaiMamaVideoRoute(
                                videoCode = slug,
                                path = episodeUrl,
                            )
                        )
                    }
                },
                onOpenOwner = { ownerUrl ->
                    if (navController.canNavigateSafely()) {
                        val username = ownerUrl.trimEnd('/').substringAfterLast('/')
                        navController.navigateSafely(
                            HentaiMamaSearchRoute(query = username)
                        )
                    }
                },
            )
        }

        composable<HentaiMamaSettingsHubRoute> {
            fun open(route: Any) {
                if (navController.canNavigateSafely()) navController.navigateSafely(route)
            }
            SiteSettingsHubScreen(
                activity = activity,
                site = SiteType.HENTAIMAMA,
                siteEntries = listOf(
                    SiteSettingsEntry(
                        key = "video",
                        title = stringResource(R.string.drawer_video_card_size),
                        subtitle = stringResource(R.string.hm_video_card_subtitle),
                        icon = Icons.Default.PlayCircleOutline,
                        onClick = { open(HentaiMamaVideoSettingsRoute(target = "all")) },
                    ),
                    SiteSettingsEntry(
                        key = "home_categories",
                        title = stringResource(R.string.drawer_home_categories),
                        subtitle = stringResource(R.string.site_home_categories_subtitle),
                        icon = Icons.Default.Category,
                        onClick = { open(HentaiMamaHomeSettingsRoute) },
                    ),
                    SiteSettingsEntry(
                        key = "history",
                        title = stringResource(R.string.hm_history_title),
                        subtitle = stringResource(R.string.hm_history_subtitle),
                        icon = Icons.Default.History,
                        onClick = { open(HentaiMamaHistoryRoute) },
                    ),
                    SiteSettingsEntry(
                        key = "playlists",
                        title = stringResource(R.string.hm_playlists_title),
                        subtitle = stringResource(R.string.hm_playlists_subtitle),
                        icon = Icons.Default.QueueMusic,
                        onClick = { open(HentaiMamaPlaylistsRoute) },
                    ),
                    SiteSettingsEntry(
                        key = "recent_episodes",
                        title = stringResource(R.string.drawer_recent_episodes),
                        subtitle = stringResource(R.string.hm_recent_episodes_subtitle),
                        icon = Icons.Default.VideoLibrary,
                        onClick = { open(HentaiMamaRecentEpisodesRoute) },
                    ),
                    SiteSettingsEntry(
                        key = "upcoming",
                        title = stringResource(R.string.drawer_upcoming),
                        subtitle = stringResource(R.string.hm_upcoming_subtitle),
                        icon = Icons.Default.CalendarMonth,
                        onClick = { open(HentaiMamaUpcomingRoute) },
                    ),
                ),
                onBack = { navController.popBackStackSafely() },
                onOpenAppSettings = { open(HomeSettingsRoute) },
                onOpenPlayerSettings = { open(PlayerSettingsRoute) },
                onOpenNetworkSettings = { open(NetworkSettingsRoute) },
                onOpenUnifiedHistory = { open(UnifiedHistoryRoute) },
            )
        }

        composable<HentaiMamaVideoSettingsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<HentaiMamaVideoSettingsRoute>()
            val targets: Set<HentaiMamaCardSettingsTarget> = when (route.target) {
                "recent" -> setOf(HentaiMamaCardSettingsTarget.Recent)
                "upcoming" -> setOf(HentaiMamaCardSettingsTarget.Upcoming)
                "home" -> setOf(HentaiMamaCardSettingsTarget.Home)
                else -> setOf(
                    HentaiMamaCardSettingsTarget.Home,
                    HentaiMamaCardSettingsTarget.Recent,
                    HentaiMamaCardSettingsTarget.Upcoming,
                )
            }
            HentaiMamaVideoSettingsScreen(
                onBack = { navController.popBackStackSafely() },
                targets = targets,
            )
        }

        composable<HentaiMamaHomeSettingsRoute> {
            HentaiMamaHomeSettingsScreen(
                onBack = { navController.popBackStackSafely() },
            )
        }

        registerGlobalSettingsRoutes(
            activity = activity,
            navController = navController,
            homeFallback = HentaiMamaSettingsHubRoute,
        )
    }
}
