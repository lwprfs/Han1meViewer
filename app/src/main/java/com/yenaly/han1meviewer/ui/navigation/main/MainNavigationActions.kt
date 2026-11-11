package com.yenaly.han1meviewer.ui.navigation.main

import android.content.Intent
import androidx.navigation.NavHostController
import com.yenaly.han1meviewer.ui.navigation.navigateSafely
import com.yenaly.han1meviewer.ui.navigation.settings.HomeSettingsRoute
import kotlinx.serialization.json.Json

private val loginRequiredDrawerItems = setOf(
    MainDrawerDestination.FavVideo,
    MainDrawerDestination.WatchLater,
    MainDrawerDestination.Playlist,
    MainDrawerDestination.Subscription,
)

fun NavHostController.navigateDrawerDestination(
    destination: MainDrawerDestination,
    isLoggedIn: Boolean,
    onRequireLogin: () -> Unit,
): Boolean {
    if (destination in loginRequiredDrawerItems && !isLoggedIn) {
        onRequireLogin()
        return false
    }

    when (destination) {
        MainDrawerDestination.Home -> {

            if (!popBackStack(HomeRoute, inclusive = false)) {
                navigateSafely(HomeRoute) { launchSingleTop = true }
            }
        }
        MainDrawerDestination.Settings -> navigateSafely(HomeSettingsRoute) { launchSingleTop = true }
        MainDrawerDestination.DailyCheckIn -> navigateSafely(DailyCheckInRoute) { launchSingleTop = true }
        MainDrawerDestination.WatchLater -> navigateSafely(MyWatchLaterRoute) { launchSingleTop = true }
        MainDrawerDestination.FavVideo -> navigateSafely(MyFavVideoRoute) { launchSingleTop = true }
        MainDrawerDestination.Playlist -> navigateSafely(MyPlaylistRoute) { launchSingleTop = true }
        MainDrawerDestination.Subscription -> navigateSafely(SubscriptionRoute) { launchSingleTop = true }
        MainDrawerDestination.CreatorCenter -> navigateSafely(CreatorCenterRoute) { launchSingleTop = true }
        MainDrawerDestination.WatchHistory -> navigateSafely(WatchHistoryRoute) { launchSingleTop = true }
        MainDrawerDestination.Download -> navigateSafely(DownloadRoute) { launchSingleTop = true }
    }
    return true
}

fun NavHostController.handleMainIntent(intent: Intent) {
    if (intent.action == com.yenaly.han1meviewer.worker.HUpdateWorker.ACTION_OPEN_UPDATE_STATUS) {
        intent.action = null
        navigateSafely(com.yenaly.han1meviewer.ui.navigation.settings.UpdateSettingsRoute)
        return
    }
    if (intent.action == Intent.ACTION_VIEW) {
        val uri = intent.data ?: return
        when (uri.scheme) {
            "http", "https" -> {
                val videoCode = uri.getQueryParameter("v")
                if (videoCode != null) {
                    navigateSafely(VideoRoute(videoCode))
                }
            }

            "file", "content" -> {
                navigateSafely(VideoRoute("-1", uri.toString()))
            }
        }
        return
    }

    intent.getStringExtra("startSearchFromTag")?.let { tag ->
        intent.removeExtra("startSearchFromTag")
        navigateSafely(SearchRoute(query = tag))
        return
    }

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    val map = intent.getSerializableExtra("startSearchFromMap") as? HashMap<String, String>
    if (map != null) {
        intent.removeExtra("startSearchFromMap")
        navigateSafely(SearchRoute(advancedSearchJson = Json.encodeToString(map)))
        return
    }

    val videoCode = intent.getStringExtra("startVideoCode")
    if (!videoCode.isNullOrEmpty()) {
        intent.removeExtra("startVideoCode")
        navigateSafely(VideoRoute(videoCode))
    }
}
