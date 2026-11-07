package com.yenaly.han1meviewer.OnAir.ui.settings

import androidx.compose.ui.res.stringResource
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.OnAir.common.OnAirConstants
import com.yenaly.han1meviewer.OnAir.common.OnAirSitePreferences
import com.yenaly.han1meviewer.OnAir.data.local.OnAirHistoryRepo
import com.yenaly.han1meviewer.OnAir.data.local.OnAirProfileCache
import com.yenaly.han1meviewer.OnAir.data.model.OnAirUserProfile
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirAuthRepo
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirCookieManager
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetwork
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetworkRepo
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import com.yenaly.yenaly_libs.utils.applicationContext
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnAirSettingsScreen(
    onBack: () -> Unit,
    onOpenCookies: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenTags: () -> Unit,
    onLoggedOut: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var showHostDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var loggingOut by remember { mutableStateOf(false) }
    var currentHost by remember { mutableStateOf(OnAirSitePreferences.currentHostname()) }
    val hasCookies = OnAirCookieManager.hasCookies
    var profile by remember { mutableStateOf<OnAirUserProfile?>(OnAirProfileCache.current()) }
    var profileLoading by remember { mutableIntStateOf(0) }
    val usernameHint = remember(hasCookies) { OnAirAuthRepo.currentUsernameHint() }

    fun refreshProfile() {
        val name = profile?.username
            ?: OnAirProfileCache.getCurrentUsername()
            ?: usernameHint
            ?: return
        profileLoading += 1
        MainScope().launch {
            OnAirNetworkRepo.getUserProfile(name).collect { s ->
                when (s) {
                    is VideoLoadingState.Success -> {
                        profile = s.info
                        OnAirProfileCache.put(s.info.username, s.info)
                    }
                    else -> Unit
                }
                profileLoading = (profileLoading - 1).coerceAtLeast(0)
            }
        }
    }

    LaunchedEffect(hasCookies) {
        if (hasCookies && profile == null) refreshProfile()
    }

    fun performLogout() {
        loggingOut = true
        scope.launch {
            val result = OnAirAuthRepo.performLogout()
            loggingOut = false
            showLogoutDialog = false
            OnAirProfileCache.clearAll()
            profile = null
            when (result) {
                is OnAirAuthRepo.AuthResult.Success -> {
                    GlobalToasts.show(applicationContext.getString(R.string.site_logged_out), level = GlobalToasts.ToastLevel.SUCCESS)
                    onLoggedOut()
                }
                is OnAirAuthRepo.AuthResult.FormError,
                is OnAirAuthRepo.AuthResult.NetworkError,
                is OnAirAuthRepo.AuthResult.LoginRequired -> {
                    GlobalToasts.show(
                        applicationContext.getString(R.string.onair_session_cleared_locally),
                        level = GlobalToasts.ToastLevel.WARNING,
                    )
                    onLoggedOut()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text(stringResource(R.string.onair_onair_settings)) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = profile != null) {
                                        profile?.let { onOpenProfile(it.username) }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                val avatar = profile?.avatarUrl
                                if (!avatar.isNullOrBlank()) {
                                    AsyncImage(
                                        model = avatar,
                                        contentDescription = profile?.displayName,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            val label = profile?.displayName?.firstOrNull()?.uppercase()
                                                ?: usernameHint?.firstOrNull()?.uppercase()
                                                ?: "?"
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = profile?.displayName
                                            ?: usernameHint
                                            ?: if (hasCookies) stringResource(R.string.site_signed_in) else stringResource(R.string.site_not_signed_in),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (profileLoading > 0) {
                                        Spacer(Modifier.width(8.dp))
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    }
                                }
                                val subline = buildString {
                                    profile?.let { p ->
                                        p.followerCount?.let { append(stringResource(R.string.onair_followers, it) + " ") }
                                        p.videoCount?.let { append(stringResource(R.string.onair_videos, it) + " ") }
                                        p.pictureCount?.let { append(stringResource(R.string.onair_pictures, it)) }
                                    }
                                    if (isEmpty()) {
                                        append(
                                            if (hasCookies) stringResource(R.string.onair_session_active_short)
                                            else stringResource(R.string.onair_no_cookies_device)
                                        )
                                    } else {
                                        trimEnd(' ', '·')
                                    }
                                }
                                Text(
                                    text = subline,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (hasCookies) {
                                IconButton(onClick = { refreshProfile() }) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = stringResource(R.string.onair_refresh_profile),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                IconButton(onClick = { showLogoutDialog = true }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = stringResource(R.string.logout),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = onOpenCookies,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(if (hasCookies) stringResource(R.string.onair_manage_cookies) else stringResource(R.string.onair_add_cookies))
                            }
                            if (!hasCookies) {
                                OutlinedButton(
                                    onClick = onOpenLogin,
                                    modifier = Modifier.weight(1f),
                                ) { Text(stringResource(R.string.site_log_in)) }
                            } else if (profile != null) {
                                OutlinedButton(
                                    onClick = { profile?.let { onOpenProfile(it.username) } },
                                    modifier = Modifier.weight(1f),
                                ) { Text(stringResource(R.string.onair_view_my_profile)) }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.site_drawer_section_browse),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.onair_announcements_chat_and_tags_moved_here),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onOpenAnnouncements)
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.onair_announcements_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.onair_site_news_and_mirror_updates),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onOpenChat)
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.drawer_chat),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.onair_site_wide_community_chat),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onOpenTags)
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.drawer_tags),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.onair_browse_all_tags),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.onair_mirror_host),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.onair_onair_is_a_tor_only_site),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = currentHost,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = { showHostDialog = true }) {
                            Text(stringResource(R.string.onair_change_host))
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.site_history),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.onair_clear_all_watch_history_stored_on),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = { showClearHistoryDialog = true }) {
                            Text(stringResource(R.string.site_clear_history))
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.onair_about_onair),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.onair_self_hosted_video_site_run_by),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showHostDialog) {
        AlertDialog(
            onDismissRequest = { showHostDialog = false },
            title = { Text(stringResource(R.string.onair_choose_onair_host)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OnAirConstants.ONAIR_HOSTNAME.forEachIndexed { index, host ->
                        val selected = currentHost == host
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    OnAirSitePreferences.setMirrorIndex(index)
                                    currentHost = host
                                    OnAirNetwork.rebuildNetwork()
                                    showHostDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(host, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHostDialog = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.site_clear_history)) },
            text = { Text(stringResource(R.string.onair_this_will_remove_all_onair_watch)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearHistoryDialog = false
                    MainScope().launch { OnAirHistoryRepo.deleteAll() }
                }) { Text(stringResource(R.string.site_clear), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { if (!loggingOut) showLogoutDialog = false },
            title = { Text(stringResource(R.string.onair_log_out_of_onair)) },
            text = { Text(stringResource(R.string.site_your_session_cookies_will_be_removed)) },
            confirmButton = {
                TextButton(
                    onClick = { performLogout() },
                    enabled = !loggingOut,
                ) {
                    if (loggingOut) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.logout), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false },
                    enabled = !loggingOut,
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
