package com.yenaly.han1meviewer.ui.screen.main

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
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

data class SiteLoginStatus(
    val title: String,
    val subtitle: String,
    val avatarUrl: String?,
    val loggedIn: Boolean,
    val onClick: ((NavHostController) -> Unit)?,
)

private const val ONAIR_COOKIES_KEY = "onair_cookies_raw"

fun siteLoginStatus(context: Context, site: SiteType): SiteLoginStatus = when (site) {
    SiteType.ONAIR -> {
        val profile = OnAirProfileCache.current()
        val raw = runCatching {
            Preferences.preferenceSp.getString(ONAIR_COOKIES_KEY, null)
        }.getOrNull().orEmpty()
        val loggedIn = profile != null || raw.contains("session=")
        if (loggedIn) {
            SiteLoginStatus(
                title = profile?.displayName?.takeIf { it.isNotBlank() } ?: context.getString(R.string.login_status_logged_in_site, CrossSiteNavigator.siteLabel(SiteType.ONAIR)),
                subtitle = profile?.username?.let { "@$it" } ?: context.getString(R.string.login_status_tap_account),
                avatarUrl = profile?.avatarUrl,
                loggedIn = true,
                onClick = { it.navigateSafely(OnAirAccountSettingsRoute) },
            )
        } else {
            SiteLoginStatus(
                title = context.getString(R.string.not_logged_in),
                subtitle = context.getString(R.string.login_status_tap_login_cookies),
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
                title = context.getString(R.string.login_status_logged_in_site, CrossSiteNavigator.siteLabel(SiteType.FAPFOLDER)),
                subtitle = context.getString(R.string.login_status_tap_manage_cookies),
                avatarUrl = null,
                loggedIn = true,
                onClick = { it.navigateSafely(FapFolderCookiesRoute) },
            )
        } else {
            SiteLoginStatus(
                title = context.getString(R.string.not_logged_in),
                subtitle = context.getString(R.string.login_status_tap_sign_in, CrossSiteNavigator.siteLabel(SiteType.FAPFOLDER)),
                avatarUrl = null,
                loggedIn = false,
                onClick = { it.navigateSafely(FapFolderLoginRoute) },
            )
        }
    }

    SiteType.MISSAV -> SiteLoginStatus(
        title = context.getString(R.string.not_logged_in),
        subtitle = context.getString(R.string.login_status_unavailable, CrossSiteNavigator.siteLabel(SiteType.MISSAV)),
        avatarUrl = null,
        loggedIn = false,
        onClick = null,
    )

    SiteType.HENTAIMAMA -> SiteLoginStatus(
        title = context.getString(R.string.not_logged_in),
        subtitle = context.getString(R.string.login_status_unavailable, CrossSiteNavigator.siteLabel(SiteType.HENTAIMAMA)),
        avatarUrl = null,
        loggedIn = false,
        onClick = null,
    )

    SiteType.HANIME, SiteType.JAVCHU -> SiteLoginStatus(
        title = context.getString(R.string.not_logged_in),
        subtitle = "",
        avatarUrl = null,
        loggedIn = false,
        onClick = null,
    )
}

private fun entry(title: String, icon: Int, route: Any) =
    SiteDrawerEntry(title, icon) { nav -> nav.navigateSafely(route) }

data class SiteDrawerSection(val title: String, val entries: List<SiteDrawerEntry>)

fun siteDrawerSections(context: Context, site: SiteType, loggedIn: Boolean): List<SiteDrawerSection> {
    fun str(res: Int) = context.getString(res)
    val home = SiteDrawerEntry(str(R.string.home_page), R.drawable.ic_baseline_home_24) {
        NavigationManager.navigateToHome()
    }
    val checkIn = SiteDrawerEntry(str(R.string.has_cum), R.drawable.ic_baseline_check_circle_24) {
        it.navigateSafely(DailyCheckInRoute)
    }
    val history = R.drawable.ic_baseline_history_24
    val browse = str(R.string.site_drawer_section_browse)
    val library = str(R.string.site_drawer_section_library)
    val account = str(R.string.site_drawer_section_account)
    val customize = str(R.string.site_drawer_section_customize)

    return when (site) {
        SiteType.MISSAV -> listOf(
            SiteDrawerSection(browse, listOf(home, checkIn)),
            SiteDrawerSection(
                library,
                listOf(entry(str(R.string.watch_history), history, MissAvHistoryRoute)),
            ),
            SiteDrawerSection(
                customize,
                listOf(
                    entry(str(R.string.drawer_home_categories), R.drawable.baseline_grid_24, MissAvHomeCategoriesRoute),
                    entry(str(R.string.drawer_host_mirror), R.drawable.baseline_domain_24, MissAvHostSettingsRoute),
                ),
            ),
        )

        SiteType.HENTAIMAMA -> listOf(
            SiteDrawerSection(
                browse,
                listOf(
                    home,
                    checkIn,
                    entry(str(R.string.drawer_recent_episodes), R.drawable.ic_baseline_update_24, HentaiMamaRecentEpisodesRoute),
                    entry(str(R.string.drawer_upcoming), R.drawable.ic_baseline_access_time_24, HentaiMamaUpcomingRoute),
                ),
            ),
            SiteDrawerSection(
                library,
                listOf(
                    entry(str(R.string.watch_history), history, HentaiMamaHistoryRoute),
                    entry(str(R.string.play_list), R.drawable.baseline_format_list_bulleted_24, HentaiMamaPlaylistsRoute),
                ),
            ),
            SiteDrawerSection(
                customize,
                listOf(
                    entry(str(R.string.drawer_video_card_size), R.drawable.ic_drawer_card_size, HentaiMamaVideoSettingsRoute(target = "all")),
                    entry(str(R.string.drawer_home_categories), R.drawable.baseline_grid_24, HentaiMamaHomeSettingsRoute),
                ),
            ),
        )

        SiteType.ONAIR -> listOf(
            SiteDrawerSection(
                browse,
                listOf(
                    home,
                    checkIn,
                    entry(str(R.string.drawer_tags), R.drawable.ic_drawer_label, OnAirTagsIndexRoute),
                    entry(str(R.string.drawer_pictures), R.drawable.ic_baseline_image_24, OnAirPicturesRoute),
                    entry(str(R.string.drawer_chat), R.drawable.baseline_forum_24, OnAirChatRoute),
                    entry(str(R.string.announcement), R.drawable.ic_baseline_newspaper_24, OnAirAnnouncementsRoute),
                ),
            ),
            SiteDrawerSection(
                library,
                listOf(entry(str(R.string.watch_history), history, OnAirHistoryRoute)),
            ),
            SiteDrawerSection(
                account,
                buildList {
                    add(entry(str(R.string.drawer_cookies_sign_in), R.drawable.ic_baseline_cookie_24, OnAirCookieRoute))
                    if (loggedIn) {
                        add(entry(str(R.string.drawer_account_session), R.drawable.ic_baseline_person_24, OnAirAccountSettingsRoute))
                    }
                },
            ),
        )

        SiteType.FAPFOLDER -> listOf(
            SiteDrawerSection(
                browse,
                listOf(
                    home,
                    checkIn,
                    entry(str(R.string.search), R.drawable.ic_baseline_search_24, FapFolderSearchRoute()),
                    entry(str(R.string.drawer_search_history), R.drawable.ic_drawer_search_history, FapFolderSearchHistoryRoute),
                ),
            ),
            SiteDrawerSection(
                library,
                listOf(
                    entry(str(R.string.watch_history), history, FapFolderHistoryRoute),
                    entry(str(R.string.saved), R.drawable.ic_drawer_bookmark, FapFolderSavedRoute),
                    entry(str(R.string.drawer_followed_communities), R.drawable.ic_drawer_groups, FapFolderFollowedCommunitiesRoute),
                ),
            ),
            SiteDrawerSection(
                account,
                buildList {
                    if (!loggedIn) add(entry(str(R.string.login), R.drawable.ic_baseline_unlock_24, FapFolderLoginRoute))
                    add(entry(str(R.string.cookies_label), R.drawable.ic_baseline_cookie_24, FapFolderCookiesRoute))
                },
            ),
        )

        SiteType.HANIME, SiteType.JAVCHU -> emptyList()
    }
}

private fun appEntries(context: Context): List<SiteDrawerEntry> = listOf(
    entry(context.getString(R.string.settings), R.drawable.ic_baseline_settings_24, HomeSettingsRoute),
    entry(context.getString(R.string.player_settings), R.drawable.baseline_player_24, PlayerSettingsRoute),
    entry(context.getString(R.string.network_settings), R.drawable.baseline_mobile_data_24, NetworkSettingsRoute),
    entry(context.getString(R.string.drawer_watch_history_all), R.drawable.ic_drawer_library, UnifiedHistoryRoute),
)

@Composable
fun SiteDrawerContent(
    site: SiteType,
    navController: NavHostController,
    onClose: () -> Unit,
    onSwitchSiteClick: () -> Unit,
    refreshKey: Any = Unit,
) {
    val context = LocalContext.current
    val status = androidx.compose.runtime.remember(site, refreshKey) { siteLoginStatus(context, site) }
    val sections = siteDrawerSections(context, site, status.loggedIn) +
            SiteDrawerSection(
                context.getString(R.string.site_drawer_section_app),
                appEntries(context),
            )

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
        sections.forEach { section ->
            if (section.entries.isEmpty()) return@forEach
            DrawerSectionHeader(section.title)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    section.entries.forEach { entry ->
                        DrawerItem(entry) {
                            onClose()
                            entry.onClick(navController)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun DrawerSectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun DrawerItem(entry: SiteDrawerEntry, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = { Icon(painterResource(entry.iconRes), contentDescription = null) },
        selected = false,
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = Color.Transparent,
            unselectedIconColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier.padding(horizontal = 4.dp),
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
