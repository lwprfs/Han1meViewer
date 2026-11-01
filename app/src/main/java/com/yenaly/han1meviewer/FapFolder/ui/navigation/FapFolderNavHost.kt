package com.yenaly.han1meviewer.FapFolder.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.yenaly.han1meviewer.FapFolder.data.local.FapFolderHistoryRepo
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderAuthRepo
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetwork
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderProbeFailure
import com.yenaly.han1meviewer.FapFolder.ui.album.FapFolderAlbumScreen
import com.yenaly.han1meviewer.FapFolder.ui.followed.FapFolderFollowedCommunitiesScreen
import com.yenaly.han1meviewer.FapFolder.ui.gate.FapFolderGateState
import com.yenaly.han1meviewer.FapFolder.ui.group.FapFolderGroupScreen
import com.yenaly.han1meviewer.FapFolder.ui.hashtag.FapFolderHashtagScreen
import com.yenaly.han1meviewer.FapFolder.ui.history.FapFolderHistoryScreen
import com.yenaly.han1meviewer.FapFolder.ui.home.FapFolderHomeScreen
import com.yenaly.han1meviewer.FapFolder.ui.home.FapFolderHomeViewModel
import com.yenaly.han1meviewer.FapFolder.ui.login.FapFolderLoginScreen
import com.yenaly.han1meviewer.FapFolder.ui.post.FapFolderPostDetailScreen
import com.yenaly.han1meviewer.FapFolder.ui.profile.FapFolderProfileScreen
import com.yenaly.han1meviewer.FapFolder.ui.saved.FapFolderSavedScreen
import com.yenaly.han1meviewer.FapFolder.ui.search.FapFolderSearchHistoryScreen
import com.yenaly.han1meviewer.FapFolder.ui.search.FapFolderSearchScreen
import com.yenaly.han1meviewer.FapFolder.ui.settings.FapFolderCookiesScreen
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.navigation.NavigationManager
import com.yenaly.han1meviewer.ui.navigation.navigateSafely
import com.yenaly.han1meviewer.ui.navigation.main.MainDestinationSpec
import com.yenaly.han1meviewer.ui.navigation.settings.HomeSettingsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.NetworkSettingsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.PlayerSettingsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.UnifiedHistoryRoute
import com.yenaly.han1meviewer.ui.navigation.settings.registerGlobalSettingsRoutes
import com.yenaly.han1meviewer.ui.screen.settings.SiteSettingsEntry
import com.yenaly.han1meviewer.ui.screen.settings.SiteSettingsHubScreen
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
        FapFolderHistoryRepo.init(activity.applicationContext)
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
                activity = activity,
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
                    activity.requestSiteSwitch()
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
                onOpenVideoBySlug = { slug ->
                    navController.navigateSafely(FapFolderPostRoute(slug, autoPlay = true))
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
            val loggedIn = FapFolderNetwork.hasAuthCookies()
            SiteSettingsHubScreen(
                activity = activity,
                site = SiteType.FAPFOLDER,
                siteEntries = fapFolderHubEntries(navController, isLoggedIn = loggedIn, full = true),
                onBack = { navController.popBackStack() },
                onOpenAppSettings = { navController.navigateSafely(HomeSettingsRoute) },
                onOpenPlayerSettings = { navController.navigateSafely(PlayerSettingsRoute) },
                onOpenNetworkSettings = { navController.navigateSafely(NetworkSettingsRoute) },
                onOpenUnifiedHistory = { navController.navigateSafely(UnifiedHistoryRoute) },
            )
        }

        composable<FapFolderFollowedCommunitiesRoute> {
            FapFolderFollowedCommunitiesScreen(
                onBack = { navController.popBackStack() },
                onOpenGroup = { slug ->
                    navController.navigateSafely(FapFolderGroupRoute(slug))
                },
            )
        }

        composable<FapFolderSavedRoute> {
            FapFolderSavedScreen(
                onBack = { navController.popBackStack() },
                onOpenPost = { slug ->
                    navController.navigateSafely(FapFolderPostRoute(slug))
                },
                onOpenHashtag = { tag ->
                    navController.navigateSafely(FapFolderHashtagRoute(tag))
                },
                onOpenGroup = { slug ->
                    navController.navigateSafely(FapFolderGroupRoute(slug))
                },
                onOpenProfile = { username ->
                    navController.navigateSafely(FapFolderProfileRoute(username))
                },
            )
        }

        composable<FapFolderCookiesRoute> {
            FapFolderCookiesScreen(
                onBack = { navController.popBackStack() },
                onOpenLogin = { navController.navigateSafely(FapFolderLoginRoute) },
                onOpenRegister = { },
                onSwitchSite = { activity.requestSiteSwitch() },
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

        registerGlobalSettingsRoutes(
            activity = activity,
            navController = navController,
            homeFallback = FapFolderSettingsRoute,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GatePlaceholder(message: String) {
    // Same look as Hanime's LoadingContent (indicator + bodyMedium message).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LoadingIndicator()
            Text(
                text = "FapFolder",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GateErrorHost(
    activity: MainActivity,
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
                onSwitchSite = { activity.requestSiteSwitch() },
                onOpenSettings = {
                    navController.navigateSafely(FapFolderSettingsRoute)
                },
            )
        }
        composable<FapFolderSettingsRoute> {
            SiteSettingsHubScreen(
                activity = activity,
                site = SiteType.FAPFOLDER,
                siteEntries = fapFolderHubEntries(navController, isLoggedIn = false, full = false),
                onBack = { navController.popBackStack() },
                onOpenAppSettings = { navController.navigateSafely(HomeSettingsRoute) },
                onOpenPlayerSettings = { navController.navigateSafely(PlayerSettingsRoute) },
                onOpenNetworkSettings = { navController.navigateSafely(NetworkSettingsRoute) },
                onOpenUnifiedHistory = { navController.navigateSafely(UnifiedHistoryRoute) },
            )
        }
        registerGlobalSettingsRoutes(
            activity = activity,
            navController = navController,
            homeFallback = FapFolderSettingsRoute,
        )
        composable<FapFolderCookiesRoute> {
            FapFolderCookiesScreen(
                onBack = { navController.popBackStack() },
                onOpenLogin = {
                    navController.navigateSafely(FapFolderLoginRoute)
                },
                onOpenRegister = { },
                onSwitchSite = { activity.requestSiteSwitch() },
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
    onOpenSettings: () -> Unit,
) {
    val headline = when {
        state.suggestRelogin -> "Session expired"
        state.suggestWait -> "Please wait a moment"
        state.suggestSwitchSite -> "Site unavailable"
        else -> "FapFolder is not ready"
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Same visuals as Hanime's ErrorContent.
            Image(
                modifier = Modifier.size(150.dp),
                painter = painterResource(R.drawable.h_chan_sad),
                contentDescription = headline,
            )
            Text(
                text = "FapFolder",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 2.dp,
                shadowElevation = 1.dp,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Text(
                    text = state.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp),
                )
            }

            if (state.suggestRelogin) {
                Button(
                    onClick = onOpenLogin,
                    modifier = Modifier.fillMaxWidth(),
                ) { GateButtonLabel(Icons.AutoMirrored.Filled.Login, "Sign in") }
                FilledTonalButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                ) { GateButtonLabel(Icons.Default.Refresh, "Retry") }
            } else {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                ) { GateButtonLabel(Icons.Default.Refresh, if (state.suggestWait) "Retry later" else "Retry") }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenCookies,
                    modifier = Modifier.weight(1f),
                ) { GateButtonLabel(Icons.Default.Cookie, "Cookies") }
                OutlinedButton(
                    onClick = onSwitchSite,
                    modifier = Modifier.weight(1f),
                ) { GateButtonLabel(Icons.Default.SwapHoriz, "Switch site") }
            }
            TextButton(onClick = onOpenSettings) {
                GateButtonLabel(Icons.Default.Settings, "Settings")
            }
        }
    }
}

@Composable
private fun GateButtonLabel(icon: ImageVector, text: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(text, maxLines = 1)
}

private fun fapFolderHubEntries(
    navController: NavHostController,
    isLoggedIn: Boolean,
    full: Boolean,
): List<SiteSettingsEntry> = buildList {
    add(
        SiteSettingsEntry(
            key = "cookies",
            title = "Cookies",
            subtitle = "Import, view or clear the FapFolder session cookies",
            icon = Icons.Default.Cookie,
            onClick = { navController.navigateSafely(FapFolderCookiesRoute) },
        )
    )
    if (!isLoggedIn) {
        add(
            SiteSettingsEntry(
                key = "login",
                title = "Sign in",
                subtitle = "Log in to FapFolder",
                icon = Icons.AutoMirrored.Filled.Login,
                onClick = { navController.navigateSafely(FapFolderLoginRoute) },
            )
        )
    }
    if (full) {
        add(
            SiteSettingsEntry(
                key = "history",
                title = "FapFolder history",
                subtitle = "Posts and videos you've opened on FapFolder",
                icon = Icons.Default.History,
                onClick = { navController.navigateSafely(FapFolderHistoryRoute) },
            )
        )
        add(
            SiteSettingsEntry(
                key = "search",
                title = "Search",
                subtitle = "Search posts, people and groups",
                icon = Icons.Default.Search,
                onClick = { navController.navigateSafely(FapFolderSearchRoute()) },
            )
        )
        add(
            SiteSettingsEntry(
                key = "search_history",
                title = "Search history",
                subtitle = "Previous FapFolder searches",
                icon = Icons.Default.History,
                onClick = { navController.navigateSafely(FapFolderSearchHistoryRoute) },
            )
        )
        add(
            SiteSettingsEntry(
                key = "followed",
                title = "Followed communities",
                subtitle = "Groups you follow",
                icon = Icons.Default.Groups,
                onClick = { navController.navigateSafely(FapFolderFollowedCommunitiesRoute) },
            )
        )
        add(
            SiteSettingsEntry(
                key = "saved",
                title = "Saved posts",
                subtitle = "Posts you bookmarked",
                icon = Icons.Default.Bookmark,
                onClick = { navController.navigateSafely(FapFolderSavedRoute) },
            )
        )
    }
}




