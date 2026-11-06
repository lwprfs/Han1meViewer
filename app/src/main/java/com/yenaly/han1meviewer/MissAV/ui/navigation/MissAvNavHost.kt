package com.yenaly.han1meviewer.MissAV.ui.navigation
import com.yenaly.han1meviewer.R
import androidx.compose.ui.res.stringResource
import com.yenaly.han1meviewer.ui.navigation.popBackStackSafely
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.navigation.NavigationManager
import com.yenaly.han1meviewer.ui.navigation.navigateSafely
import com.yenaly.han1meviewer.SiteType
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
import com.yenaly.han1meviewer.MissAV.ui.home.MissAvHomeScreen
import com.yenaly.han1meviewer.MissAV.ui.home.MissAvHomeSettingsScreen
import com.yenaly.han1meviewer.MissAV.ui.home.MissAvHostSettingsScreen
import com.yenaly.han1meviewer.MissAV.ui.home.MissAvQualitySettingsScreen
import com.yenaly.han1meviewer.MissAV.ui.home.MissAvSubtitleSettingsScreen
import com.yenaly.han1meviewer.MissAV.ui.search.MissAvSearchScreen
import com.yenaly.han1meviewer.MissAV.ui.video.MissAvVideoScreen
import com.yenaly.han1meviewer.MissAV.ui.history.MissAvHistoryScreen

import com.yenaly.han1meviewer.MissAV.ui.home.MissAvHomeViewModel
import com.yenaly.han1meviewer.MissAV.viewmodel.MissAvViewModel
@Serializable
object MissAvHomeRoute

@Serializable
object MissAvSettingsHubRoute

@Serializable
object MissAvHomeCategoriesRoute

@Serializable
object MissAvHostSettingsRoute

@Serializable
object MissAvQualitySettingsRoute

@Serializable
object MissAvSubtitleSettingsRoute

@Serializable
data class MissAvSearchRoute(val query: String? = null)

@Serializable
data class MissAvVideoRoute(val videoCode: String, val path: String)

@Serializable
object MissAvHistoryRoute

@Composable
fun MissAvNavHost(
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
        NavigationManager.initialize(navController, SiteType.MISSAV)
    }

    val sharedMissAvViewModel: MissAvViewModel = viewModel()
    val sharedHomeViewModel: MissAvHomeViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = MissAvHomeRoute,
        modifier = modifier,
    ) {
        composable<MissAvHomeRoute> {
            Box(modifier = Modifier.padding(contentPadding)) {
                MissAvHomeScreen(
                    onNavigateToVideo = { code, path ->
                        navController.navigateSafely(MissAvVideoRoute(code, path))
                    },
                    onNavigateToSearch = { query ->
                        if (!query.isNullOrBlank()) {
                            sharedMissAvViewModel.setSearchQuery(query)
                        }
                        navController.navigateSafely(MissAvSearchRoute(query))
                    },
                    onSwitchSite = { activity.requestSiteSwitch() },
                    onNavigateToHistory = {
                        navController.navigateSafely(MissAvHistoryRoute)
                    },
                    onNavigateToSettings = onOpenDrawer,
                    viewModel = sharedHomeViewModel,
                )
            }
        }

        composable<MissAvSettingsHubRoute> {
            SiteSettingsHubScreen(
                activity = activity,
                site = SiteType.MISSAV,
                siteEntries = listOf(
                    SiteSettingsEntry(
                        key = "home_categories",
                        title = stringResource(R.string.drawer_home_categories),
                        subtitle = stringResource(R.string.site_home_categories_subtitle),
                        icon = Icons.Default.Category,
                        onClick = { navController.navigateSafely(MissAvHomeCategoriesRoute) },
                    ),
                    SiteSettingsEntry(
                        key = "host",
                        title = stringResource(R.string.drawer_host_mirror),
                        subtitle = stringResource(R.string.missav_host_subtitle),
                        icon = Icons.Default.Dns,
                        onClick = { navController.navigateSafely(MissAvHostSettingsRoute) },
                    ),
                    SiteSettingsEntry(
                        key = "video_quality",
                        title = stringResource(R.string.missav_video_resolution),
                        subtitle = stringResource(R.string.missav_choose_auto_or_a_fixed_starting),
                        icon = Icons.Default.HighQuality,
                        onClick = { navController.navigateSafely(MissAvQualitySettingsRoute) },
                    ),
                    SiteSettingsEntry(
                        key = "video_subtitle",
                        title = "Video Subtitle",
                        subtitle = "Choose the subtitle source and language",
                        icon = Icons.Default.Subtitles,
                        onClick = { navController.navigateSafely(MissAvSubtitleSettingsRoute) },
                    ),
                    SiteSettingsEntry(
                        key = "history",
                        title = stringResource(R.string.missav_history_title),
                        subtitle = stringResource(R.string.missav_history_subtitle),
                        icon = Icons.Default.History,
                        onClick = { navController.navigateSafely(MissAvHistoryRoute) },
                    ),
                ),
                onBack = { navController.popBackStackSafely() },
                onOpenAppSettings = { navController.navigateSafely(HomeSettingsRoute) },
                onOpenPlayerSettings = { navController.navigateSafely(PlayerSettingsRoute) },
                onOpenNetworkSettings = { navController.navigateSafely(NetworkSettingsRoute) },
                onOpenUnifiedHistory = { navController.navigateSafely(UnifiedHistoryRoute) },
            )
        }

        composable<MissAvHomeCategoriesRoute> {
            MissAvHomeSettingsScreen(
                viewModel = sharedHomeViewModel,
                onBack = { navController.popBackStackSafely() },
            )
        }

        composable<MissAvHostSettingsRoute> {
            MissAvHostSettingsScreen(
                onBack = { navController.popBackStackSafely() },
            )
        }

        composable<MissAvQualitySettingsRoute> {
            MissAvQualitySettingsScreen(
                onBack = { navController.popBackStackSafely() },
            )
        }

        composable<MissAvSubtitleSettingsRoute> {
            MissAvSubtitleSettingsScreen(
                onBack = { navController.popBackStackSafely() },
            )
        }

        composable<MissAvSearchRoute> {
            val route = it.toRoute<MissAvSearchRoute>()
            MissAvSearchScreen(
                initialQuery = route.query,
                onBack = { navController.popBackStackSafely() },
                onNavigateToVideo = { code, path ->
                    navController.navigateSafely(MissAvVideoRoute(code, path))
                },
                viewModel = sharedMissAvViewModel,
                onOpenOtherSite = { request -> activity.confirmCrossSiteOpen(request) },
            )
        }

        composable<MissAvVideoRoute> {
            val route = it.toRoute<MissAvVideoRoute>()
            MissAvVideoScreen(
                videoCode = route.videoCode,
                path = route.path,
                onBack = { navController.popBackStackSafely() },
                onNavigateToVideo = { code, path ->
                    navController.navigateSafely(MissAvVideoRoute(code, "/$code"))
                },
                onNavigateToSearch = { query ->
                    if (!query.isNullOrBlank()) {
                        sharedMissAvViewModel.setSearchQuery(query)
                    }
                    navController.navigateSafely(MissAvSearchRoute(query))
                },
                viewModel = sharedMissAvViewModel,
            )
        }

        composable<MissAvHistoryRoute> {
            MissAvHistoryScreen(
                onNavigateToVideo = { code, path ->
                    navController.navigateSafely(MissAvVideoRoute(code, path))
                },
                onBack = { navController.popBackStackSafely() },
            )
        }

        registerGlobalSettingsRoutes(
            activity = activity,
            navController = navController,
            homeFallback = MissAvSettingsHubRoute,
        )
    }
}
