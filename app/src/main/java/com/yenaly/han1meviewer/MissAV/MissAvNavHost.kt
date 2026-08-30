package com.yenaly.han1meviewer.MissAV

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.navigation.navigateSafely
import com.yenaly.han1meviewer.ui.navigation.main.MainDestinationSpec
import kotlinx.serialization.Serializable

@Serializable
object MissAvHomeRoute

@Serializable
data class MissAvSearchRoute(val query: String? = null)

@Serializable
data class MissAvVideoRoute(val videoCode: String, val path: String)

@Serializable
object MissAvHistoryRoute

@Composable
fun MissAvNavHost(
    activity: MainActivity,
    navController: NavHostController,
    isDrawerOpen: Boolean,
    onOpenDrawer: () -> Unit,
    onDestinationChanged: (MainDestinationSpec) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    NavHost(
        navController = navController,
        startDestination = MissAvHomeRoute,
        modifier = modifier,
    ) {
        composable<MissAvHomeRoute> {
            // Only apply padding to home screen
            Box(modifier = Modifier.padding(contentPadding)) {
                MissAvHomeScreen(
                    onNavigateToVideo = { code, path ->
                        navController.navigateSafely(MissAvVideoRoute(code, path))
                    },
                    onNavigateToSearch = { query ->
                        navController.navigateSafely(MissAvSearchRoute(query))
                    },
                )
            }
        }
        composable<MissAvSearchRoute> {
            val route = it.toRoute<MissAvSearchRoute>()
            // No padding wrapper - search screen has its own internal padding
            MissAvSearchScreen(
                initialQuery = route.query,
                onBack = { navController.popBackStack() },
                onNavigateToVideo = { code, path ->
                    navController.navigateSafely(MissAvVideoRoute(code, path))
                },
            )
        }
        composable<MissAvVideoRoute> {
            val route = it.toRoute<MissAvVideoRoute>()
            // No padding wrapper - video screen has its own Scaffold
            MissAvVideoScreen(
                videoCode = route.videoCode,
                path = route.path,
                onBack = { navController.popBackStack() },
                onNavigateToVideo = { code, path ->
                    navController.navigateSafely(MissAvVideoRoute(code, "/$code"))
                },
                onNavigateToSearch = { query ->
                    navController.navigateSafely(MissAvSearchRoute(query))
                },
            )
        }
        composable<MissAvHistoryRoute> {
            // No padding wrapper - history screen has its own Scaffold
            MissAvHistoryScreen(
                onNavigateToVideo = { code, path ->
                    navController.navigateSafely(MissAvVideoRoute(code, path))
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}