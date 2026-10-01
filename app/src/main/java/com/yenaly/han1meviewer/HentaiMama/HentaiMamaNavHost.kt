package com.yenaly.han1meviewer.HentaiMama

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.HentaiMama.settings.HentaiMamaVideoSettingsScreen
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
)

@Serializable
data class HentaiMamaVideoRoute(
    val videoCode: String,
    val path: String,
    val resumePosition: Long = 0L,
)

@Serializable
object HentaiMamaHistoryRoute

@Serializable
object HentaiMamaSettingsHubRoute

@Serializable
object HentaiMamaVideoSettingsRoute

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
            )
        }

        composable<HentaiMamaSettingsHubRoute> {
            HentaiMamaSettingsHubScreen(
                onBack = { navController.popBackStack() },
                onNavigateToVideoSettings = {
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(HentaiMamaVideoSettingsRoute)
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
            )
        }

        composable<HentaiMamaVideoSettingsRoute> {
            HentaiMamaVideoSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable<HentaiMamaHomeSettingsRoute> {
            HentaiMamaHomeSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
