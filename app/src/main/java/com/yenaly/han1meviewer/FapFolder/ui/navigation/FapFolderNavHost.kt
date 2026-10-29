package com.yenaly.han1meviewer.FapFolder.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderAuthRepo
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetwork
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderProbeFailure
import com.yenaly.han1meviewer.FapFolder.ui.album.FapFolderAlbumScreen
import com.yenaly.han1meviewer.FapFolder.ui.gate.FapFolderGateState
import com.yenaly.han1meviewer.FapFolder.ui.group.FapFolderGroupScreen
import com.yenaly.han1meviewer.FapFolder.ui.hashtag.FapFolderHashtagScreen
import com.yenaly.han1meviewer.FapFolder.ui.history.FapFolderHistoryScreen
import com.yenaly.han1meviewer.FapFolder.ui.home.FapFolderHomeScreen
import com.yenaly.han1meviewer.FapFolder.ui.home.FapFolderHomeViewModel
import com.yenaly.han1meviewer.FapFolder.ui.login.FapFolderLoginScreen
import com.yenaly.han1meviewer.FapFolder.ui.post.FapFolderPostDetailScreen
import com.yenaly.han1meviewer.FapFolder.ui.profile.FapFolderProfileScreen
import com.yenaly.han1meviewer.FapFolder.ui.search.FapFolderSearchHistoryScreen
import com.yenaly.han1meviewer.FapFolder.ui.search.FapFolderSearchScreen
import com.yenaly.han1meviewer.FapFolder.ui.settings.FapFolderCookiesScreen
import com.yenaly.han1meviewer.FapFolder.ui.settings.FapFolderSettingsHubScreen
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.navigation.NavigationManager
import com.yenaly.han1meviewer.ui.navigation.navigateSafely
import com.yenaly.han1meviewer.ui.navigation.main.MainDestinationSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@Composable
fun FapFolderNavHost(
    activity: MainActivity,
    navController: NavHostController,
    isDrawerOpen: Boolean,
    onOpenDrawer: () -> Unit,
    onDestinationChanged: (MainDestinationSpec) -> Unit,
) {
    var gateState by rememberSaveable(stateSaver = FapFolderGateState.Saver) {
        mutableStateOf<FapFolderGateState>(FapFolderGateState.Initializing)
    }
    var retryTick by rememberSaveable { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        FapFolderNetwork.init(activity.applicationContext)
        NavigationManager.initialize(navController, SiteType.FAPFOLDER)
    }

    LaunchedEffect(retryTick) {
        gateState = FapFolderGateState.Probing
        delay(16L)

        if (!FapFolderNetwork.hasAuthCookies()) {
            gateState = FapFolderGateState.Passed
            return@LaunchedEffect
        }

        val probe = runCatching { FapFolderAuthRepo.probe(activity).first() }
            .getOrNull()

        gateState = when {
            probe == null -> FapFolderGateState.Passed
            probe.ok -> FapFolderGateState.Passed
            probe.failure == FapFolderProbeFailure.NoCookies -> FapFolderGateState.Passed
            probe.suggestRelogin || probe.failure == FapFolderProbeFailure.SessionExpired ->
                FapFolderGateState.ContentError(
                    reason = probe.userMessage ?: "Session expired. Please re-authenticate.",
                    suggestRelogin = true,
                )
            probe.failure == FapFolderProbeFailure.CloudflareChallenge ->
                FapFolderGateState.ContentError(
                    reason = probe.userMessage ?: "Cloudflare challenge. Wait and retry.",
                    suggestWait = true,
                )
            probe.failure == FapFolderProbeFailure.SiteDown ->
                FapFolderGateState.ContentError(
                    reason = probe.userMessage ?: "FapFolder appears to be down.",
                    suggestSwitchSite = true,
                )
            else -> FapFolderGateState.Passed
        }
    }

    DisposableEffect(navController) {
        FapFolderNavBridge.attach(navController)
        onDispose { FapFolderNavBridge.detach() }
    }

    val state = gateState

    when (state) {
        is FapFolderGateState.Initializing -> {
            GatePlaceholder("Preparing…")
            return
        }
        is FapFolderGateState.Probing -> {
            GatePlaceholder("Validating session…")
            return
        }
        is FapFolderGateState.ContentError -> {
            GateErrorHost(
                navController = navController,
                state = state,
                onRetry = { retryTick = System.currentTimeMillis() },
            )
            return
        }
        is FapFolderGateState.Passed -> Unit
    }

    val homeViewModel: FapFolderHomeViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = FapFolderHomeRoute,
    ) {
        composable<FapFolderHomeRoute> {
            FapFolderHomeScreen(
                onOpenPost = { slug -> navController.navigateSafely(FapFolderPostRoute(slug)) },
                onOpenSearch = { query ->
                    navController.navigateSafely(
                        FapFolderSearchRoute(
                            query = query,
                            isHashtag = query?.startsWith("#") == true,
                        )
                    )
                },
                onOpenUser = { username ->
                    navController.navigateSafely(FapFolderProfileRoute(username))
                },
                onOpenHashtag = { tag ->
                    navController.navigateSafely(FapFolderHashtagRoute(tag))
                },
                onOpenGroup = { slug ->
                    navController.navigateSafely(FapFolderGroupRoute(slug))
                },
                onOpenAlbum = { albumId ->
                    navController.navigateSafely(FapFolderAlbumRoute(albumId))
                },
                onOpenHistory = { navController.navigateSafely(FapFolderHistoryRoute) },
                onOpenSettings = { navController.navigateSafely(FapFolderSettingsRoute) },
                onSwitchSite = {
                    activity.onBackPressedDispatcher.onBackPressed()
                },
                onSessionExpired = {
                    retryTick = System.currentTimeMillis()
                },
                onLoadFailure = { },
                viewModel = homeViewModel,
            )
        }

        composable<FapFolderPostRoute> { entry ->
            val route = entry.toRoute<FapFolderPostRoute>()
            FapFolderPostDetailScreen(
                slugOrId = route.slugOrId,
                autoPlay = route.autoPlay,
                onBack = { navController.popBackStack() },
                onOpenHashtag = { tag ->
                    navController.navigateSafely(FapFolderHashtagRoute(tag))
                },
                onOpenGroup = { slug ->
                    navController.navigateSafely(FapFolderGroupRoute(slug))
                },
                onOpenProfile = { username ->
                    navController.navigateSafely(FapFolderProfileRoute(username))
                },
                onOpenAlbum = { albumId ->
                    navController.navigateSafely(FapFolderAlbumRoute(albumId))
                },
                onOpenVideo = { slugOrId ->
                    navController.navigateSafely(FapFolderPostRoute(slugOrId, autoPlay = true))
                },
            )
        }

        composable<FapFolderSearchRoute> { entry ->
            val route = entry.toRoute<FapFolderSearchRoute>()
            FapFolderSearchScreen(
                initialQuery = route.query,
                initialHashtag = route.isHashtag,
                initialTab = route.tab,
                onBack = { navController.popBackStack() },
                onOpenPost = { slug -> navController.navigateSafely(FapFolderPostRoute(slug)) },
                onOpenHashtag = { tag -> navController.navigateSafely(FapFolderHashtagRoute(tag)) },
                onOpenGroup = { slug -> navController.navigateSafely(FapFolderGroupRoute(slug)) },
                onOpenProfile = { username ->
                    navController.navigateSafely(FapFolderProfileRoute(username))
                },
            )
        }

        composable<FapFolderSearchHistoryRoute> {
            FapFolderSearchHistoryScreen(
                onBack = { navController.popBackStack() },
                onRunSearch = { query, isHashtag, tab ->
                    if (isHashtag) {
                        navController.navigateSafely(FapFolderHashtagRoute(query))
                    } else {
                        navController.navigateSafely(
                            FapFolderSearchRoute(query = query, isHashtag = false, tab = tab)
                        )
                    }
                },
            )
        }

        composable<FapFolderHashtagRoute> { entry ->
            val route = entry.toRoute<FapFolderHashtagRoute>()
            FapFolderHashtagScreen(
                tag = route.tag,
                onBack = { navController.popBackStack() },
                onOpenPost = { slug -> navController.navigateSafely(FapFolderPostRoute(slug)) },
                onOpenHashtag = { tag -> navController.navigateSafely(FapFolderHashtagRoute(tag)) },
                onOpenGroup = { slug -> navController.navigateSafely(FapFolderGroupRoute(slug)) },
                onOpenProfile = { username ->
                    navController.navigateSafely(FapFolderProfileRoute(username))
                },
            )
        }

        composable<FapFolderGroupRoute> { entry ->
            val route = entry.toRoute<FapFolderGroupRoute>()
            FapFolderGroupScreen(
                slug = route.slug,
                initialTab = route.tab,
                onBack = { navController.popBackStack() },
                onOpenPost = { slug -> navController.navigateSafely(FapFolderPostRoute(slug)) },
                onOpenProfile = { username ->
                    navController.navigateSafely(FapFolderProfileRoute(username))
                },
                onOpenAlbum = { albumId ->
                    navController.navigateSafely(FapFolderAlbumRoute(albumId))
                },
                onOpenHashtag = { tag ->
                    navController.navigateSafely(FapFolderHashtagRoute(tag))
                },
            )
        }

        composable<FapFolderProfileRoute> { entry ->
            val route = entry.toRoute<FapFolderProfileRoute>()
            FapFolderProfileScreen(
                username = route.username,
                initialTab = route.tab,
                onBack = { navController.popBackStack() },
                onOpenPost = { slug -> navController.navigateSafely(FapFolderPostRoute(slug)) },
                onOpenGroup = { slug -> navController.navigateSafely(FapFolderGroupRoute(slug)) },
                onOpenAlbum = { albumId ->
                    navController.navigateSafely(FapFolderAlbumRoute(albumId))
                },
                onOpenHashtag = { tag ->
                    navController.navigateSafely(FapFolderHashtagRoute(tag))
                },
                onOpenProfile = { username ->
                    navController.navigateSafely(FapFolderProfileRoute(username))
                },
            )
        }

        composable<FapFolderAlbumRoute> { entry ->
            val route = entry.toRoute<FapFolderAlbumRoute>()
            FapFolderAlbumScreen(
                albumId = route.albumId,
                onBack = { navController.popBackStack() },
                onOpenPost = { slug -> navController.navigateSafely(FapFolderPostRoute(slug)) },
            )
        }

        composable<FapFolderHistoryRoute> {
            FapFolderHistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenPost = { slug -> navController.navigateSafely(FapFolderPostRoute(slug)) },
            )
        }

        composable<FapFolderSettingsRoute> {
            FapFolderSettingsHubScreen(
                onBack = { navController.popBackStack() },
                onOpenCookies = { navController.navigateSafely(FapFolderCookiesRoute) },
                onOpenLogin = { navController.navigateSafely(FapFolderLoginRoute) },
                onOpenHistory = { navController.navigateSafely(FapFolderHistoryRoute) },
                onOpenSearch = { navController.navigateSafely(FapFolderSearchRoute()) },
                onOpenSearchHistory = {
                    navController.navigateSafely(FapFolderSearchHistoryRoute)
                },
            )
        }

        composable<FapFolderCookiesRoute> {
            FapFolderCookiesScreen(
                onBack = { navController.popBackStack() },
                onOpenLogin = { navController.navigateSafely(FapFolderLoginRoute) },
                onOpenRegister = { },
                onSwitchSite = {
                    activity.onBackPressedDispatcher.onBackPressed()
                },
                onCookiesSaved = {
                    retryTick = System.currentTimeMillis()
                    navController.popBackStack()
                },
                onLoggedOut = {
                    retryTick = System.currentTimeMillis()
                },
                gateState = state,
            )
        }

        composable<FapFolderLoginRoute> {
            FapFolderLoginScreen(
                onBack = { navController.popBackStack() },
                onLoginSuccess = {
                    retryTick = System.currentTimeMillis()
                    navController.popBackStack(FapFolderHomeRoute, inclusive = false)
                },
                onOpenCookies = {
                    navController.navigateSafely(FapFolderCookiesRoute)
                },
            )
        }
    }
}

@Composable
private fun GatePlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "FapFolder", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun GateErrorHost(
    navController: NavHostController,
    state: FapFolderGateState.ContentError,
    onRetry: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = FapFolderGateRoute,
    ) {
        composable<FapFolderGateRoute> {
            GateErrorScreen(
                state = state,
                onRetry = onRetry,
                onOpenCookies = {
                    navController.navigateSafely(FapFolderCookiesRoute)
                },
                onOpenLogin = {
                    navController.navigateSafely(FapFolderLoginRoute)
                },
                onSwitchSite = { },
            )
        }
        composable<FapFolderCookiesRoute> {
            FapFolderCookiesScreen(
                onBack = { navController.popBackStack() },
                onOpenLogin = {
                    navController.navigateSafely(FapFolderLoginRoute)
                },
                onOpenRegister = { },
                onSwitchSite = { },
                onCookiesSaved = onRetry,
                onLoggedOut = onRetry,
                gateState = state,
            )
        }
        composable<FapFolderLoginRoute> {
            FapFolderLoginScreen(
                onBack = { navController.popBackStack() },
                onLoginSuccess = onRetry,
                onOpenCookies = {
                    navController.navigateSafely(FapFolderCookiesRoute)
                },
            )
        }
    }
}

@Composable
private fun GateErrorScreen(
    state: FapFolderGateState.ContentError,
    onRetry: () -> Unit,
    onOpenCookies: () -> Unit,
    onOpenLogin: () -> Unit,
    onSwitchSite: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "FapFolder", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = state.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onRetry) { Text("Retry") }
                TextButton(onClick = onOpenCookies) { Text("Cookies") }
                if (state.suggestRelogin) {
                    TextButton(onClick = onOpenLogin) { Text("Sign in") }
                }
                if (state.suggestSwitchSite) {
                    TextButton(onClick = onSwitchSite) { Text("Switch site") }
                }
            }
        }
    }
}
