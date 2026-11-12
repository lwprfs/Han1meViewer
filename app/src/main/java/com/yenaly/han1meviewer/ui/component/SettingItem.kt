package com.yenaly.han1meviewer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.ui.preview.ComponentPreview
import kotlin.math.roundToInt

private val TileShape = RoundedCornerShape(20.dp)
private val BadgeShape = RoundedCornerShape(12.dp)

/**
 * Section label shown above a group of setting tiles.
 * Shared by every settings screen so headers look identical everywhere.
 */
@Composable
fun SettingSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** Rounded tonal square that holds a setting icon. */
@Composable
fun SettingIconBadge(
    iconRes: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SettingIconBadgeBox(modifier, enabled) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun SettingIconBadge(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SettingIconBadgeBox(modifier, enabled) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SettingIconBadgeBox(
    modifier: Modifier,
    enabled: Boolean,
    icon: @Composable () -> Unit,
) {
    val container = MaterialTheme.colorScheme.secondaryContainer
    val content = MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(BadgeShape)
            .background(if (enabled) container else container.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides
                    if (enabled) content else content.copy(alpha = 0.5f),
            content = icon,
        )
    }
}

/**
 * Rounded card-like container used by every setting row so that rows
 * stacked in a LazyColumn read as a tidy list of tiles.
 */
@Composable
fun SettingTile(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val tileModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 2.dp)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = tileModifier,
            shape = TileShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            content = content,
        )
    } else {
        Surface(
            modifier = tileModifier,
            shape = TileShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            content = content,
        )
    }
}

@Composable
private fun SettingTexts(
    title: String,
    summary: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.38f
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
        if (!summary.isNullOrBlank()) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
fun SettingSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    iconRes: Int? = null,
    enabled: Boolean = true,
) {
    SettingTile(
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            iconRes?.let { SettingIconBadge(it, enabled = enabled) }
            SettingTexts(title, summary, Modifier.weight(1f), enabled)
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
    }
}

@Composable
fun SettingNavigationItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    valueText: String? = null,
    iconRes: Int? = null,
    enabled: Boolean = true,
) {
    SettingTile(modifier = modifier, onClick = onClick, enabled = enabled) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            iconRes?.let { SettingIconBadge(it, enabled = enabled) }
            SettingTexts(title, summary, Modifier.weight(1f), enabled)
            if (!valueText.isNullOrBlank()) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.6f, fill = false),
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SettingInfoItem(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    valueText: String? = null,
    iconRes: Int? = null,
) {
    SettingTile(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            iconRes?.let { SettingIconBadge(it) }
            SettingTexts(title, summary, Modifier.weight(1f))
            if (!valueText.isNullOrBlank()) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SettingSliderItem(
    title: String,
    value: Int,
    valueRange: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 1,
    summary: String? = null,
    iconRes: Int? = null,
) {
    val totalSteps = if (step > 0) {
        ((valueRange.last - valueRange.first) / step) - 1
    } else {
        0
    }
    SettingTile(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                iconRes?.let { SettingIconBadge(it) }
                SettingTexts(title, summary, Modifier.weight(1f))
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.roundToInt()) },
                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                steps = totalSteps.coerceAtLeast(0),
            )
        }
    }
}

/**
 * Selectable row used inside dialogs and sheets. The selected row gets a
 * tonal highlight instead of only a trailing check mark.
 */
@Composable
fun SettingChoiceItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    iconRes: Int? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else androidx.compose.ui.graphics.Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            iconRes?.let {
                Icon(
                    painter = painterResource(it),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (!summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingSwitchItemPreview() {
    ComponentPreview {
        SettingSwitchItem(
            title = "启用动态取色",
            summary = "Android 12 及以上可使用系统取色",
            checked = true,
            onCheckedChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingNavigationItemPreview() {
    ComponentPreview {
        SettingNavigationItem(
            title = "播放器内核",
            summary = "当前使用 ExoPlayer",
            valueText = "ExoPlayer",
            onClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingInfoItemPreview() {
    ComponentPreview {
        SettingInfoItem(
            title = "当前版本",
            summary = "Han1meViewer",
            valueText = "v1.0.0",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingSliderItemPreview() {
    ComponentPreview {
        SettingSliderItem(
            title = "检查更新跳出视窗间隔天数",
            summary = "7天\n最近还没跳出过更新视窗哦",
            value = 7,
            valueRange = 0..30,
            onValueChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingChoiceItemPreview() {
    ComponentPreview {
        SettingChoiceItem(
            title = "跟随系统",
            selected = true,
            onClick = {},
        )
    }
}
