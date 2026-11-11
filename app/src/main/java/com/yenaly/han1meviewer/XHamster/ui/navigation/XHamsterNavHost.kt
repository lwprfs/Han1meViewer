package com.yenaly.han1meviewer.XHamster.ui.navigation

import com.yenaly.han1meviewer.R
import androidx.compose.ui.res.stringResource
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
import com.yenaly.han1meviewer.XHamster.ui.account.XHamsterAccountScreen
import com.yenaly.han1meviewer.XHamster.ui.categories.XHamsterCategoriesScreen
import com.yenaly.han1meviewer.XHamster.ui.categories.XHamsterCategoryScreen
import com.yenaly.han1meviewer.XHamster.ui.history.XHamsterHistoryScreen
import com.yenaly.han1meviewer.XHamster.ui.home.XHamsterHomeScreen
import com.yenaly.han1meviewer.XHamster.ui.search.XHamsterSearchScreen
import com.yenaly.han1meviewer.XHamster.ui.settings.XHamsterSettingsScreen
import com.yenaly.han1meviewer.XHamster.ui.video.XHamsterVideoScreen
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.navigation.NavigationManager
import com.yenaly.han1meviewer.ui.navigation.main.MainDestinationSpec
import com.yenaly.han1meviewer.ui.navigation.navigateSafely
import com.yenaly.han1meviewer.ui.navigation.popBackStackSafely
import com.yenaly.han1meviewer.ui.navigation.settings.HomeSettingsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.NetworkSettingsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.PlayerSettingsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.SitePlayerSettingsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.UnifiedHistoryRoute
import com.yenaly.han1meviewer.ui.navigation.settings.registerGlobalSettingsRoutes
import com.yenaly.han1meviewer.ui.screen.settings.SiteSettingsEntry
import com.yenaly.han1meviewer.ui.screen.settings.SiteSettingsHubScreen
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable
object XHamsterHomeRoute

@Serializable
object XHamsterSettingsHubRoute

@Serializable
data class XHamsterSearchRoute(val query: String? = null)

@Serializable
data class XHamsterVideoRoute(val videoCode: String, val resumePosition: Long = 0L)

@Serializable
object XHamsterHistoryRoute

@Serializable
object XHamsterSettingsRoute

@Serializable
object XHamsterCategoriesRoute

@Serializable
object XHamsterAccountRoute

@Serializable
data class XHamsterCategoryRoute(val path: String, val title: String)

@Composable
fun XHamsterNavHost(
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
        NavigationManager.initialize(navController, SiteType.XHAMSTER)
    }

    NavHost(
        navController = navController,
        startDestination = XHamsterHomeRoute,
        modifier = modifier,
    ) {
        composable<XHamsterHomeRoute> {
            Box(modifier = Modifier.padding(contentPadding)) {
                XHamsterHomeScreen(
                    onNavigateToVideo = { code ->
                        navController.navigateSafely(XHamsterVideoRoute(code))
                    },
                    onNavigateToSearch = { query ->
                        navController.navigateSafely(XHamsterSearchRoute(query))
                    },
                    onNavigateToHistory = { navController.navigateSafely(XHamsterHistoryRoute) },
                    onNavigateToCategories = {
                        navController.navigateSafely(XHamsterCategoriesRoute)
                    },
                    onOpenDrawer = onOpenDrawer,
                    onSwitchSite = { activity.requestSiteSwitch() },
                )
            }
        }

        composable<XHamsterSettingsHubRoute> {
            SiteSettingsHubScreen(
                activity = activity,
                site = SiteType.XHAMSTER,
                siteEntries = listOf(
                    SiteSettingsEntry(
                        key = "playback",
                        title = stringResource(R.string.xhamster_settings_title),
                        subtitle = stringResource(R.string.xhamster_settings_summary),
                        icon = Icons.Default.HighQuality,
                        onClick = { navController.navigateSafely(XHamsterSettingsRoute) },
                    ),
                    SiteSettingsEntry(
                        key = "history",
                        title = stringResource(R.string.xhamster_history_title),
                        subtitle = stringResource(R.string.xhamster_history_summary),
                        icon = Icons.Default.History,
                        onClick = { navController.navigateSafely(XHamsterHistoryRoute) },
                    ),
                ),
                onBack = { navController.popBackStackSafely() },
                onOpenAppSettings = { navController.navigateSafely(HomeSettingsRoute) },
                onOpenPlayerSettings = { navController.navigateSafely(SitePlayerSettingsRoute(SiteType.XHAMSTER.name)) },
                onOpenNetworkSettings = { navController.navigateSafely(NetworkSettingsRoute) },
                onOpenUnifiedHistory = { navController.navigateSafely(UnifiedHistoryRoute) },
            )
        }

        composable<XHamsterSettingsRoute> {
            XHamsterSettingsScreen(onBack = { navController.popBackStackSafely() })
        }

        composable<XHamsterSearchRoute> {
            val route = it.toRoute<XHamsterSearchRoute>()
            XHamsterSearchScreen(
                initialQuery = route.query,
                onBack = { navController.popBackStackSafely() },
                onNavigateToVideo = { code ->
                    navController.navigateSafely(XHamsterVideoRoute(code))
                },
            )
        }

        composable<XHamsterVideoRoute> {
            val route = it.toRoute<XHamsterVideoRoute>()
            XHamsterVideoScreen(
                videoId = route.videoCode,
                resumePosition = route.resumePosition,
                onBack = { navController.popBackStackSafely() },
                onNavigateToVideo = { code ->
                    navController.navigateSafely(XHamsterVideoRoute(code))
                },
                onNavigateToPath = { path, title ->
                    navController.navigateSafely(XHamsterCategoryRoute(path, title))
                },
            )
        }

        composable<XHamsterAccountRoute> {
            XHamsterAccountScreen(
                onBack = { navController.popBackStackSafely() },
                onOpenPath = { path, title ->
                    navController.navigateSafely(XHamsterCategoryRoute(path, title))
                },
            )
        }

        composable<XHamsterCategoriesRoute> {
            XHamsterCategoriesScreen(
                onBack = { navController.popBackStackSafely() },
                onOpenCategory = { path, title ->
                    navController.navigateSafely(XHamsterCategoryRoute(path, title))
                },
            )
        }

        composable<XHamsterCategoryRoute> {
            val route = it.toRoute<XHamsterCategoryRoute>()
            XHamsterCategoryScreen(
                path = route.path,
                title = route.title,
                onBack = { navController.popBackStackSafely() },
                onNavigateToVideo = { code ->
                    navController.navigateSafely(XHamsterVideoRoute(code))
                },
            )
        }

        composable<XHamsterHistoryRoute> {
            XHamsterHistoryScreen(
                onNavigateToVideo = { code ->
                    navController.navigateSafely(XHamsterVideoRoute(code))
                },
                onBack = { navController.popBackStackSafely() },
            )
        }

        registerGlobalSettingsRoutes(
            activity = activity,
            navController = navController,
            homeFallback = XHamsterSettingsHubRoute,
        )
    }
}
