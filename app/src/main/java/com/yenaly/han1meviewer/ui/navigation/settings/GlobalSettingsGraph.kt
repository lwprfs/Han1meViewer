package com.yenaly.han1meviewer.ui.navigation.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.navigation.PendingVideoOpen
import com.yenaly.han1meviewer.ui.navigation.main.DailyCheckInRoute
import com.yenaly.han1meviewer.ui.navigation.main.DailyCheckInRouteScreen
import com.yenaly.han1meviewer.ui.navigation.navigateSafely
import com.yenaly.han1meviewer.ui.screen.history.UnifiedHistoryScreen
import kotlinx.serialization.Serializable

@Serializable
object UnifiedHistoryRoute

fun NavGraphBuilder.registerGlobalSettingsRoutes(
    activity: MainActivity,
    navController: NavHostController,
    homeFallback: Any,
) {
    val openHanimeVideo: (String) -> Unit = { code ->
        activity.requestCrossSiteOpen(PendingVideoOpen(site = SiteType.HANIME, videoCode = code))
    }

    composable<HomeSettingsRoute> {
        SettingsScaffold(
            navController = navController,
            fallbackDestination = homeFallback,
        ) {
            HomeSettingsRouteScreen(
                activity = activity,
                onNavigateToPlayerSettings = { navController.navigateSafely(PlayerSettingsRoute) },
                onNavigateToHKeyframeSettings = { navController.navigateSafely(HKeyframeSettingsRoute) },
                onNavigateToDownloadSettings = { navController.navigateSafely(DownloadSettingsRoute) },
                onNavigateToNetworkSettings = { navController.navigateSafely(NetworkSettingsRoute) },
                onNavigateToUnifiedHistory = { navController.navigateSafely(UnifiedHistoryRoute) },
            )
        }
    }
    composable<PlayerSettingsRoute> {
        SettingsScaffold(
            navController = navController,
            fallbackDestination = HomeSettingsRoute,
        ) {
            PlayerSettingsRouteScreen(
                onNavigateToMpvSettings = { navController.navigateSafely(MpvPlayerSettingsRoute) },
            )
        }
    }
    composable<NetworkSettingsRoute> {
        SettingsScaffold(
            navController = navController,
            fallbackDestination = HomeSettingsRoute,
        ) {
            NetworkSettingsRouteScreen()
        }
    }
    composable<DownloadSettingsRoute> {
        SettingsScaffold(
            navController = navController,
            fallbackDestination = HomeSettingsRoute,
        ) {
            DownloadSettingsRouteScreen(activity = activity)
        }
    }
    composable<MpvPlayerSettingsRoute> {
        SettingsScaffold(
            navController = navController,
            fallbackDestination = PlayerSettingsRoute,
        ) {
            MpvPlayerSettingsRouteScreen()
        }
    }
    composable<HKeyframesRoute> {
        var showImportDialog by remember { mutableStateOf(false) }
        SettingsScaffold(
            navController = navController,
            fallbackDestination = HKeyframeSettingsRoute,
            actions = {
                HKeyframesTopBarActions(onImportClick = { showImportDialog = true })
            },
        ) {
            HKeyframesRouteScreen(
                onOpenVideo = openHanimeVideo,
                showImportDialog = showImportDialog,
                onImportDialogDismiss = { showImportDialog = false },
            )
        }
    }
    composable<SharedHKeyframesRoute> {
        SettingsScaffold(
            navController = navController,
            fallbackDestination = HKeyframeSettingsRoute,
        ) {
            SharedHKeyframesRouteScreen(
                onOpenVideo = openHanimeVideo,
            )
        }
    }
    composable<HKeyframeSettingsRoute> {
        SettingsScaffold(
            navController = navController,
            fallbackDestination = HomeSettingsRoute,
        ) {
            HKeyframeSettingsRouteScreen(
                onNavigateToHKeyframes = { navController.navigateSafely(HKeyframesRoute) },
                onNavigateToSharedHKeyframes = { navController.navigateSafely(SharedHKeyframesRoute) },
            )
        }
    }
    registerUnifiedHistoryRoute(activity, navController)

    composable<DailyCheckInRoute> {
        DailyCheckInRouteScreen(
            activity = activity,
            onBack = { navController.popBackStack() },
            onNavigateToVideo = openHanimeVideo,
        )
    }
}

fun NavGraphBuilder.registerUnifiedHistoryRoute(
    activity: MainActivity,
    navController: NavHostController,
) {
    composable<UnifiedHistoryRoute> {
        UnifiedHistoryScreen(
            activity = activity,
            onBack = { navController.popBackStack() },
        )
    }
}
