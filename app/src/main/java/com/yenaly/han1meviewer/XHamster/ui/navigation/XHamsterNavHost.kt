package com.yenaly.han1meviewer.XHamster.ui.navigation

import com.yenaly.han1meviewer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import android.content.Intent
import android.net.Uri
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
import com.yenaly.han1meviewer.XHamster.common.XHamsterConstants
import com.yenaly.han1meviewer.XHamster.ui.entities.XHamsterAccountSettingsScreen
import com.yenaly.han1meviewer.XHamster.ui.entities.XHamsterChannelsScreen
import com.yenaly.han1meviewer.XHamster.ui.entities.XHamsterCreatorDashboardScreen
import com.yenaly.han1meviewer.XHamster.ui.entities.XHamsterCreatorsScreen
import com.yenaly.han1meviewer.XHamster.ui.entities.XHamsterPhotosScreen
import com.yenaly.han1meviewer.XHamster.ui.gallery.XHamsterGalleryScreen
import com.yenaly.han1meviewer.XHamster.ui.profile.XHamsterProfileScreen
import com.yenaly.han1meviewer.XHamster.ui.entities.XHamsterTopCreatorVideosScreen
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

@Serializable
object XHamsterChannelsRoute

@Serializable
object XHamsterCreatorsRoute

@Serializable
object XHamsterPhotosRoute

@Serializable
object XHamsterTopCreatorVideosRoute

@Serializable
object XHamsterDashboardRoute

@Serializable
object XHamsterAccountSettingsRoute

@Serializable
data class XHamsterProfileRoute(val path: String, val title: String)

@Serializable
data class XHamsterGalleryRoute(val url: String, val title: String)

private fun isProfilePath(path: String): Boolean =
    path.startsWith("/users/") || path.startsWith("/creators/") || path.startsWith("/pornstars/")

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
                        key = "account",
                        title = stringResource(R.string.xhamster_account_settings),
                        subtitle = stringResource(R.string.xhamster_account_settings_summary),
                        icon = Icons.Default.Person,
                        onClick = { navController.navigateSafely(XHamsterAccountSettingsRoute) },
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
                onNavigateToUploader = { path, title ->
                    if (isProfilePath(path)) {
                        navController.navigateSafely(XHamsterProfileRoute(path, title))
                    } else {
                        navController.navigateSafely(XHamsterCategoryRoute(path, title))
                    }
                },
            )
        }

        composable<XHamsterAccountRoute> {
            XHamsterAccountScreen(
                onBack = { navController.popBackStackSafely() },
                onOpenDashboard = { navController.navigateSafely(XHamsterDashboardRoute) },
                onOpenSettings = { navController.navigateSafely(XHamsterAccountSettingsRoute) },
                onOpenPath = { path, title ->
                    if (isProfilePath(path)) {
                        navController.navigateSafely(XHamsterProfileRoute(path, title))
                    } else {
                        navController.navigateSafely(XHamsterCategoryRoute(path, title))
                    }
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

        composable<XHamsterChannelsRoute> {
            XHamsterChannelsScreen(
                onBack = { navController.popBackStackSafely() },
                onOpenChannel = { path, title ->
                    navController.navigateSafely(XHamsterCategoryRoute(path, title))
                },
            )
        }

        composable<XHamsterCreatorsRoute> {
            XHamsterCreatorsScreen(
                onBack = { navController.popBackStackSafely() },
                onOpenCreator = { path, title ->
                    navController.navigateSafely(XHamsterProfileRoute(path, title))
                },
                onOpenTopVideos = { navController.navigateSafely(XHamsterTopCreatorVideosRoute) },
            )
        }

        composable<XHamsterTopCreatorVideosRoute> {
            XHamsterTopCreatorVideosScreen(
                onBack = { navController.popBackStackSafely() },
                onNavigateToVideo = { code ->
                    navController.navigateSafely(XHamsterVideoRoute(code))
                },
            )
        }

        composable<XHamsterPhotosRoute> {
            XHamsterPhotosScreen(
                onBack = { navController.popBackStackSafely() },
                onOpenGallery = { url ->
                    navController.navigateSafely(XHamsterGalleryRoute(url, ""))
                },
                onOpenAuthor = { path, title ->
                    navController.navigateSafely(XHamsterProfileRoute(path, title))
                },
            )
        }

        composable<XHamsterGalleryRoute> {
            val route = it.toRoute<XHamsterGalleryRoute>()
            XHamsterGalleryScreen(
                url = route.url,
                title = route.title,
                onBack = { navController.popBackStackSafely() },
                onOpenAuthor = { path, title ->
                    if (isProfilePath(path)) {
                        navController.navigateSafely(XHamsterProfileRoute(path, title))
                    } else {
                        navController.navigateSafely(XHamsterCategoryRoute(path, title))
                    }
                },
            )
        }

        composable<XHamsterProfileRoute> {
            val route = it.toRoute<XHamsterProfileRoute>()
            XHamsterProfileScreen(
                path = route.path,
                title = route.title,
                onBack = { navController.popBackStackSafely() },
                onNavigateToVideo = { code ->
                    navController.navigateSafely(XHamsterVideoRoute(code))
                },
                onOpenPath = { path, title ->
                    if (isProfilePath(path)) {
                        navController.navigateSafely(XHamsterProfileRoute(path, title))
                    } else {
                        navController.navigateSafely(XHamsterCategoryRoute(path, title))
                    }
                },
            )
        }

        composable<XHamsterDashboardRoute> {
            XHamsterCreatorDashboardScreen(
                onBack = { navController.popBackStackSafely() },
                onOpenTopVideos = { navController.navigateSafely(XHamsterTopCreatorVideosRoute) },
            )
        }

        composable<XHamsterAccountSettingsRoute> {
            XHamsterAccountSettingsScreen(
                onBack = { navController.popBackStackSafely() },
                onOpenInBrowser = { path ->
                    openInBrowser(activity, XHamsterConstants.listUrl(path))
                },
            )
        }

        registerGlobalSettingsRoutes(
            activity = activity,
            navController = navController,
            homeFallback = XHamsterSettingsHubRoute,
        )
    }
}

private fun openInBrowser(activity: MainActivity, url: String) {
    runCatching {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
