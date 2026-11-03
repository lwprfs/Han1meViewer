package com.yenaly.han1meviewer.ui.navigation

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import com.yenaly.han1meviewer.FapFolder.ui.navigation.FapFolderHomeRoute
import com.yenaly.han1meviewer.FapFolder.ui.navigation.FapFolderPostRoute
import com.yenaly.han1meviewer.FapFolder.ui.navigation.FapFolderSearchRoute
import com.yenaly.han1meviewer.HentaiMama.ui.navigation.HentaiMamaHomeRoute
import com.yenaly.han1meviewer.HentaiMama.ui.navigation.HentaiMamaSearchRoute
import com.yenaly.han1meviewer.HentaiMama.ui.navigation.HentaiMamaVideoRoute
import com.yenaly.han1meviewer.OnAir.ui.navigation.OnAirHomeRoute
import com.yenaly.han1meviewer.OnAir.ui.navigation.OnAirSearchRoute
import com.yenaly.han1meviewer.OnAir.ui.navigation.OnAirVideoRoute
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.ui.navigation.main.HomeRoute
import com.yenaly.han1meviewer.ui.navigation.main.SearchRoute
import com.yenaly.han1meviewer.ui.navigation.main.VideoRoute
import com.yenaly.han1meviewer.MissAV.ui.navigation.MissAvHomeRoute
import com.yenaly.han1meviewer.MissAV.ui.navigation.MissAvSearchRoute
import com.yenaly.han1meviewer.MissAV.ui.navigation.MissAvVideoRoute

object NavigationManager {

    private const val TAG = "NavigationManager"

    private const val SWITCH_HOME_DELAY_MS = 300L
    private const val PENDING_OPEN_POLL_MS = 150L
    private const val PENDING_OPEN_MAX_ATTEMPTS = 240

    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentNavController: NavHostController? = null
    private var currentSiteType: SiteType? = null

    private var pendingToken = 0L

    fun initialize(navController: NavHostController, siteType: SiteType) {
        currentNavController = navController
        currentSiteType = siteType
        Log.d(TAG, "Initialized with site: $siteType")
    }

    fun getCurrentSiteType(): SiteType = currentSiteType ?: Preferences.siteType

    fun getNavController(): NavHostController? = currentNavController

    fun navigateToVideo(
        videoCode: String,
        path: String? = null,
        resumePosition: Long = 0L,
        autoPlay: Boolean = false,
    ) {
        currentNavController?.let { navController ->
            try {
                when (getCurrentSiteType()) {
                    SiteType.HANIME, SiteType.JAVCHU -> {
                        navController.navigate(VideoRoute(videoCode))
                    }
                    SiteType.MISSAV -> {
                        navController.navigate(MissAvVideoRoute(videoCode, path ?: "/en/$videoCode"))
                    }
                    SiteType.HENTAIMAMA -> {
                        navController.navigate(
                            HentaiMamaVideoRoute(
                                videoCode = videoCode,
                                path = path ?: "/$videoCode",
                                resumePosition = resumePosition,
                            )
                        )
                    }
                    SiteType.ONAIR -> {
                        navController.navigate(OnAirVideoRoute(videoId = videoCode))
                    }
                    SiteType.FAPFOLDER -> {
                        navController.navigate(
                            FapFolderPostRoute(slugOrId = path ?: videoCode, autoPlay = autoPlay)
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "navigateToVideo failed: ${e.message}")
            }
        }
    }

    fun navigateToSearch(query: String? = null) {
        currentNavController?.let { navController ->
            try {
                when (getCurrentSiteType()) {
                    SiteType.HANIME, SiteType.JAVCHU -> {
                        navController.navigate(SearchRoute(query))
                    }
                    SiteType.MISSAV -> {
                        navController.navigate(MissAvSearchRoute(query))
                    }
                    SiteType.HENTAIMAMA -> {
                        navController.navigate(HentaiMamaSearchRoute(query))
                    }
                    SiteType.ONAIR -> {
                        navController.navigate(OnAirSearchRoute(query = query))
                    }
                    SiteType.FAPFOLDER -> {
                        navController.navigate(FapFolderSearchRoute(query = query))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "navigateToSearch failed: ${e.message}")
            }
        }
    }

    fun navigateToHome() {
        currentNavController?.let { navController ->
            try {
                val site = getCurrentSiteType()
                Log.d(TAG, "navigateToHome called for site: $site")

                if (isAtHome(site)) return
                when (site) {
                    SiteType.HANIME, SiteType.JAVCHU -> {
                        navController.popBackStack(0, false)
                        navController.navigate(HomeRoute)
                    }
                    SiteType.MISSAV -> {
                        navController.popBackStack(0, false)
                        navController.navigate(MissAvHomeRoute)
                    }
                    SiteType.HENTAIMAMA -> {
                        navController.popBackStack(0, false)
                        navController.navigate(HentaiMamaHomeRoute)
                    }
                    SiteType.ONAIR -> {
                        navController.popBackStack(0, false)
                        navController.navigate(OnAirHomeRoute)
                    }
                    SiteType.FAPFOLDER -> {
                        navController.popBackStack(0, false)
                        navController.navigate(FapFolderHomeRoute)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "navigateToHome failed: ${e.message}")
                navController.popBackStack(0, false)
            }
        }
    }

    fun navigateBack() {
        currentNavController?.popBackStackSafely()
    }

    fun clearBackStack() {
        currentNavController?.popBackStack(0, false)
    }

    fun switchSite(newSiteType: SiteType, thenOpen: PendingVideoOpen? = null) {
        Log.d(TAG, "switchSite called: $newSiteType (pending open: ${thenOpen?.videoCode})")
        currentSiteType = newSiteType
        Preferences.siteType = newSiteType
        pendingToken++
        currentNavController?.popBackStack(0, false)
        mainHandler.postDelayed({
            navigateToHome()
            if (thenOpen != null) schedulePendingOpen(thenOpen)
        }, SWITCH_HOME_DELAY_MS)
    }

    private fun schedulePendingOpen(request: PendingVideoOpen) {
        val token = ++pendingToken
        var attempts = 0
        val poll = object : Runnable {
            override fun run() {
                if (token != pendingToken) return
                if (getCurrentSiteType() != request.site) {
                    Log.d(TAG, "Pending open dropped: site changed again")
                    return
                }
                if (isAtHome(request.site) && isNavigationReady()) {
                    pendingToken++
                    navigateToVideo(
                        request.videoCode,
                        request.path,
                        request.resumePosition,
                        request.autoPlay,
                    )
                    return
                }
                if (++attempts >= PENDING_OPEN_MAX_ATTEMPTS) {
                    Log.w(TAG, "Pending open timed out for ${request.videoCode}")
                    return
                }
                mainHandler.postDelayed(this, PENDING_OPEN_POLL_MS)
            }
        }
        mainHandler.postDelayed(poll, PENDING_OPEN_POLL_MS)
    }

    private fun isNavigationReady(): Boolean {
        val entry = currentNavController?.currentBackStackEntry ?: return false
        return entry.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    private fun isAtHome(site: SiteType): Boolean {
        val destination = currentNavController?.currentDestination ?: return false
        return try {
            when (site) {
                SiteType.HANIME, SiteType.JAVCHU -> destination.hasRoute(HomeRoute::class)
                SiteType.MISSAV -> destination.hasRoute(MissAvHomeRoute::class)
                SiteType.HENTAIMAMA -> destination.hasRoute(HentaiMamaHomeRoute::class)
                SiteType.ONAIR -> destination.hasRoute(OnAirHomeRoute::class)
                SiteType.FAPFOLDER -> destination.hasRoute(FapFolderHomeRoute::class)
            }
        } catch (e: Exception) {
            false
        }
    }
}
