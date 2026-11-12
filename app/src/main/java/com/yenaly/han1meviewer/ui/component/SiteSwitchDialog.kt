package com.yenaly.han1meviewer.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.ui.theme.SpacingLarge
import com.yenaly.han1meviewer.ui.theme.SpacingNormal
import com.yenaly.yenaly_libs.utils.applicationContext

data class SiteSwitchOption(
    val name: String,
    val description: String,
    val siteType: SiteType,
    @DrawableRes val logoRes: Int?,

    val accentColor: Color,
)

val DefaultSiteSwitchOptions: List<SiteSwitchOption> = listOf(
    SiteSwitchOption(
        name = "Hanime",
        description = applicationContext.getString(R.string.site_main_site_with_h_anime),
        siteType = SiteType.HANIME,
        logoRes = R.drawable.s_switch_han1me,
        accentColor = Color(0xFFE53935),
    ),
    SiteSwitchOption(
        name = "MissAV",
        description = applicationContext.getString(R.string.site_jav_streaming_platform),
        siteType = SiteType.MISSAV,
        logoRes = R.drawable.s_switch_missav,
        accentColor = Color(0xFF1E88E5),
    ),
    SiteSwitchOption(
        name = "HentaiMama",
        description = applicationContext.getString(R.string.site_hentai_streaming_site),
        siteType = SiteType.HENTAIMAMA,
        logoRes = R.drawable.s_switch_hentaimama,
        accentColor = Color(0xFF8E24AA),
    ),
    SiteSwitchOption(
        name = "Javchu",
        description = applicationContext.getString(R.string.site_jav_library_fixed_url),
        siteType = SiteType.JAVCHU,
        logoRes = R.drawable.s_switch_javchu,
        accentColor = Color(0xFFFB8C00),
    ),
    SiteSwitchOption(
        name = "OnAir",
        description = applicationContext.getString(R.string.site_self_hosted_onion_video_site),
        siteType = SiteType.ONAIR,
        logoRes = R.drawable.s_switch_onair,
        accentColor = Color(0xFF00897B),
    ),
    SiteSwitchOption(
        name = "FapFolder",
        description = applicationContext.getString(R.string.site_community_feed_albums_and_videos),
        siteType = SiteType.FAPFOLDER,
        logoRes = R.drawable.s_switch_fapfolder,
        accentColor = Color(0xFFD81B60),
    ),
    SiteSwitchOption(
        name = "SupJav",
        description = applicationContext.getString(R.string.site_supjav_description),
        siteType = SiteType.SUPJAV,
        logoRes = R.drawable.s_switch_supjav,
        accentColor = Color(0xFF5E35B1),
    ),
    SiteSwitchOption(
        name = applicationContext.getString(R.string.site_rouvideo_name),
        description = applicationContext.getString(R.string.site_rouvideo_description),
        siteType = SiteType.ROUVIDEO,
        logoRes = R.drawable.s_switch_rouvideo,
        accentColor = Color(0xFFE53935),
    ),
    SiteSwitchOption(
        name = applicationContext.getString(R.string.site_xhamster_name),
        description = applicationContext.getString(R.string.site_xhamster_description),
        siteType = SiteType.XHAMSTER,
        logoRes = R.drawable.s_switch_xhamster,
        accentColor = Color(0xFFEF6C00),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun SiteSwitchDialog(
    currentSite: SiteType,
    onDismiss: () -> Unit,
    onSiteSelected: (SiteType) -> Unit,
    modifier: Modifier = Modifier,
    options: List<SiteSwitchOption> = DefaultSiteSwitchOptions,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val visibleOptions = options.filter {
        it.siteType == currentSite || Preferences.isSiteEnabled(it.siteType)
    }

    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = SpacingLarge)
                .padding(bottom = SpacingLarge),
            verticalArrangement = Arrangement.spacedBy(SpacingNormal),
        ) {
            Column(modifier = Modifier.padding(bottom = SpacingNormal)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(width = 4.dp, height = 20.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(SpacingNormal))
                    Text(
                        text = stringResource(R.string.site_switch_site),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = stringResource(R.string.site_tap_a_source_to_jump_straight),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            visibleOptions.chunked(2).forEach { rowOptions ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(SpacingNormal),
                ) {
                    rowOptions.forEach { option ->
                        SiteSwitchTile(
                            option = option,
                            isCurrent = option.siteType == currentSite,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onClick = {
                                if (option.siteType != currentSite) {
                                    onSiteSelected(option.siteType)
                                }
                                onDismiss()
                            },
                        )
                    }
                    if (rowOptions.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SiteSwitchTile(
    option: SiteSwitchOption,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {

    val shape = RoundedCornerShape(20.dp)

    Surface(
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = if (isCurrent) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (isCurrent) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingLarge),
            verticalArrangement = Arrangement.spacedBy(SpacingNormal),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (option.logoRes != null) {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (option.logoRes != null) {
                        Image(
                            painter = painterResource(option.logoRes),
                            contentDescription = option.name,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(
                            text = option.name.take(1),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                if (isCurrent) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.site_current_site),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column {
                Text(
                    text = option.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun SiteSwitchConfirmDialog(
    title: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        },
    )
}
