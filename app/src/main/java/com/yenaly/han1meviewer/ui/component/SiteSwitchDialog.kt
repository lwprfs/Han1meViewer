package com.yenaly.han1meviewer.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.ui.theme.SpacingLarge
import com.yenaly.han1meviewer.ui.theme.SpacingNormal
import com.yenaly.han1meviewer.util.applicationContext

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
    val current = visibleOptions.firstOrNull { it.siteType == currentSite }
    val others = visibleOptions.filter { it.siteType != currentSite }

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
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(width = 4.dp, height = 22.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary,
                                    )
                                )
                            ),
                    )
                    Spacer(Modifier.width(SpacingNormal))
                    Text(
                        text = stringResource(R.string.site_switch_site),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = "${visibleOptions.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.site_tap_a_source_to_jump_straight),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            if (current != null) {
                SiteSwitchCurrentCard(option = current, onClick = onDismiss)
            }

            others.forEach { option ->
                SiteSwitchRow(
                    option = option,
                    onClick = {
                        onSiteSelected(option.siteType)
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun SiteLogo(option: SiteSwitchOption, boxSize: Dp, logoSize: Dp) {
    Box(
        modifier = Modifier
            .size(boxSize)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (option.logoRes != null) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.primaryContainer
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (option.logoRes != null) {
            Image(
                painter = painterResource(option.logoRes),
                contentDescription = option.name,
                modifier = Modifier
                    .size(logoSize)
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
}

@Composable
private fun SiteSwitchCurrentCard(
    option: SiteSwitchOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.5.dp, option.accentColor.copy(alpha = 0.7f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingLarge),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpacingLarge),
        ) {
            SiteLogo(option, boxSize = 56.dp, logoSize = 42.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.site_current_site),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SiteSwitchRow(
    option: SiteSwitchOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SiteLogo(option, boxSize = 48.dp, logoSize = 36.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
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
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(option.accentColor),
            )
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
