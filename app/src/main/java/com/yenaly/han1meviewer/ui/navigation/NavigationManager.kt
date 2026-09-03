// app/src/main/java/com/yenaly/han1meviewer/ui/navigation/NavigationManager.kt
package com.yenaly.han1meviewer.ui.navigation

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.navigation.NavHostController
import com.yenaly.han1meviewer.HentaiMama.HentaiMamaHomeRoute
import com.yenaly.han1meviewer.HentaiMama.HentaiMamaSearchRoute
import com.yenaly.han1meviewer.HentaiMama.HentaiMamaVideoRoute
import com.yenaly.han1meviewer.MissAV.MissAvHomeRoute
import com.yenaly.han1meviewer.MissAV.MissAvSearchRoute
import com.yenaly.han1meviewer.MissAV.MissAvVideoRoute
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.ui.navigation.main.HomeRoute
import com.yenaly.han1meviewer.ui.navigation.main.SearchRoute
import com.yenaly.han1meviewer.ui.navigation.main.VideoRoute

object NavigationManager {
    
    private const val TAG = "NavigationManager"
    
    private var currentNavController: NavHostController? = null
    private var currentSiteType: SiteType? = null
    
    fun initialize(navController: NavHostController, siteType: SiteType) {
        currentNavController = navController
        currentSiteType = siteType
        Log.d(TAG, "Initialized with site: $siteType")
    }
    
    fun getCurrentSiteType(): SiteType = currentSiteType ?: Preferences.siteType
    
    fun getNavController(): NavHostController? = currentNavController
    
    fun navigateToVideo(videoCode: String, path: String? = null) {
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
                        navController.navigate(HentaiMamaVideoRoute(videoCode, path ?: "/$videoCode"))
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
                }
            } catch (e: Exception) {
                Log.e(TAG, "navigateToSearch failed: ${e.message}")
            }
        }
    }
    
    fun navigateToHome() {
        currentNavController?.let { navController ->
            try {
                Log.d(TAG, "navigateToHome called for site: ${getCurrentSiteType()}")
                when (getCurrentSiteType()) {
                    SiteType.HANIME, SiteType.JAVCHU -> {
                        // Clear everything and navigate to HomeRoute
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
                }
            } catch (e: Exception) {
                Log.e(TAG, "navigateToHome failed: ${e.message}")
                // Fallback: try to pop back to start
                navController.popBackStack(0, false)
            }
        }
    }
    
    fun navigateBack() {
        currentNavController?.popBackStack()
    }
    
    fun clearBackStack() {
        currentNavController?.popBackStack(0, false)
    }
    
    fun switchSite(newSiteType: SiteType) {
        Log.d(TAG, "switchSite called: $newSiteType")
        currentSiteType = newSiteType
        Preferences.siteType = newSiteType
        
        // Clear back stack when switching sites
        currentNavController?.popBackStack(0, false)
        
        // Navigate to home with a delay to ensure the graph is ready
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToHome()
        }, 300)
    }
}