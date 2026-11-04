package com.yenaly.han1meviewer.FapFolder.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderAccountRepo
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderAuthRepo
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetwork
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteIconBadge
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import kotlinx.coroutines.launch

private data class AccountRow(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FapFolderAccountScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenRegister: () -> Unit,
    onOpenCookies: () -> Unit,
    onOpenMyProfile: (String) -> Unit,
    onOpenAccountSettings: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenFollowed: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenHistory: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var viewer by remember { mutableStateOf<FapFolderAccountRepo.Viewer?>(null) }
    var loading by remember { mutableStateOf(true) }
    var signingOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loading = true
        viewer = FapFolderAccountRepo.fetchViewer()
        loading = false
    }

    val loggedIn = viewer?.loggedIn == true || (viewer == null && FapFolderNetwork.hasAuthCookies())

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text(stringResource(R.string.site_my_account)) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { paddingValues ->
        when {
            loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            !loggedIn -> SignedOutContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onOpenLogin = onOpenLogin,
                onOpenRegister = onOpenRegister,
                onOpenCookies = onOpenCookies,
            )

            else -> {
                val username = viewer?.username
                val rows = buildList {
                    if (!username.isNullOrBlank()) {
                        add(
                            AccountRow(
                                stringResource(R.string.fap_my_profile), stringResource(R.string.fap_view_your_public_page),
                                Icons.Filled.Person,
                            ) { onOpenMyProfile(username) }
                        )
                    }
                    add(
                        AccountRow(
                            stringResource(R.string.fap_account_settings), stringResource(R.string.fap_email_username_and_password_check),
                            Icons.Filled.Settings, onOpenAccountSettings,
                        )
                    )
                    add(
                        AccountRow(
                            stringResource(R.string.privacy), "Who can see your info, photos and groups",
                            Icons.Filled.Lock, onOpenPrivacy,
                        )
                    )
                    add(
                        AccountRow(
                            stringResource(R.string.drawer_followed_communities), "Groups you follow",
                            Icons.Filled.Groups, onOpenFollowed,
                        )
                    )
                    add(
                        AccountRow(
                            "Saved posts", "Posts you bookmarked",
                            Icons.Filled.Bookmark, onOpenSaved,
                        )
                    )
                    add(
                        AccountRow(
                            "Watch history", stringResource(R.string.fap_posts_and_videos_you_opened),
                            Icons.Filled.History, onOpenHistory,
                        )
                    )
                    add(
                        AccountRow(
                            "Cookies & session", "Import, view or clear session cookies",
                            Icons.Filled.Cookie, onOpenCookies,
                        )
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item(key = "header") {
                        AccountHeader(
                            username = username,
                            avatarUrl = viewer?.avatarUrl,
                        )
                    }
                    items(rows.size) { index -> AccountRowItem(rows[index]) }
                    item(key = "signout") {
                        OutlinedButton(
                            onClick = {
                                if (signingOut) return@OutlinedButton
                                signingOut = true
                                scope.launch {
                                    FapFolderAuthRepo.logout()
                                    signingOut = false
                                    onLoggedOut()
                                }
                            },
                            enabled = !signingOut,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = if (signingOut) stringResource(R.string.fap_signing_out) else stringResource(R.string.fap_sign_out),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountHeader(username: String?, avatarUrl: String?) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = username,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = username?.firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            Column {
                Text(
                    text = username ?: stringResource(R.string.site_signed_in),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "fapfolder.club",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun AccountRowItem(row: AccountRow) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = row.onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SiteIconBadge(imageVector = row.icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = row.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SignedOutContent(
    modifier: Modifier,
    onOpenLogin: () -> Unit,
    onOpenRegister: () -> Unit,
    onOpenCookies: () -> Unit,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.fap_you_are_not_signed_in),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = stringResource(R.string.fap_sign_in_or_create_a_free),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )
        Button(onClick = onOpenLogin, modifier = Modifier.fillMaxWidth()) {
            Icon(
                Icons.AutoMirrored.Filled.Login,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(stringResource(R.string.site_sign_in), modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedButton(
            onClick = onOpenRegister,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Icon(
                Icons.Filled.PersonAdd,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(stringResource(R.string.site_create_account), modifier = Modifier.padding(start = 8.dp))
        }
        TextButton(onClick = onOpenCookies, modifier = Modifier.padding(top = 4.dp)) {
            Text(stringResource(R.string.fap_paste_cookies_instead))
        }
    }
}
