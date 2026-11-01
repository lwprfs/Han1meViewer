package com.yenaly.han1meviewer.ui.screen.main

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

/** Site specific drawer entries; replaces the Hanime drawer while another site is active. */
fun siteDrawerEntries(site: SiteType): List<SiteDrawerEntry> {
    val home = SiteDrawerEntry("Home", R.drawable.ic_baseline_home_24) {
        NavigationManager.navigateToHome()
    }
    val checkIn = SiteDrawerEntry("Did you cum?", R.drawable.ic_baseline_thumb_up_alt_24) {
        it.navigateSafely(DailyCheckInRoute)
    }
    fun entry(title: String, icon: Int, route: Any) =
        SiteDrawerEntry(title, icon) { nav -> nav.navigateSafely(route) }

    val history = R.drawable.ic_baseline_history_24
    val list = R.drawable.ic_baseline_list_24
    val settings = R.drawable.ic_baseline_settings_24
    return when (site) {
        SiteType.MISSAV -> listOf(
            home,
            entry("Settings", settings, MissAvSettingsHubRoute),
            checkIn,
            entry("History", history, MissAvHistoryRoute),
        )
        SiteType.HENTAIMAMA -> listOf(
            home,
            entry("Settings", settings, HentaiMamaSettingsHubRoute),
            checkIn,
            entry("History", history, HentaiMamaHistoryRoute),
            entry("Upcoming", list, HentaiMamaUpcomingRoute),
            entry("Recent episodes", list, HentaiMamaRecentEpisodesRoute),
            entry("Playlists", list, HentaiMamaPlaylistsRoute),
        )
        SiteType.ONAIR -> listOf(
            home,
            entry("Settings", settings, OnAirSettingsRoute),
            checkIn,
            entry("History", history, OnAirHistoryRoute),
            entry("Announcements", list, OnAirAnnouncementsRoute),
            entry("Tags", list, OnAirTagsIndexRoute),
            entry("Pictures", list, OnAirPicturesRoute),
            entry("Chat", list, OnAirChatRoute),
        )
        SiteType.FAPFOLDER -> listOf(
            home,
            entry("Settings", settings, FapFolderSettingsRoute),
            checkIn,
            entry("History", history, FapFolderHistoryRoute),
            entry("Saved", list, FapFolderSavedRoute),
            entry("Followed communities", list, FapFolderFollowedCommunitiesRoute),
        )
        SiteType.HANIME, SiteType.JAVCHU -> emptyList()
    }
}

@Composable
fun SiteDrawerContent(
    site: SiteType,
    entries: List<SiteDrawerEntry>,
    onEntryClick: (SiteDrawerEntry) -> Unit,
    onSwitchSiteClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            text = CrossSiteNavigator.siteLabel(site),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp),
        )
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        entries.forEach { entry ->
            NavigationDrawerItem(
                label = { Text(entry.title) },
                icon = { Icon(painterResource(entry.iconRes), contentDescription = entry.title) },
                selected = false,
                onClick = { onEntryClick(entry) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        NavigationDrawerItem(
            label = { Text(androidx.compose.ui.res.stringResource(R.string.switch_site)) },
            icon = {
                Icon(painterResource(R.drawable.ic_baseline_switch_24), contentDescription = null)
            },
            selected = false,
            onClick = onSwitchSiteClick,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        Spacer(Modifier.height(16.dp))
    }
}
