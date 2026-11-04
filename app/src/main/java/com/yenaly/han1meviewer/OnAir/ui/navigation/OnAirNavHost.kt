package com.yenaly.han1meviewer.OnAir.ui.navigation

import com.yenaly.yenaly_libs.utils.applicationContext
import com.yenaly.han1meviewer.R
import androidx.compose.ui.res.stringResource
import com.yenaly.han1meviewer.ui.navigation.popBackStackSafely
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
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
import com.yenaly.han1meviewer.ui.navigation.settings.HomeSettingsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.NetworkSettingsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.PlayerSettingsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.UnifiedHistoryRoute
import com.yenaly.han1meviewer.ui.navigation.settings.registerGlobalSettingsRoutes
import com.yenaly.han1meviewer.ui.screen.settings.SiteSettingsEntry
import com.yenaly.han1meviewer.ui.screen.settings.SiteSettingsHubScreen
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
                    applicationContext.getString(R.string.onair_tor_timeout)
                TorFailure.NetworkUnreachable ->
                    applicationContext.getString(R.string.onair_tor_unreachable)
                TorFailure.NonTorExit ->
                    applicationContext.getString(R.string.onair_tor_non_exit)
                else ->
                    tor.message.ifBlank { applicationContext.getString(R.string.onair_tor_required) }
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
            title = { Text(stringResource(R.string.onair_tor_not_connected)) },
            text = {
                Text(
                    torModalReason.ifBlank {
                        stringResource(R.string.onair_tor_modal_default)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showTorModal = false
                    retryTick = System.currentTimeMillis()
                }) { Text(stringResource(R.string.retry)) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        showTorModal = false
                        activity.requestSiteSwitch()
                    }) { Text(stringResource(R.string.site_switch_site)) }
                    TextButton(onClick = {
                        showTorModal = false
                        navController.navigate(OnAirCookieRoute) { launchSingleTop = true }
                    }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }

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
                    onOpenSettings = onOpenDrawer,
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
                    onBack = { navController.popBackStackSafely() },
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
                    onBack = { navController.popBackStackSafely() },
                    onOpenVideo = { id -> navController.navigateSafely(OnAirVideoRoute(id)) },
                    onLoadFailure = recoverableError,
                )
            }

            composable<OnAirTagsIndexRoute> {
                OnAirTagsIndexScreen(
                    onBack = { navController.popBackStackSafely() },
                    onOpenTag = { t -> navController.navigateSafely(OnAirTagRoute(t)) },
                    onLoadFailure = recoverableError,
                )
            }

            composable<OnAirPicturesRoute> {
                OnAirPicturesScreen(
                    onBack = { navController.popBackStackSafely() },
                    onOpenUser = { u -> navController.navigateSafely(OnAirUserRoute(u)) },
                    onLoadFailure = recoverableError,
                )
            }

            composable<OnAirUserRoute> { entry ->
                val route = entry.toRoute<OnAirUserRoute>()
                OnAirUserScreen(
                    username = route.username,
                    onBack = { navController.popBackStackSafely() },
                    onOpenVideo = { id -> navController.navigateSafely(OnAirVideoRoute(id)) },
                    onLoadFailure = recoverableError,
                )
            }

            composable<OnAirHistoryRoute> {
                OnAirHistoryScreen(
                    onBack = { navController.popBackStackSafely() },
                    onOpenVideo = { id, _ -> navController.navigateSafely(OnAirVideoRoute(id)) },
                )
            }

            composable<OnAirAnnouncementsRoute> {
                OnAirAnnouncementsScreen(onBack = { navController.popBackStackSafely() })
            }

            composable<OnAirChatRoute> {
                OnAirChatScreen(
                    onBack = { navController.popBackStackSafely() },
                    onOpenUser = { u -> navController.navigateSafely(OnAirUserRoute(u)) },
                )
            }

            composable<OnAirSettingsRoute> {
                SiteSettingsHubScreen(
                    activity = activity,
                    site = SiteType.ONAIR,
                    siteEntries = onAirHubEntries(navController, signedInContent = true),
                    onBack = { navController.popBackStackSafely() },
                    onOpenAppSettings = { navController.navigateSafely(HomeSettingsRoute) },
                    onOpenPlayerSettings = { navController.navigateSafely(PlayerSettingsRoute) },
                    onOpenNetworkSettings = { navController.navigateSafely(NetworkSettingsRoute) },
                    onOpenUnifiedHistory = { navController.navigateSafely(UnifiedHistoryRoute) },
                )
            }

            composable<OnAirAccountSettingsRoute> {
                OnAirSettingsScreen(
                    onBack = { navController.popBackStackSafely() },
                    onOpenCookies = { navController.navigateSafely(OnAirCookieRoute) },
                    onOpenLogin = { navController.navigateSafely(OnAirLoginRoute) },
                    onOpenAnnouncements = { navController.navigateSafely(OnAirAnnouncementsRoute) },
                    onOpenChat = { navController.navigateSafely(OnAirChatRoute) },
                    onOpenTags = { navController.navigateSafely(OnAirTagsIndexRoute) },
                    onLoggedOut = {
                        OnAirProfileCache.invalidate()
                        gateState = OnAirGateState.ContentError(
                            reason = applicationContext.getString(R.string.onair_signed_out),
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
                    onBack = { navController.popBackStackSafely() },
                    onOpenLogin = { navController.navigateSafely(OnAirLoginRoute) },
                    onOpenRegister = { navController.navigateSafely(OnAirRegisterRoute) },
                    onSwitchSite = { activity.requestSiteSwitch() },
                    onCookiesSaved = { retryTick = System.currentTimeMillis() },
                    onLoggedOut = { retryTick = System.currentTimeMillis() },
                )
            }

            composable<OnAirLoginRoute> {
                OnAirLoginScreen(
                    onBack = { navController.popBackStackSafely() },
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
                    onBack = { navController.popBackStackSafely() },
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
                    onBack = { navController.popBackStackSafely() },
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

            registerGlobalSettingsRoutes(
                activity = activity,
                navController = navController,
                homeFallback = OnAirSettingsRoute,
            )
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
                    onOpenSettings = {
                        navController.navigate(OnAirSettingsRoute) { launchSingleTop = true }
                    },
                )
            }
            composable<OnAirSettingsRoute> {
                SiteSettingsHubScreen(
                    activity = activity,
                    site = SiteType.ONAIR,
                    siteEntries = onAirHubEntries(navController, signedInContent = false),
                    onBack = { navController.popBackStackSafely() },
                    onOpenAppSettings = { navController.navigateSafely(HomeSettingsRoute) },
                    onOpenPlayerSettings = { navController.navigateSafely(PlayerSettingsRoute) },
                    onOpenNetworkSettings = { navController.navigateSafely(NetworkSettingsRoute) },
                    onOpenUnifiedHistory = { navController.navigateSafely(UnifiedHistoryRoute) },
                )
            }
            registerGlobalSettingsRoutes(
                activity = activity,
                navController = navController,
                homeFallback = OnAirSettingsRoute,
            )
            composable<OnAirCookieRoute> {
                OnAirCookieSettingsScreen(
                    onBack = { navController.popBackStackSafely() },
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
                    onBack = { navController.popBackStackSafely() },
                    onOpenRegister = {
                        navController.navigate(OnAirRegisterRoute) { launchSingleTop = true }
                    },
                    onSuccess = { retryTick = System.currentTimeMillis() },
                )
            }
            composable<OnAirRegisterRoute> {
                OnAirRegisterScreen(
                    onBack = { navController.popBackStackSafely() },
                    onOpenLogin = {
                        navController.navigate(OnAirLoginRoute) { launchSingleTop = true }
                    },
                    onSuccess = { retryTick = System.currentTimeMillis() },
                )
            }
        }
    }
}

private fun onAirHubEntries(
    navController: NavHostController,
    signedInContent: Boolean,
): List<SiteSettingsEntry> = buildList {
    if (signedInContent) {
        add(
            SiteSettingsEntry(
                key = "account",
                title = applicationContext.getString(R.string.drawer_account_session),
                subtitle = applicationContext.getString(R.string.onair_account_subtitle),
                icon = Icons.Default.Person,
                onClick = { navController.navigateSafely(OnAirAccountSettingsRoute) },
            )
        )
    }
    add(
        SiteSettingsEntry(
            key = "cookies",
            title = applicationContext.getString(R.string.drawer_cookies_sign_in),
            subtitle = applicationContext.getString(R.string.onair_cookies_subtitle),
            icon = Icons.Default.Lock,
            onClick = { navController.navigateSafely(OnAirCookieRoute) },
        )
    )
    if (signedInContent) {
        add(
            SiteSettingsEntry(
                key = "tags",
                title = applicationContext.getString(R.string.drawer_tags),
                subtitle = applicationContext.getString(R.string.onair_tags_subtitle),
                icon = Icons.Default.Label,
                onClick = { navController.navigateSafely(OnAirTagsIndexRoute) },
            )
        )
        add(
            SiteSettingsEntry(
                key = "announcements",
                title = applicationContext.getString(R.string.onair_announcements_title),
                subtitle = applicationContext.getString(R.string.onair_announcements_subtitle),
                icon = Icons.Default.Campaign,
                onClick = { navController.navigateSafely(OnAirAnnouncementsRoute) },
            )
        )
        add(
            SiteSettingsEntry(
                key = "chat",
                title = applicationContext.getString(R.string.drawer_chat),
                subtitle = applicationContext.getString(R.string.onair_chat_subtitle),
                icon = Icons.Default.Chat,
                onClick = { navController.navigateSafely(OnAirChatRoute) },
            )
        )
        add(
            SiteSettingsEntry(
                key = "history",
                title = applicationContext.getString(R.string.onair_history_title),
                subtitle = applicationContext.getString(R.string.onair_history_subtitle),
                icon = Icons.Default.History,
                onClick = { navController.navigateSafely(OnAirHistoryRoute) },
            )
        )
    }
}
