package com.yenaly.han1meviewer.OnAir.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.yenaly.han1meviewer.OnAir.data.local.OnAirProfileCache
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirAuthProbe
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetwork
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirTorChecker
import com.yenaly.han1meviewer.OnAir.data.remote.TorFailure
import com.yenaly.han1meviewer.OnAir.ui.announcement.OnAirAnnouncementsScreen
import com.yenaly.han1meviewer.OnAir.ui.auth.OnAirLoginScreen
import com.yenaly.han1meviewer.OnAir.ui.auth.OnAirRegisterScreen
import com.yenaly.han1meviewer.OnAir.ui.chat.OnAirChatScreen
import com.yenaly.han1meviewer.OnAir.ui.gate.OnAirGateScreen
import com.yenaly.han1meviewer.OnAir.ui.gate.OnAirGateState
import com.yenaly.han1meviewer.OnAir.ui.history.OnAirHistoryScreen
import com.yenaly.han1meviewer.OnAir.ui.home.OnAirHomeScreen
import com.yenaly.han1meviewer.OnAir.ui.home.OnAirHomeViewModel
import com.yenaly.han1meviewer.OnAir.ui.picture.OnAirPicturesScreen
import com.yenaly.han1meviewer.OnAir.ui.search.OnAirSearchScreen
import com.yenaly.han1meviewer.OnAir.ui.search.OnAirSearchViewModel
import com.yenaly.han1meviewer.OnAir.ui.settings.OnAirCookieSettingsScreen
import com.yenaly.han1meviewer.OnAir.ui.settings.OnAirSettingsScreen
import com.yenaly.han1meviewer.OnAir.ui.tag.OnAirTagScreen
import com.yenaly.han1meviewer.OnAir.ui.tag.OnAirTagsIndexScreen
import com.yenaly.han1meviewer.OnAir.ui.user.OnAirUserScreen
import com.yenaly.han1meviewer.OnAir.ui.video.OnAirVideoScreen
import com.yenaly.han1meviewer.OnAir.ui.video.OnAirVideoViewModel
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.navigation.NavigationManager
import com.yenaly.han1meviewer.ui.navigation.navigateSafely
import com.yenaly.han1meviewer.ui.navigation.main.MainDestinationSpec
import kotlinx.coroutines.delay

@Composable
fun OnAirNavHost(
    activity: MainActivity,
    navController: NavHostController,
    isDrawerOpen: Boolean,
    onOpenDrawer: () -> Unit,
    onDestinationChanged: (MainDestinationSpec) -> Unit,
) {
    var gateState by rememberSaveable(stateSaver = OnAirGateState.Saver) {
        mutableStateOf(OnAirGateState.Initializing)
    }
    var retryTick by rememberSaveable { mutableStateOf(0L) }
    var showTorModal by rememberSaveable { mutableStateOf(false) }
    var torModalReason by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(retryTick) {
        OnAirNetwork.init(activity.applicationContext)
        NavigationManager.initialize(navController, SiteType.ONAIR)

        gateState = OnAirGateState.CheckingTor
        delay(16)

        val tor = OnAirTorChecker.check()
        if (!tor.isTorExit) {
            val message = when (tor.failure) {
                TorFailure.Timeout ->
                    "Tor check timed out. Make sure the Tor daemon is running, then retry."
                TorFailure.NetworkUnreachable ->
                    "Cannot reach the Tor check service. Connect to Tor before opening OnAir."
                TorFailure.NonTorExit ->
                    "This device is not exiting through the Tor network. " +
                            "Enable Tor and try again."
                else ->
                    tor.message.ifBlank { "Tor is required to access OnAir." }
            }
            gateState = OnAirGateState.TorBlocked(
                reason = message,
                torFailure = when (tor.failure) {
                    TorFailure.Timeout -> OnAirGateState.TorFailureKind.Timeout
                    TorFailure.NetworkUnreachable -> OnAirGateState.TorFailureKind.NetworkUnreachable
                    TorFailure.NonTorExit -> OnAirGateState.TorFailureKind.NonTorExit
                    else -> OnAirGateState.TorFailureKind.Unknown
                },
            )
            torModalReason = message
            showTorModal = true
            return@LaunchedEffect
        }

        gateState = OnAirGateState.Probing
        delay(16)
        val probe = OnAirAuthProbe.probe(activity)
        gateState = if (probe.ok) {
            OnAirGateState.Passed
        } else {
            OnAirGateState.ContentError(
                reason = probe.userMessage.orEmpty(),
                suggestSwitchSite = probe.suggestSwitchSite,
                suggestWait = probe.suggestWait,
                suggestRelogin = probe.suggestRelogin,
                probeFailure = probe.failure,
            )
        }
    }

    if (showTorModal) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Tor not connected") },
            text = {
                Text(
                    torModalReason.ifBlank {
                        "OnAir is only reachable via the Tor network. " +
                                "Enable Tor and retry, or switch to a different site."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showTorModal = false
                    retryTick = System.currentTimeMillis()
                }) { Text("Retry") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        showTorModal = false
                        activity.requestSiteSwitch()
                    }) { Text("Switch site") }
                    TextButton(onClick = {
                        showTorModal = false
                        navController.navigate(OnAirCookieRoute) { launchSingleTop = true }
                    }) { Text("Cancel") }
                }
            },
        )
    }

    // Child-screen failures must NOT re-run the gate.
    val recoverableError: (String) -> Unit = { _ -> }

    if (gateState is OnAirGateState.Passed) {
        val homeViewModel: OnAirHomeViewModel = viewModel()

        LaunchedEffect(Unit) {
            if (OnAirProfileCache.getCurrentUsername() != null &&
                OnAirProfileCache.current() == null
            ) {
                homeViewModel.refreshCurrentUser()
            }
        }

        NavHost(
            navController = navController,
            startDestination = OnAirHomeRoute,
        ) {
            composable<OnAirHomeRoute> {
                OnAirHomeScreen(
                    onOpenVideo = { id -> navController.navigateSafely(OnAirVideoRoute(id)) },
                    onOpenSearch = { q -> navController.navigateSafely(OnAirSearchRoute(query = q)) },
                    onOpenTag = { t -> navController.navigateSafely(OnAirTagRoute(t)) },
                    onOpenTags = { navController.navigateSafely(OnAirTagsIndexRoute) },
                    onOpenPictures = { navController.navigateSafely(OnAirPicturesRoute) },
                    onOpenUser = { u -> navController.navigateSafely(OnAirUserRoute(u)) },
                    onOpenHistory = { navController.navigateSafely(OnAirHistoryRoute) },
                    onOpenAnnouncement = { navController.navigateSafely(OnAirAnnouncementsRoute) },
                    onOpenSettings = { navController.navigateSafely(OnAirSettingsRoute) },
                    onSwitchSite = { activity.requestSiteSwitch() },
                    onLoadFailure = recoverableError,
                    viewModel = homeViewModel,
                )
            }

            composable<OnAirSearchRoute> { entry ->
                val route = entry.toRoute<OnAirSearchRoute>()
                val vm: OnAirSearchViewModel = viewModel()
                LaunchedEffect(route) {
                    if (!route.query.isNullOrBlank()) vm.setQuery(route.query)
                    if (!route.field.isNullOrBlank()) vm.setField(route.field)
                    if (!route.length.isNullOrBlank()) vm.setLength(route.length)
                    if (!route.date.isNullOrBlank()) vm.setDate(route.date)
                    if (!route.sort.isNullOrBlank()) vm.setSort(route.sort)
                    vm.setTagFilter(route.tag)
                }
                OnAirSearchScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onOpenVideo = { id -> navController.navigateSafely(OnAirVideoRoute(id)) },
                    onOpenTag = { t -> navController.navigateSafely(OnAirTagRoute(t)) },
                    onOpenUser = { u -> navController.navigateSafely(OnAirUserRoute(u)) },
                    onLoadFailure = recoverableError,
                )
            }

            composable<OnAirTagRoute> { entry ->
                val route = entry.toRoute<OnAirTagRoute>()
                OnAirTagScreen(
                    tagName = route.tagName,
                    onBack = { navController.popBackStack() },
                    onOpenVideo = { id -> navController.navigateSafely(OnAirVideoRoute(id)) },
                    onLoadFailure = recoverableError,
                )
            }

            composable<OnAirTagsIndexRoute> {
                OnAirTagsIndexScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTag = { t -> navController.navigateSafely(OnAirTagRoute(t)) },
                    onLoadFailure = recoverableError,
                )
            }

            composable<OnAirPicturesRoute> {
                OnAirPicturesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenUser = { u -> navController.navigateSafely(OnAirUserRoute(u)) },
                    onLoadFailure = recoverableError,
                )
            }

            composable<OnAirUserRoute> { entry ->
                val route = entry.toRoute<OnAirUserRoute>()
                OnAirUserScreen(
                    username = route.username,
                    onBack = { navController.popBackStack() },
                    onOpenVideo = { id -> navController.navigateSafely(OnAirVideoRoute(id)) },
                    onLoadFailure = recoverableError,
                )
            }

            composable<OnAirHistoryRoute> {
                OnAirHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenVideo = { id, _ -> navController.navigateSafely(OnAirVideoRoute(id)) },
                )
            }

            composable<OnAirAnnouncementsRoute> {
                OnAirAnnouncementsScreen(onBack = { navController.popBackStack() })
            }

            composable<OnAirChatRoute> {
                OnAirChatScreen(
                    onBack = { navController.popBackStack() },
                    onOpenUser = { u -> navController.navigateSafely(OnAirUserRoute(u)) },
                )
            }

            composable<OnAirSettingsRoute> {
                OnAirSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCookies = { navController.navigateSafely(OnAirCookieRoute) },
                    onOpenLogin = { navController.navigateSafely(OnAirLoginRoute) },
                    onOpenAnnouncements = { navController.navigateSafely(OnAirAnnouncementsRoute) },
                    onOpenChat = { navController.navigateSafely(OnAirChatRoute) },
                    onOpenTags = { navController.navigateSafely(OnAirTagsIndexRoute) },
                    onLoggedOut = {
                        OnAirProfileCache.invalidate()
                        gateState = OnAirGateState.ContentError(
                            reason = "You signed out of OnAir.",
                            suggestRelogin = true,
                        )
                        navController.popBackStack(OnAirHomeRoute, inclusive = true)
                    },
                    onOpenProfile = { username ->
                        navController.navigateSafely(OnAirUserRoute(username))
                    },
                )
            }

            composable<OnAirCookieRoute> {
                OnAirCookieSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLogin = { navController.navigateSafely(OnAirLoginRoute) },
                    onOpenRegister = { navController.navigateSafely(OnAirRegisterRoute) },
                    onSwitchSite = { activity.requestSiteSwitch() },
                    onCookiesSaved = { retryTick = System.currentTimeMillis() },
                    onLoggedOut = { retryTick = System.currentTimeMillis() },
                )
            }

            composable<OnAirLoginRoute> {
                OnAirLoginScreen(
                    onBack = { navController.popBackStack() },
                    onOpenRegister = { navController.navigateSafely(OnAirRegisterRoute) },
                    onSuccess = {
                        OnAirProfileCache.invalidate()
                        retryTick = System.currentTimeMillis()
                        navController.popBackStack(OnAirHomeRoute, inclusive = false)
                    },
                )
            }

            composable<OnAirRegisterRoute> {
                OnAirRegisterScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLogin = { navController.navigateSafely(OnAirLoginRoute) },
                    onSuccess = {
                        OnAirProfileCache.invalidate()
                        retryTick = System.currentTimeMillis()
                        navController.popBackStack(OnAirHomeRoute, inclusive = false)
                    },
                )
            }

            composable<OnAirVideoRoute> { entry ->
                val route = entry.toRoute<OnAirVideoRoute>()
                val vm: OnAirVideoViewModel = viewModel()
                OnAirVideoScreen(
                    videoId = route.videoId,
                    preferredQuality = route.preferredQuality,
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onOpenVideo = { id ->
                        if (id != route.videoId) {
                            navController.navigateSafely(OnAirVideoRoute(id))
                        }
                    },
                    onOpenUser = { u -> navController.navigateSafely(OnAirUserRoute(u)) },
                    onOpenTag = { t -> navController.navigateSafely(OnAirTagRoute(t)) },
                    onLoadFailure = recoverableError,
                )
            }
        }
    } else {
        val activeState = gateState
        NavHost(
            navController = navController,
            startDestination = OnAirGateRoute,
        ) {
            composable<OnAirGateRoute> {
                OnAirGateScreen(
                    state = activeState,
                    onSwitchSite = { activity.requestSiteSwitch() },
                    onOpenCookies = {
                        navController.navigate(OnAirCookieRoute) { launchSingleTop = true }
                    },
                    onRetry = { retryTick = System.currentTimeMillis() },
                )
            }
            composable<OnAirCookieRoute> {
                OnAirCookieSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLogin = {
                        navController.navigate(OnAirLoginRoute) { launchSingleTop = true }
                    },
                    onOpenRegister = {
                        navController.navigate(OnAirRegisterRoute) { launchSingleTop = true }
                    },
                    onSwitchSite = { activity.requestSiteSwitch() },
                    onCookiesSaved = { retryTick = System.currentTimeMillis() },
                    onLoggedOut = { retryTick = System.currentTimeMillis() },
                    gateState = activeState,
                )
            }
            composable<OnAirLoginRoute> {
                OnAirLoginScreen(
                    onBack = { navController.popBackStack() },
                    onOpenRegister = {
                        navController.navigate(OnAirRegisterRoute) { launchSingleTop = true }
                    },
                    onSuccess = { retryTick = System.currentTimeMillis() },
                )
            }
            composable<OnAirRegisterRoute> {
                OnAirRegisterScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLogin = {
                        navController.navigate(OnAirLoginRoute) { launchSingleTop = true }
                    },
                    onSuccess = { retryTick = System.currentTimeMillis() },
                )
            }
        }
    }
}
