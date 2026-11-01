package com.yenaly.han1meviewer.ui.screen.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetwork
import com.yenaly.han1meviewer.FapFolder.ui.navigation.FapFolderCookiesRoute
import com.yenaly.han1meviewer.FapFolder.ui.navigation.FapFolderLoginRoute
import com.yenaly.han1meviewer.FapFolder.ui.navigation.FapFolderSearchHistoryRoute
import com.yenaly.han1meviewer.FapFolder.ui.navigation.FapFolderSearchRoute
import com.yenaly.han1meviewer.HentaiMama.ui.navigation.HentaiMamaHomeSettingsRoute
import com.yenaly.han1meviewer.HentaiMama.ui.navigation.HentaiMamaVideoSettingsRoute
import com.yenaly.han1meviewer.MissAV.ui.navigation.MissAvHomeCategoriesRoute
import com.yenaly.han1meviewer.MissAV.ui.navigation.MissAvHostSettingsRoute
import com.yenaly.han1meviewer.OnAir.data.local.OnAirProfileCache
import com.yenaly.han1meviewer.OnAir.ui.navigation.OnAirAccountSettingsRoute
import com.yenaly.han1meviewer.OnAir.ui.navigation.OnAirCookieRoute
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.ui.navigation.settings.HomeSettingsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.NetworkSettingsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.PlayerSettingsRoute
import com.yenaly.han1meviewer.ui.navigation.settings.UnifiedHistoryRoute
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.yenaly.han1meviewer.FapFolder.ui.navigation.FapFolderFollowedCommunitiesRoute
import com.yenaly.han1meviewer.FapFolder.ui.navigation.FapFolderHistoryRoute
import com.yenaly.han1meviewer.FapFolder.ui.navigation.FapFolderSavedRoute
import com.yenaly.han1meviewer.FapFolder.ui.navigation.FapFolderSettingsRoute
import com.yenaly.han1meviewer.HentaiMama.ui.navigation.HentaiMamaHistoryRoute
import com.yenaly.han1meviewer.HentaiMama.ui.navigation.HentaiMamaPlaylistsRoute
import com.yenaly.han1meviewer.HentaiMama.ui.navigation.HentaiMamaRecentEpisodesRoute
import com.yenaly.han1meviewer.HentaiMama.ui.navigation.HentaiMamaSettingsHubRoute
import com.yenaly.han1meviewer.HentaiMama.ui.navigation.HentaiMamaUpcomingRoute
import com.yenaly.han1meviewer.MissAV.ui.navigation.MissAvHistoryRoute
import com.yenaly.han1meviewer.MissAV.ui.navigation.MissAvSettingsHubRoute
import com.yenaly.han1meviewer.OnAir.ui.navigation.OnAirAnnouncementsRoute
import com.yenaly.han1meviewer.OnAir.ui.navigation.OnAirChatRoute
import com.yenaly.han1meviewer.OnAir.ui.navigation.OnAirHistoryRoute
import com.yenaly.han1meviewer.OnAir.ui.navigation.OnAirPicturesRoute
import com.yenaly.han1meviewer.OnAir.ui.navigation.OnAirSettingsRoute
import com.yenaly.han1meviewer.OnAir.ui.navigation.OnAirTagsIndexRoute
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.ui.navigation.CrossSiteNavigator
import com.yenaly.han1meviewer.ui.navigation.NavigationManager
import com.yenaly.han1meviewer.ui.navigation.main.DailyCheckInRoute
import com.yenaly.han1meviewer.ui.navigation.navigateSafely

data class SiteDrawerEntry(
    val title: String,
    val iconRes: Int,
    val onClick: (NavHostController) -> Unit,
)

/** Login state shown in the drawer header instead of the Hanime profile card. */
data class SiteLoginStatus(
    val title: String,
    val subtitle: String,
    val avatarUrl: String?,
    val loggedIn: Boolean,
    val onClick: ((NavHostController) -> Unit)?,
)

private const val ONAIR_COOKIES_KEY = "onair_cookies_raw"

fun siteLoginStatus(site: SiteType): SiteLoginStatus = when (site) {
    SiteType.ONAIR -> {
        val profile = OnAirProfileCache.current()
        val raw = runCatching {
            Preferences.preferenceSp.getString(ONAIR_COOKIES_KEY, null)
        }.getOrNull().orEmpty()
        val loggedIn = profile != null || raw.contains("session=")
        if (loggedIn) {
            SiteLoginStatus(
                title = profile?.displayName?.takeIf { it.isNotBlank() } ?: "Logged in to OnAir",
                subtitle = profile?.username?.let { "@$it" } ?: "Tap for account & session",
                avatarUrl = profile?.avatarUrl,
                loggedIn = true,
                onClick = { it.navigateSafely(OnAirAccountSettingsRoute) },
            )
        } else {
            SiteLoginStatus(
                title = "Not logged in",
                subtitle = "Tap to log in or import cookies",
                avatarUrl = null,
                loggedIn = false,
                onClick = { it.navigateSafely(OnAirCookieRoute) },
            )
        }
    }

    SiteType.FAPFOLDER -> {
        val loggedIn = runCatching { FapFolderNetwork.hasAuthCookies() }.getOrDefault(false)
        if (loggedIn) {
            SiteLoginStatus(
                title = "Logged in to FapFolder",
                subtitle = "Tap to manage session cookies",
                avatarUrl = null,
                loggedIn = true,
                onClick = { it.navigateSafely(FapFolderCookiesRoute) },
            )
        } else {
            SiteLoginStatus(
                title = "Not logged in",
                subtitle = "Tap to sign in to FapFolder",
                avatarUrl = null,
                loggedIn = false,
                onClick = { it.navigateSafely(FapFolderLoginRoute) },
            )
        }
    }

    SiteType.MISSAV -> SiteLoginStatus(
        title = "Not logged in",
        subtitle = "Login is unavailable for MissAV",
        avatarUrl = null,
        loggedIn = false,
        onClick = null,
    )

    SiteType.HENTAIMAMA -> SiteLoginStatus(
        title = "Not logged in",
        subtitle = "Login is unavailable for HentaiMama",
        avatarUrl = null,
        loggedIn = false,
        onClick = null,
    )

    SiteType.HANIME, SiteType.JAVCHU -> SiteLoginStatus(
        title = "Not logged in",
        subtitle = "",
        avatarUrl = null,
        loggedIn = false,
        onClick = null,
    )
}

private fun entry(title: String, icon: Int, route: Any) =
    SiteDrawerEntry(title, icon) { nav -> nav.navigateSafely(route) }

/** Site specific entries (everything the old per-site settings hubs offered). */
fun siteDrawerEntries(site: SiteType, loggedIn: Boolean): List<SiteDrawerEntry> {
    val home = SiteDrawerEntry("Home", R.drawable.ic_baseline_home_24) {
        NavigationManager.navigateToHome()
    }
    val checkIn = SiteDrawerEntry("Did you cum?", R.drawable.ic_baseline_check_circle_24) {
        it.navigateSafely(DailyCheckInRoute)
    }
    val history = entryIcon(R.drawable.ic_baseline_history_24)
    return when (site) {
        SiteType.MISSAV -> listOf(
            home,
            checkIn,
            entry("History", history, MissAvHistoryRoute),
            entry("Home categories", R.drawable.baseline_grid_24, MissAvHomeCategoriesRoute),
            entry("Host / Mirror", R.drawable.baseline_domain_24, MissAvHostSettingsRoute),
        )

        SiteType.HENTAIMAMA -> listOf(
            home,
            checkIn,
            entry("History", history, HentaiMamaHistoryRoute),
            entry("Playlists", R.drawable.baseline_format_list_bulleted_24, HentaiMamaPlaylistsRoute),
            entry("Recent episodes", R.drawable.ic_baseline_update_24, HentaiMamaRecentEpisodesRoute),
            entry("Upcoming", R.drawable.ic_baseline_access_time_24, HentaiMamaUpcomingRoute),
            entry("Video & card size", R.drawable.ic_video_quilty, HentaiMamaVideoSettingsRoute(target = "all")),
            entry("Home categories", R.drawable.baseline_grid_24, HentaiMamaHomeSettingsRoute),
        )

        SiteType.ONAIR -> buildList {
            add(home)
            add(checkIn)
            add(entry("History", history, OnAirHistoryRoute))
            add(entry("Cookies & sign in", R.drawable.ic_baseline_cookie_24, OnAirCookieRoute))
            if (loggedIn) {
                add(entry("Account, host & session", R.drawable.ic_baseline_person_24, OnAirAccountSettingsRoute))
            }
            add(entry("Tags", R.drawable.baseline_flag_24, OnAirTagsIndexRoute))
            add(entry("Announcements", R.drawable.ic_baseline_newspaper_24, OnAirAnnouncementsRoute))
            add(entry("Chat", R.drawable.baseline_forum_24, OnAirChatRoute))
            add(entry("Pictures", R.drawable.ic_baseline_image_24, OnAirPicturesRoute))
        }

        SiteType.FAPFOLDER -> buildList {
            add(home)
            add(checkIn)
            add(entry("History", history, FapFolderHistoryRoute))
            add(entry("Cookies", R.drawable.ic_baseline_cookie_24, FapFolderCookiesRoute))
            if (!loggedIn) add(entry("Sign in", R.drawable.ic_baseline_unlock_24, FapFolderLoginRoute))
            add(entry("Search", R.drawable.ic_baseline_search_24, FapFolderSearchRoute()))
            add(entry("Search history", R.drawable.baseline_restore_24, FapFolderSearchHistoryRoute))
            add(entry("Followed communities", R.drawable.ic_subscribtion, FapFolderFollowedCommunitiesRoute))
            add(entry("Saved", R.drawable.ic_baseline_favorite_border_24, FapFolderSavedRoute))
        }

        SiteType.HANIME, SiteType.JAVCHU -> emptyList()
    }
}

private fun entryIcon(res: Int): Int = res

/** In-app settings shared by all sites (same ones the Hanime drawer links to). */
private fun appEntries(): List<SiteDrawerEntry> = listOf(
    entry("Settings", R.drawable.ic_baseline_settings_24, HomeSettingsRoute),
    entry("Player settings", R.drawable.baseline_player_24, PlayerSettingsRoute),
    entry("Network settings", R.drawable.baseline_mobile_data_24, NetworkSettingsRoute),
    entry("Watch history (all sites)", R.drawable.ic_baseline_watch_later_24, UnifiedHistoryRoute),
)

@Composable
fun SiteDrawerContent(
    site: SiteType,
    navController: NavHostController,
    onClose: () -> Unit,
    onSwitchSiteClick: () -> Unit,
    refreshKey: Any = Unit,
) {
    val status = androidx.compose.runtime.remember(site, refreshKey) { siteLoginStatus(site) }
    val siteEntries = siteDrawerEntries(site, status.loggedIn)
    val appEntries = appEntries()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SiteDrawerHeader(
            site = site,
            status = status,
            onStatusClick = status.onClick?.let { click ->
                {
                    onClose()
                    click(navController)
                }
            },
            onSwitchSiteClick = onSwitchSiteClick,
        )
        Spacer(Modifier.height(8.dp))
        siteEntries.forEach { entry ->
            DrawerItem(entry) {
                onClose()
                entry.onClick(navController)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
        appEntries.forEach { entry ->
            DrawerItem(entry) {
                onClose()
                entry.onClick(navController)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DrawerItem(entry: SiteDrawerEntry, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(entry.title) },
        icon = { Icon(painterResource(entry.iconRes), contentDescription = null) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}

@Composable
private fun SiteDrawerHeader(
    site: SiteType,
    status: SiteLoginStatus,
    onStatusClick: (() -> Unit)?,
    onSwitchSiteClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 12.dp, bottom = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = status.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .then(
                        if (onStatusClick != null) Modifier.clickable(onClick = onStatusClick)
                        else Modifier
                    ),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.bg_default_header),
                fallback = painterResource(id = R.drawable.bg_default_header),
                error = painterResource(id = R.drawable.bg_default_header),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 8.dp)
                    .then(
                        if (onStatusClick != null) Modifier.clickable(onClick = onStatusClick)
                        else Modifier
                    ),
            ) {
                Text(
                    text = status.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = status.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = CrossSiteNavigator.siteLabel(site),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier.clickable(onClick = onSwitchSiteClick),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_switch_24),
                    contentDescription = stringResource(R.string.switch_site),
                    modifier = Modifier.padding(6.dp),
                )
                Text(
                    text = stringResource(R.string.switch_site),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
