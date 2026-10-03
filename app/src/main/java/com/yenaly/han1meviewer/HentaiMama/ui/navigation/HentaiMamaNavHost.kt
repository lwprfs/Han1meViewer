package com.yenaly.han1meviewer.HentaiMama.ui.navigation

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
import com.yenaly.han1meviewer.HentaiMama.ui.home.HentaiMamaSettingsHubScreen
import com.yenaly.han1meviewer.HentaiMama.ui.home.HentaiMamaViewModel
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
                            HentaiMamaVideoRoute(videoCode = code, path = path)
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
                onNavigateToSettings = {
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(HentaiMamaSettingsHubRoute)
                    }
                },
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
                onBack = { navController.popBackStack() },
                onNavigateToVideo = { code ->
                    if (navController.canNavigateSafely()) {
                        val path = HentaiMamaNetwork.normalizeUrl("/$code")
                        navController.navigateSafely(
                            HentaiMamaVideoRoute(videoCode = code, path = path)
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
                onBack = { navController.popBackStack() },
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
                onBack = { navController.popBackStack() },
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
                onBack = { navController.popBackStack() },
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
                onBack = { navController.popBackStack() },
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
                onBack = { navController.popBackStack() },
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

        composable<HentaiMamaSettingsHubRoute> {
            HentaiMamaSettingsHubScreen(
                onBack = { navController.popBackStack() },
                onNavigateToVideoSettings = {
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(
                            HentaiMamaVideoSettingsRoute(target = "all")
                        )
                    }
                },
                onNavigateToHomeCategories = {
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(HentaiMamaHomeSettingsRoute)
                    }
                },
                onNavigateToHistory = {
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(HentaiMamaHistoryRoute)
                    }
                },
                onNavigateToUpcoming = {
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(HentaiMamaUpcomingRoute)
                    }
                },
                onNavigateToRecentEpisodes = {
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(HentaiMamaRecentEpisodesRoute)
                    }
                },
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
                onBack = { navController.popBackStack() },
                targets = targets,
            )
        }

        composable<HentaiMamaHomeSettingsRoute> {
            HentaiMamaHomeSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
