package com.yenaly.han1meviewer.HentaiMama

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.navigation.NavigationManager
import com.yenaly.han1meviewer.ui.navigation.canNavigateSafely
import com.yenaly.han1meviewer.ui.navigation.navigateSafely
import com.yenaly.han1meviewer.ui.navigation.main.MainDestinationSpec
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable
object HentaiMamaHomeRoute

@Serializable
data class HentaiMamaSearchRoute(val query: String? = null)

@Serializable
data class HentaiMamaVideoRoute(val videoCode: String, val path: String)

@Composable
fun HentaiMamaNavHost(
    activity: MainActivity,
    navController: NavHostController,
    isDrawerOpen: Boolean,
    onOpenDrawer: () -> Unit,
    onDestinationChanged: (MainDestinationSpec) -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(50)
        NavigationManager.initialize(navController, SiteType.HENTAIMAMA)
    }

    NavHost(
        navController = navController,
        startDestination = HentaiMamaHomeRoute,
    ) {
        composable<HentaiMamaHomeRoute> {
            HentaiMamaHomeScreen(
                onNavigateToVideo = { code ->
                    if (navController.canNavigateSafely()) {
                        val path = HentaiMamaNetwork.normalizeUrl("/$code")
                        navController.navigateSafely(HentaiMamaVideoRoute(code, path))
                    }
                },
                onNavigateToSearch = { query ->
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(HentaiMamaSearchRoute(query))
                    }
                },
                onSwitchSite = {
                    activity.requestSiteSwitch()
                },
                onNavigateToSettings = {

                },
            )
        }

        composable<HentaiMamaSearchRoute> {
            val route = it.toRoute<HentaiMamaSearchRoute>()
            HentaiMamaSearchScreen(
                initialQuery = route.query,
                onBack = { navController.popBackStack() },
                onNavigateToVideo = { code ->
                    if (navController.canNavigateSafely()) {
                        val path = HentaiMamaNetwork.normalizeUrl("/$code")
                        navController.navigateSafely(HentaiMamaVideoRoute(code, path))
                    }
                },
            )
        }

        composable<HentaiMamaVideoRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<HentaiMamaVideoRoute>()
            HentaiMamaVideoScreen(
                videoCode = route.videoCode,
                path = route.path,
                onBack = { navController.popBackStack() },
                onNavigateToVideo = { code, path ->
                    if (code != route.videoCode && navController.canNavigateSafely()) {
                        navController.navigateSafely(HentaiMamaVideoRoute(code, path))
                    }
                },
                onNavigateToSearch = { query ->
                    if (navController.canNavigateSafely()) {
                        navController.navigateSafely(HentaiMamaSearchRoute(query))
                    }
                },
            )
        }
    }
}
