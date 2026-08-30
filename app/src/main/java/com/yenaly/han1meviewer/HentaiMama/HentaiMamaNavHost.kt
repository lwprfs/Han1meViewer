package com.yenaly.han1meviewer.HentaiMama

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.navigation.navigateSafely
import com.yenaly.han1meviewer.ui.navigation.main.MainDestinationSpec
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
    NavHost(
        navController = navController,
        startDestination = HentaiMamaHomeRoute,
    ) {
        composable<HentaiMamaHomeRoute> {
            HentaiMamaHomeScreen(
                onNavigateToVideo = { code ->
                    // Navigate to video with proper path
                    navController.navigateSafely(HentaiMamaVideoRoute(code, "/$code"))
                },
                onNavigateToSearch = { query ->
                    navController.navigateSafely(HentaiMamaSearchRoute(query))
                },
            )
        }
        composable<HentaiMamaSearchRoute> {
            val route = it.toRoute<HentaiMamaSearchRoute>()
            HentaiMamaSearchScreen(
                initialQuery = route.query,
                onBack = { navController.popBackStack() },
                onNavigateToVideo = { code ->
                    navController.navigateSafely(HentaiMamaVideoRoute(code, "/$code"))
                },
            )
        }
        composable<HentaiMamaVideoRoute> {
            val route = it.toRoute<HentaiMamaVideoRoute>()
            HentaiMamaVideoScreen(
                videoCode = route.videoCode,
                path = route.path,
                onBack = { 
                    navController.popBackStack() 
                },
                onNavigateToVideo = { code ->
                    // IMPORTANT: Pop the current video screen before navigating to a new one
                    // This prevents back stack issues
                    navController.popBackStack()
                    // Navigate to the new episode
                    navController.navigateSafely(HentaiMamaVideoRoute(code, "/$code"))
                },
                onNavigateToSearch = { query ->
                    navController.navigateSafely(HentaiMamaSearchRoute(query))
                },
            )
        }
    }
}
