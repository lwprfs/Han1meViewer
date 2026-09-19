package com.yenaly.han1meviewer.MissAV.ui.navigation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.navigation.NavigationManager
import com.yenaly.han1meviewer.ui.navigation.navigateSafely
import com.yenaly.han1meviewer.ui.navigation.main.MainDestinationSpec
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import com.yenaly.han1meviewer.MissAV.ui.home.MissAvHomeScreen
import com.yenaly.han1meviewer.MissAV.ui.home.MissAvSettingsHubScreen
import com.yenaly.han1meviewer.MissAV.ui.home.MissAvHomeSettingsScreen
import com.yenaly.han1meviewer.MissAV.ui.home.MissAvHostSettingsScreen
import com.yenaly.han1meviewer.MissAV.ui.search.MissAvSearchScreen
import com.yenaly.han1meviewer.MissAV.ui.video.MissAvVideoScreen
import com.yenaly.han1meviewer.MissAV.ui.history.MissAvHistoryScreen

import com.yenaly.han1meviewer.MissAV.ui.home.MissAvHomeViewModel
import com.yenaly.han1meviewer.MissAV.viewmodel.MissAvViewModel
@Serializable
object MissAvHomeRoute

@Serializable
object MissAvSettingsHubRoute

@Serializable
object MissAvHomeCategoriesRoute

@Serializable
object MissAvHostSettingsRoute

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
    LaunchedEffect(Unit) {
        delay(50)
        NavigationManager.initialize(navController, com.yenaly.han1meviewer.SiteType.MISSAV)
    }

    val sharedMissAvViewModel: MissAvViewModel = viewModel()
    val sharedHomeViewModel: MissAvHomeViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = MissAvHomeRoute,
        modifier = modifier,
    ) {
        composable<MissAvHomeRoute> {
            Box(modifier = Modifier.padding(contentPadding)) {
                MissAvHomeScreen(
                    onNavigateToVideo = { code, path ->
                        navController.navigateSafely(MissAvVideoRoute(code, path))
                    },
                    onNavigateToSearch = { query ->
                        if (!query.isNullOrBlank()) {
                            sharedMissAvViewModel.setSearchQuery(query)
                        }
                        navController.navigateSafely(MissAvSearchRoute(query))
                    },
                    onSwitchSite = { activity.requestSiteSwitch() },
                    onNavigateToHistory = {
                        navController.navigateSafely(MissAvHistoryRoute)
                    },
                    onNavigateToSettings = {
                        navController.navigateSafely(MissAvSettingsHubRoute)
                    },
                    viewModel = sharedHomeViewModel,
                )
            }
        }

        composable<MissAvSettingsHubRoute> {
            MissAvSettingsHubScreen(
                onBack = { navController.popBackStack() },
                onNavigateToHomeCategories = {
                    navController.navigateSafely(MissAvHomeCategoriesRoute)
                },
                onNavigateToHost = {
                    navController.navigateSafely(MissAvHostSettingsRoute)
                },
            )
        }

        composable<MissAvHomeCategoriesRoute> {
            MissAvHomeSettingsScreen(
                viewModel = sharedHomeViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable<MissAvHostSettingsRoute> {
            MissAvHostSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable<MissAvSearchRoute> {
            val route = it.toRoute<MissAvSearchRoute>()
            MissAvSearchScreen(
                initialQuery = route.query,
                onBack = { navController.popBackStack() },
                onNavigateToVideo = { code, path ->
                    navController.navigateSafely(MissAvVideoRoute(code, path))
                },
                viewModel = sharedMissAvViewModel,
            )
        }

        composable<MissAvVideoRoute> {
            val route = it.toRoute<MissAvVideoRoute>()
            MissAvVideoScreen(
                videoCode = route.videoCode,
                path = route.path,
                onBack = { navController.popBackStack() },
                onNavigateToVideo = { code, path ->
                    navController.navigateSafely(MissAvVideoRoute(code, "/$code"))
                },
                onNavigateToSearch = { query ->
                    if (!query.isNullOrBlank()) {
                        sharedMissAvViewModel.setSearchQuery(query)
                    }
                    navController.navigateSafely(MissAvSearchRoute(query))
                },
                viewModel = sharedMissAvViewModel,
            )
        }

        composable<MissAvHistoryRoute> {
            MissAvHistoryScreen(
                onNavigateToVideo = { code, path ->
                    navController.navigateSafely(MissAvVideoRoute(code, path))
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
