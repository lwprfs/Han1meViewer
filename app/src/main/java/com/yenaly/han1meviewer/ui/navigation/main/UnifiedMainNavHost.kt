// app/src/main/java/com/yenaly/han1meviewer/ui/navigation/main/UnifiedMainNavHost.kt
package com.yenaly.han1meviewer.ui.navigation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import android.util.Log
import androidx.navigation.NavHostController
import com.yenaly.han1meviewer.HentaiMama.HentaiMamaNavHost
import com.yenaly.han1meviewer.MissAV.MissAvNavHost
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.navigation.NavigationManager
import kotlinx.coroutines.delay

@Composable
fun UnifiedMainNavHost(
    activity: MainActivity,
    navController: NavHostController,
    isDrawerOpen: Boolean,
    onOpenDrawer: () -> Unit,
    onDestinationChanged: (MainDestinationSpec) -> Unit,
    siteChangeKey: Long = 0L,
) {
    val siteType = Preferences.siteType
    
    Log.d("UnifiedMainNavHost", "Current siteType: $siteType, baseUrl: ${Preferences.baseUrl}")
    
    key(siteChangeKey) {
        LaunchedEffect(siteType, siteChangeKey) {
            Log.d("UnifiedMainNavHost", "Initializing for site: $siteType, key: $siteChangeKey")
            NavigationManager.initialize(navController, siteType)
            delay(50)
        }

        when (siteType) {
            // Javchu and Hanime BOTH use MainNavHost
            SiteType.HANIME, SiteType.JAVCHU -> {
                MainNavHost(
                    activity = activity,
                    navController = navController,
                    isDrawerOpen = isDrawerOpen,
                    onOpenDrawer = onOpenDrawer,
                    onDestinationChanged = onDestinationChanged,
                )
            }
            SiteType.MISSAV -> {
                MissAvNavHost(
                    activity = activity,
                    navController = navController,
                    isDrawerOpen = isDrawerOpen,
                    onOpenDrawer = onOpenDrawer,
                    onDestinationChanged = onDestinationChanged,
                )
            }
            SiteType.HENTAIMAMA -> {
                HentaiMamaNavHost(
                    activity = activity,
                    navController = navController,
                    isDrawerOpen = isDrawerOpen,
                    onOpenDrawer = onOpenDrawer,
                    onDestinationChanged = onDestinationChanged,
                )
            }
        }
    }
}