package com.yenaly.han1meviewer.FapFolder.ui.navigation

import android.util.Log
import androidx.navigation.NavHostController
import com.yenaly.han1meviewer.FapFolder.FapFolderSiteType

object FapFolderNavBridge {

    private const val TAG = "FapFolderNavBridge"

    @Volatile
    private var currentNavController: NavHostController? = null

    @Volatile
    private var isActive: Boolean = false

    fun attach(navController: NavHostController) {
        currentNavController = navController
        isActive = true
        Log.d(TAG, "attach: navController bound")
    }

    fun detach() {
        currentNavController = null
        isActive = false
        Log.d(TAG, "detach: navController released")
    }

    fun isActive(): Boolean = isActive && currentNavController != null

    fun navigateToHome() {
        val nav = currentNavController ?: return
        runCatching {
            nav.navigate(FapFolderHomeRoute) { launchSingleTop = true }
        }.onFailure { Log.w(TAG, "navigateToHome failed: ${it.message}") }
    }

    fun navigateToPost(slugOrId: String, autoPlay: Boolean = false) {
        val nav = currentNavController ?: return
        if (slugOrId.isBlank()) return
        runCatching {
            nav.navigate(FapFolderPostRoute(slugOrId = slugOrId, autoPlay = autoPlay)) {
                launchSingleTop = true
            }
        }.onFailure { Log.w(TAG, "navigateToPost failed: ${it.message}") }
    }

    fun navigateToSearch(query: String?, isHashtag: Boolean = false) {
        val nav = currentNavController ?: return
        runCatching {
            nav.navigate(
                FapFolderSearchRoute(
                    query = query,
                    isHashtag = isHashtag,
                )
            ) { launchSingleTop = true }
        }.onFailure { Log.w(TAG, "navigateToSearch failed: ${it.message}") }
    }

    fun navigateToSearchHistory() {
        val nav = currentNavController ?: return
        runCatching {
            nav.navigate(FapFolderSearchHistoryRoute) { launchSingleTop = true }
        }.onFailure { Log.w(TAG, "navigateToSearchHistory failed: ${it.message}") }
    }

    fun navigateToHashtag(tag: String) {
        val nav = currentNavController ?: return
        if (tag.isBlank()) return
        runCatching {
            nav.navigate(FapFolderHashtagRoute(tag = tag.removePrefix("#"))) {
                launchSingleTop = true
            }
        }.onFailure { Log.w(TAG, "navigateToHashtag failed: ${it.message}") }
    }

    fun navigateToGroup(slug: String, tab: String = "posts") {
        val nav = currentNavController ?: return
        if (slug.isBlank()) return
        runCatching {
            nav.navigate(FapFolderGroupRoute(slug = slug, tab = tab)) {
                launchSingleTop = true
            }
        }.onFailure { Log.w(TAG, "navigateToGroup failed: ${it.message}") }
    }

    fun navigateToProfile(username: String, tab: String = "posts") {
        val nav = currentNavController ?: return
        if (username.isBlank()) return
        runCatching {
            nav.navigate(FapFolderProfileRoute(username = username, tab = tab)) {
                launchSingleTop = true
            }
        }.onFailure { Log.w(TAG, "navigateToProfile failed: ${it.message}") }
    }

    fun navigateToAlbum(albumId: String) {
        val nav = currentNavController ?: return
        if (albumId.isBlank()) return
        runCatching {
            nav.navigate(FapFolderAlbumRoute(albumId = albumId)) { launchSingleTop = true }
        }.onFailure { Log.w(TAG, "navigateToAlbum failed: ${it.message}") }
    }

    fun navigateToSettings() {
        val nav = currentNavController ?: return
        runCatching {
            nav.navigate(FapFolderSettingsRoute) { launchSingleTop = true }
        }.onFailure { Log.w(TAG, "navigateToSettings failed: ${it.message}") }
    }

    fun navigateToHistory() {
        val nav = currentNavController ?: return
        runCatching {
            nav.navigate(FapFolderHistoryRoute) { launchSingleTop = true }
        }.onFailure { Log.w(TAG, "navigateToHistory failed: ${it.message}") }
    }

    fun navigateToSaved() {
        val nav = currentNavController ?: return
        runCatching {
            nav.navigate(FapFolderSavedRoute) { launchSingleTop = true }
        }.onFailure { Log.w(TAG, "navigateToSaved failed: ${it.message}") }
    }

    fun navigateToFollowedCommunities() {
        val nav = currentNavController ?: return
        runCatching {
            nav.navigate(FapFolderFollowedCommunitiesRoute) { launchSingleTop = true }
        }.onFailure { Log.w(TAG, "navigateToFollowedCommunities failed: ${it.message}") }
    }

    fun navigateToCookies() {
        val nav = currentNavController ?: return
        runCatching {
            nav.navigate(FapFolderCookiesRoute) { launchSingleTop = true }
        }.onFailure { Log.w(TAG, "navigateToCookies failed: ${it.message}") }
    }

    fun navigateToLogin() {
        val nav = currentNavController ?: return
        runCatching {
            nav.navigate(FapFolderLoginRoute) { launchSingleTop = true }
        }.onFailure { Log.w(TAG, "navigateToLogin failed: ${it.message}") }
    }

    fun navigateToGate() {
        val nav = currentNavController ?: return
        runCatching {
            nav.navigate(FapFolderGateRoute) { launchSingleTop = true }
        }.onFailure { Log.w(TAG, "navigateToGate failed: ${it.message}") }
    }

    fun popBackStack(): Boolean {
        val nav = currentNavController ?: return false
        return runCatching { nav.popBackStack() }.getOrDefault(false)
    }

    fun isOnAnyRoute(): Boolean = currentNavController?.currentBackStackEntry != null

    val siteHostname: String get() = FapFolderSiteType.defaultHostname()
    val siteBaseUrl: String get() = FapFolderSiteType.baseUrl()
}
