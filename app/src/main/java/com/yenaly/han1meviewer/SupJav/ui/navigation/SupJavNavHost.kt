package com.yenaly.han1meviewer.SupJav.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.SupJav.ui.history.SupJavHistoryScreen
import com.yenaly.han1meviewer.SupJav.ui.home.SupJavHomeScreen
import com.yenaly.han1meviewer.SupJav.ui.search.SupJavSearchScreen
import com.yenaly.han1meviewer.SupJav.ui.settings.SupJavSettingsScreen
import com.yenaly.han1meviewer.SupJav.ui.video.SupJavVideoScreen
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.navigation.NavigationManager
import com.yenaly.han1meviewer.ui.navigation.main.MainDestinationSpec
import com.yenaly.han1meviewer.ui.navigation.navigateSafely
import com.yenaly.han1meviewer.ui.navigation.popBackStackSafely
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
object SupJavHomeRoute

@Serializable
object SupJavSettingsHubRoute

@Serializable
data class SupJavSearchRoute(val query: String? = null)

@Serializable
data class SupJavVideoRoute(val videoCode: String, val resumePosition: Long = 0L)

@Serializable
object SupJavHistoryRoute

@Serializable
object SupJavSettingsRoute

@Composable
fun SupJavNavHost(
    activity: MainActivity,
    navController: NavHostController,
    isDrawerOpen: Boolean,
    onOpenDrawer: () -> Unit,
    onDestinationChanged: (MainDestinationSpec) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LaunchedEffect(Unit) {
        delay(50)
        NavigationManager.initialize(navController, SiteType.SUPJAV)
    }

    NavHost(
        navController = navController,
        startDestination = SupJavHomeRoute,
        modifier = modifier,
    ) {
        composable<SupJavHomeRoute> {
            Box(modifier = Modifier.padding(contentPadding)) {
                SupJavHomeScreen(
                    onNavigateToVideo = { code ->
                        navController.navigateSafely(SupJavVideoRoute(code))
                    },
                    onNavigateToSearch = { query ->
                        navController.navigateSafely(SupJavSearchRoute(query))
                    },
                    onNavigateToHistory = { navController.navigateSafely(SupJavHistoryRoute) },
                    onOpenDrawer = onOpenDrawer,
                    onSwitchSite = { activity.requestSiteSwitch() },
                )
            }
        }

        composable<SupJavSettingsHubRoute> {
            SiteSettingsHubScreen(
                activity = activity,
                site = SiteType.SUPJAV,
                siteEntries = listOf(
                    SiteSettingsEntry(
                        key = "playback",
                        title = "Playback & servers",
                        subtitle = "Preferred quality, server order and language",
                        icon = Icons.Default.HighQuality,
                        onClick = { navController.navigateSafely(SupJavSettingsRoute) },
                    ),
                    SiteSettingsEntry(
                        key = "history",
                        title = "Watch history",
                        subtitle = "Videos you watched on SupJav",
                        icon = Icons.Default.History,
                        onClick = { navController.navigateSafely(SupJavHistoryRoute) },
                    ),
                ),
                onBack = { navController.popBackStackSafely() },
                onOpenAppSettings = { navController.navigateSafely(HomeSettingsRoute) },
                onOpenPlayerSettings = { navController.navigateSafely(PlayerSettingsRoute) },
                onOpenNetworkSettings = { navController.navigateSafely(NetworkSettingsRoute) },
                onOpenUnifiedHistory = { navController.navigateSafely(UnifiedHistoryRoute) },
            )
        }

        composable<SupJavSettingsRoute> {
            SupJavSettingsScreen(onBack = { navController.popBackStackSafely() })
        }

        composable<SupJavSearchRoute> {
            val route = it.toRoute<SupJavSearchRoute>()
            SupJavSearchScreen(
                initialQuery = route.query,
                onBack = { navController.popBackStackSafely() },
                onOpenOtherSite = { request -> activity.confirmCrossSiteOpen(request) },
                onNavigateToVideo = { code ->
                    navController.navigateSafely(SupJavVideoRoute(code))
                },
            )
        }

        composable<SupJavVideoRoute> {
            val route = it.toRoute<SupJavVideoRoute>()
            SupJavVideoScreen(
                videoCode = route.videoCode,
                resumePosition = route.resumePosition,
                onBack = { navController.popBackStackSafely() },
                onNavigateToVideo = { code ->
                    navController.navigateSafely(SupJavVideoRoute(code))
                },
                onNavigateToSearch = { query ->
                    navController.navigateSafely(SupJavSearchRoute(query))
                },
            )
        }

        composable<SupJavHistoryRoute> {
            SupJavHistoryScreen(
                onNavigateToVideo = { code ->
                    navController.navigateSafely(SupJavVideoRoute(code))
                },
                onBack = { navController.popBackStackSafely() },
            )
        }

        registerGlobalSettingsRoutes(
            activity = activity,
            navController = navController,
            homeFallback = SupJavSettingsHubRoute,
        )
    }
}
