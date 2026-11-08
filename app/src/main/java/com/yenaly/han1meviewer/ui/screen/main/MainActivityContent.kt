package com.yenaly.han1meviewer.ui.screen.main

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import com.yenaly.han1meviewer.FapFolder.ui.navigation.FapFolderHomeRoute
import com.yenaly.han1meviewer.SupJav.ui.navigation.SupJavHomeRoute
import com.yenaly.han1meviewer.RouVideo.ui.navigation.RouVideoHomeRoute
import com.yenaly.han1meviewer.HentaiMama.ui.navigation.HentaiMamaHomeRoute
import com.yenaly.han1meviewer.MissAV.ui.navigation.MissAvHomeRoute
import com.yenaly.han1meviewer.OnAir.ui.navigation.OnAirHomeRoute
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.logic.exception.CloudFlareBlockedException
import com.yenaly.han1meviewer.logic.model.github.Latest
import com.yenaly.han1meviewer.logic.state.PageState
import com.yenaly.han1meviewer.ui.component.SiteSwitchConfirmDialog
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.component.CrossSiteOpenDialog
import com.yenaly.han1meviewer.ui.component.UpdateDialog
import com.yenaly.han1meviewer.ui.component.GlobalDialogHost
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import com.yenaly.han1meviewer.ui.component.GlobalToastHost
import com.yenaly.han1meviewer.ui.component.UsageNoticeDialog
import com.yenaly.han1meviewer.ui.navigation.NavigationManager
import com.yenaly.han1meviewer.ui.navigation.main.MainDestinationSpec
import com.yenaly.han1meviewer.ui.navigation.main.UnifiedMainNavHost
import com.yenaly.han1meviewer.ui.navigation.main.handleMainIntent
import com.yenaly.han1meviewer.ui.navigation.main.navigateDrawerDestination
import com.yenaly.han1meviewer.ui.theme.HanimeTheme
import com.yenaly.han1meviewer.ui.viewmodel.AppViewModel
import com.yenaly.han1meviewer.ui.screen.home.homepage.HomePageViewModel
import com.yenaly.han1meviewer.util.getUpdateIfExists
import com.yenaly.han1meviewer.util.installApkPackage
import com.yenaly.han1meviewer.util.requestPostNotificationPermission
import com.yenaly.han1meviewer.worker.HUpdateWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime
import androidx.core.graphics.drawable.toDrawable

@OptIn(ExperimentalTime::class)
@Composable
fun MainActivityContent(
    activity: MainActivity,
    viewModel: HomePageViewModel,
    pendingNavigationRequests: Flow<Intent>,
    showAuthGuard: Boolean,
    onOpenAccount: () -> Unit,
    onLogoutClick: () -> Unit,
    onRequireLogin: () -> Unit,
    onSwitchSiteClick: () -> Unit,
    onNavigateControllerReady: (NavHostController) -> Unit,
    siteChangeKey: Long = 0L,
) {
    HanimeTheme {
        val windowBackground = MaterialTheme.colorScheme.background
        LaunchedEffect(windowBackground) {
            activity.window.setBackgroundDrawable(windowBackground.toArgb().toDrawable())
        }

        val composeNavController = rememberNavController()

        LaunchedEffect(siteChangeKey) {
            onNavigateControllerReady(composeNavController)
            NavigationManager.initialize(composeNavController, Preferences.siteType)
        }

        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        var currentMainDestination by remember { mutableStateOf(MainDestinationSpec.Home) }
        var pendingUpdate by remember { mutableStateOf<Latest?>(null) }
        var showUsageNotice by remember { mutableStateOf(!Preferences.usageNoticeAccepted) }
        val loginFirst = stringResource(R.string.login_first)
        val updateDownloadBackground = stringResource(R.string.update_download_background)
        val isDrawerOpen =
            drawerState.currentValue == DrawerValue.Open || drawerState.targetValue == DrawerValue.Open

        val homeState by viewModel.homePageFlow.collectAsStateWithLifecycle()
        val isLoggedIn by Preferences.loginStateFlow.collectAsStateWithLifecycle()
        val headerAvatarUrl = if (isLoggedIn) {
            (homeState as? PageState.Success)?.info?.page?.avatarUrl
        } else {
            null
        }
        val headerUsername = if (isLoggedIn) {
            (homeState as? PageState.Success)?.info?.page?.username
        } else {
            null
        }
        val headerIsLoading = isLoggedIn && homeState is PageState.Loading
        val selectedDrawerDestination = currentMainDestination.drawerDestination

        val isHanimeFamilySite = Preferences.siteType == SiteType.HANIME ||
                Preferences.siteType == SiteType.JAVCHU

        val backStackEntry by composeNavController.currentBackStackEntryAsState()
        val currentSite = Preferences.siteType
        val isAtSiteHome = remember(backStackEntry, currentSite) {
            val d = backStackEntry?.destination
            when (currentSite) {
                SiteType.MISSAV -> d?.hasRoute(MissAvHomeRoute::class) == true
                SiteType.HENTAIMAMA -> d?.hasRoute(HentaiMamaHomeRoute::class) == true
                SiteType.ONAIR -> d?.hasRoute(OnAirHomeRoute::class) == true
                SiteType.FAPFOLDER -> d?.hasRoute(FapFolderHomeRoute::class) == true
                SiteType.SUPJAV -> d?.hasRoute(SupJavHomeRoute::class) == true
                SiteType.ROUVIDEO -> d?.hasRoute(RouVideoHomeRoute::class) == true
                else -> false
            }
        }
        val drawerAvailable = if (isHanimeFamilySite) {
            currentMainDestination.drawerEnabled
        } else {
            isAtSiteHome
        }

        val drawerEnabledNow = drawerAvailable
        LaunchedEffect(siteChangeKey, Preferences.siteType, drawerEnabledNow) {
            if (drawerState.currentValue == DrawerValue.Open ||
                drawerState.targetValue == DrawerValue.Open
            ) {
                drawerState.snapTo(DrawerValue.Closed)
            }
        }

        LaunchedEffect(Unit) {
            pendingNavigationRequests.collect { intent ->
                composeNavController.handleMainIntent(intent)
            }
        }

        LaunchedEffect(Unit) {
            AppViewModel.pendingUpdateDialog.collect { latest ->
                Preferences.lastUpdatePopupTime = kotlin.time.Clock.System.now().epochSeconds
                pendingUpdate = latest
            }
        }

        LaunchedEffect(viewModel) {
            viewModel.sessionExpiredMessage.collect { event ->
                if (event.message != null) {
                    GlobalToasts.show(event.message, level = GlobalToasts.ToastLevel.WARNING)
                } else {
                    GlobalToasts.show(activity.getString(event.fallbackResId), level = GlobalToasts.ToastLevel.WARNING)
                }
            }
        }

        LaunchedEffect(homeState) {
            if (homeState is PageState.Error) {
                val throwable = (homeState as PageState.Error).throwable
                if (throwable is CloudFlareBlockedException) {
                    Log.e("error", "被屏蔽时的处理")
                }
            }
        }

        MainActivityScaffold(
            drawerState = drawerState,
            drawerEnabled = drawerAvailable,
            selectedDestination = selectedDrawerDestination,
            avatarUrl = headerAvatarUrl,
            username = headerUsername,
            isLoggedIn = isLoggedIn,
            isLoading = headerIsLoading,
            currentSite = Preferences.baseUrl,
            onAvatarClick = {
                if (isLoggedIn) {
                    scope.launch { drawerState.close() }
                    onOpenAccount()
                } else {
                    onRequireLogin()
                }
            },
            onAvatarLongClick = {
                onLogoutClick()
            },
            onSwitchSiteClick = {
                scope.launch { drawerState.close() }
                onSwitchSiteClick()
            },
            siteDrawerContent = if (isHanimeFamilySite) null else ({
                SiteDrawerContent(
                    site = currentSite,
                    navController = composeNavController,
                    refreshKey = drawerState.currentValue,
                    onClose = { scope.launch { drawerState.close() } },
                    onSwitchSiteClick = {
                        scope.launch { drawerState.close() }
                        onSwitchSiteClick()
                    },
                )
            }),
            onDrawerItemSelected = { destination ->
                val handled = composeNavController.navigateDrawerDestination(
                    destination = destination,
                    isLoggedIn = isLoggedIn,
                    onRequireLogin = { GlobalToasts.show(loginFirst, level = GlobalToasts.ToastLevel.WARNING) },
                )
                if (handled) {
                    scope.launch { drawerState.close() }
                }
                handled
            },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                UnifiedMainNavHost(
                    activity = activity,
                    navController = composeNavController,
                    isDrawerOpen = isDrawerOpen,
                    onOpenDrawer = {
                        if (drawerAvailable) {
                            scope.launch { drawerState.open() }
                        }
                    },
                    onDestinationChanged = { destination ->
                        currentMainDestination = destination
                    },
                    siteChangeKey = siteChangeKey,
                )

                if (showAuthGuard) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                    )
                }

                pendingUpdate?.let { latest ->
                    UpdateDialog(
                        latest = latest,
                        onDismiss = { pendingUpdate = null },
                        onConfirm = {
                            pendingUpdate = null
                            scope.launch {
                                val file = activity.getUpdateIfExists(latest)
                                if (file != null) {
                                    activity.installApkPackage(file)
                                } else {
                                    if (activity.requestPostNotificationPermission()) {
                                        HUpdateWorker.enqueue(activity.applicationContext, latest)
                                        GlobalToasts.show(updateDownloadBackground, level = GlobalToasts.ToastLevel.INFO)
                                    }
                                }
                            }
                        },
                    )
                }

                UsageNoticeDialog(
                    visible = showUsageNotice,
                    onAccepted = {
                        Preferences.usageNoticeAccepted = true
                        showUsageNotice = false
                    },
                    onDeclined = { activity.finish() },
                )
            }
        }

        if (activity.showSiteSwitchConfirm) {
            SiteSwitchConfirmDialog(
                title = stringResource(R.string.confirm_switch_site),
                confirmText = stringResource(R.string.sure),
                dismissText = stringResource(R.string.no),
                onConfirm = { activity.confirmSiteSwitch() },
                onDismiss = { activity.dismissSiteSwitch() },
            )
        }

        if (activity.showLogoutConfirm) {
            AlertDialog(
                onDismissRequest = { activity.dismissLogoutConfirm() },
                title = { Text(stringResource(R.string.sure_to_logout)) },
                confirmButton = {
                    TextButton(onClick = { activity.confirmLogout() }) {
                        Text(stringResource(R.string.sure))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activity.dismissLogoutConfirm() }) {
                        Text(stringResource(R.string.no))
                    }
                },
            )
        }

        activity.crossSiteRequest?.let { request ->
            CrossSiteOpenDialog(
                request = request,
                onDismiss = { activity.dismissCrossSiteRequest() },
                onValidated = { activity.confirmCrossSiteOpen(request) },
            )
        }

        GlobalDialogHost()
        GlobalToastHost()
    }
}
