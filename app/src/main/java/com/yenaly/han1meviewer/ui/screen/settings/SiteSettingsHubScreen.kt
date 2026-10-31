package com.yenaly.han1meviewer.ui.screen.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.component.ChoiceDialog
import com.yenaly.han1meviewer.ui.component.SettingNavigationItem
import com.yenaly.han1meviewer.ui.component.SettingSwitchItem
import com.yenaly.han1meviewer.ui.navigation.CrossSiteNavigator
import com.yenaly.han1meviewer.ui.navigation.settings.SettingsPreferenceKeys
import com.yenaly.han1meviewer.ui.navigation.settings.saveBoolean
import com.yenaly.han1meviewer.ui.navigation.settings.saveInt
import com.yenaly.han1meviewer.ui.navigation.settings.saveString
import com.yenaly.han1meviewer.ui.screen.settings.dialog.HorizontalCardCountDialog
import com.yenaly.han1meviewer.ui.screen.settings.dialog.SearchGridColumnsDialog
import com.yenaly.han1meviewer.ui.theme.ThemeColorPreset
import com.yenaly.han1meviewer.util.ThemeUtils

data class SiteSettingsEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

private const val APP_LANGUAGE_KEY = "app_language"

private enum class HubDialog { DarkMode, ThemeColor, AppLanguage, GridColumns, CardCount }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteSettingsHubScreen(
    activity: MainActivity,
    site: SiteType,
    siteEntries: List<SiteSettingsEntry>,
    onBack: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenPlayerSettings: () -> Unit,
    onOpenNetworkSettings: () -> Unit,
    onOpenUnifiedHistory: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<HubDialog?>(null) }

    @Suppress("UNUSED_VARIABLE")
    val refreshTick = refreshKey

    val darkModeLabel = when (Preferences.useDarkMode) {
        "follow_system" -> stringResource(R.string.follow_system)
        "always_off" -> stringResource(R.string.always_off)
        "always_on" -> stringResource(R.string.always_on)
        else -> Preferences.useDarkMode
    }
    val themeColorName = stringResource(ThemeColorPreset.fromKey(Preferences.themeColor).displayNameRes)
    val appLanguageValue =
        Preferences.preferenceSp.getString(APP_LANGUAGE_KEY, "system") ?: "system"
    val appLanguageLabel = when (appLanguageValue) {
        "system" -> stringResource(R.string.follow_system)
        "zh-rCN" -> stringResource(R.string.simplified_chinese)
        "zh" -> stringResource(R.string.traditional_chinese)
        "ja" -> stringResource(R.string.japanese_lang)
        "en" -> stringResource(R.string.english_lang)
        else -> appLanguageValue
    }
    val gridConfig = Preferences.searchGridColumnsConfig
    val cardConfig = Preferences.horizontalCardCountConfig
    val dynamicColorEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    ChoiceDialog(
        visible = dialog == HubDialog.DarkMode,
        title = stringResource(R.string.dark_theme),
        options = listOf(
            stringResource(R.string.follow_system) to "follow_system",
            stringResource(R.string.always_off) to "always_off",
            stringResource(R.string.always_on) to "always_on",
        ),
        selectedValue = Preferences.useDarkMode,
        onDismiss = { dialog = null },
        onSelect = { value ->
            dialog = null
            if (value != Preferences.useDarkMode) {
                saveString(SettingsPreferenceKeys.USE_DARK_MODE, value)
                ThemeUtils.applyDarkModeFromPreferences(context)
                activity.recreate()
            }
        },
    )

    ChoiceDialog(
        visible = dialog == HubDialog.ThemeColor,
        title = stringResource(R.string.theme_color),
        options = ThemeColorPreset.entries
            .filter { dynamicColorEnabled || it != ThemeColorPreset.SYSTEM }
            .map { stringResource(it.displayNameRes) to it.key },
        selectedValue = Preferences.themeColor ?: ThemeColorPreset.DEFAULT.key,
        onDismiss = { dialog = null },
        onSelect = { key ->
            dialog = null
            saveString(SettingsPreferenceKeys.THEME_COLOR, key)
            refreshKey++
            activity.recreate()
        },
    )

    ChoiceDialog(
        visible = dialog == HubDialog.AppLanguage,
        title = stringResource(R.string.app_lang),
        options = listOf(
            stringResource(R.string.follow_system) to "system",
            stringResource(R.string.simplified_chinese) to "zh-rCN",
            stringResource(R.string.traditional_chinese) to "zh",
            stringResource(R.string.japanese_lang) to "ja",
            stringResource(R.string.english_lang) to "en",
        ),
        selectedValue = appLanguageValue,
        onDismiss = { dialog = null },
        onSelect = { value ->
            dialog = null
            if (value != appLanguageValue) {
                saveString(APP_LANGUAGE_KEY, value)
                refreshKey++
                activity.recreate()
            }
        },
    )

    if (dialog == HubDialog.GridColumns) {
        SearchGridColumnsDialog(
            initialConfig = gridConfig,
            onDismiss = { dialog = null },
            onConfirm = { config ->
                dialog = null
                saveInt(SettingsPreferenceKeys.SEARCH_GRID_COLUMNS_COMPACT, config.compactColumns)
                saveInt(SettingsPreferenceKeys.SEARCH_GRID_COLUMNS_MEDIUM, config.mediumColumns)
                saveInt(SettingsPreferenceKeys.SEARCH_GRID_COLUMNS_EXPANDED, config.expandedColumns)
                saveInt(SettingsPreferenceKeys.SEARCH_GRID_COLUMNS_LARGE, config.largeColumns)
                refreshKey++
            },
        )
    }

    if (dialog == HubDialog.CardCount) {
        HorizontalCardCountDialog(
            initialConfig = cardConfig,
            onDismiss = { dialog = null },
            onConfirm = { config ->
                dialog = null
                saveString(SettingsPreferenceKeys.HORIZONTAL_CARD_COUNT_NARROW, config.narrowCount.toString())
                saveString(SettingsPreferenceKeys.HORIZONTAL_CARD_COUNT_COMPACT, config.compactCount.toString())
                saveString(SettingsPreferenceKeys.HORIZONTAL_CARD_COUNT_MEDIUM, config.mediumCount.toString())
                saveString(SettingsPreferenceKeys.HORIZONTAL_CARD_COUNT_EXPANDED, config.expandedCount.toString())
                refreshKey++
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${CrossSiteNavigator.siteLabel(site)} · ${stringResource(R.string.settings)}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { HubSectionTitle("App appearance") }
            item {
                SettingNavigationItem(
                    title = stringResource(R.string.dark_theme),
                    valueText = darkModeLabel,
                    onClick = { dialog = HubDialog.DarkMode },
                )
            }
            item {
                SettingNavigationItem(
                    title = stringResource(R.string.theme_color),
                    valueText = themeColorName,
                    onClick = { dialog = HubDialog.ThemeColor },
                )
            }
            item {
                SettingSwitchItem(
                    title = "Pure black dark mode",
                    summary = "Use a pure black background on OLED screens",
                    checked = Preferences.pureBlackDarkMode,
                    onCheckedChange = { enabled ->
                        if (enabled != Preferences.pureBlackDarkMode) {
                            saveBoolean(SettingsPreferenceKeys.PURE_BLACK_DARK_MODE, enabled)
                            refreshKey++
                            activity.recreate()
                        }
                    },
                )
            }
            item {
                SettingNavigationItem(
                    title = stringResource(R.string.app_lang),
                    valueText = appLanguageLabel,
                    onClick = { dialog = HubDialog.AppLanguage },
                )
            }

            item { HubSectionTitle("Layout & column count") }
            item {
                SettingNavigationItem(
                    title = "Video grid columns",
                    summary = "Columns of search / list grids by screen width",
                    valueText = listOf(
                        gridConfig.compactColumns,
                        gridConfig.mediumColumns,
                        gridConfig.expandedColumns,
                        gridConfig.largeColumns,
                    ).joinToString(" / "),
                    onClick = { dialog = HubDialog.GridColumns },
                )
            }
            item {
                SettingNavigationItem(
                    title = "Cards per row",
                    summary = "Number of cards visible in horizontal home rows",
                    valueText = listOf(
                        cardConfig.narrowCount,
                        cardConfig.compactCount,
                        cardConfig.mediumCount,
                        cardConfig.expandedCount,
                    ).joinToString(" / "),
                    onClick = { dialog = HubDialog.CardCount },
                )
            }

            item { HubSectionTitle("All app settings") }
            item {
                HubRow(
                    SiteSettingsEntry(
                        key = "app_settings",
                        title = stringResource(R.string.settings),
                        subtitle = "Playback, language, backup, cache, update and everything else",
                        icon = Icons.Default.Settings,
                        onClick = onOpenAppSettings,
                    )
                )
            }
            item {
                SettingNavigationItem(
                    title = stringResource(R.string.player_settings),
                    onClick = onOpenPlayerSettings,
                )
            }
            item {
                SettingNavigationItem(
                    title = stringResource(R.string.network_settings),
                    onClick = onOpenNetworkSettings,
                )
            }

            if (siteEntries.isNotEmpty()) {
                item { HubSectionTitle("${CrossSiteNavigator.siteLabel(site)} settings") }
                items(siteEntries.size, key = { siteEntries[it].key }) { index ->
                    HubRow(siteEntries[index])
                }
            }

            if (onOpenUnifiedHistory != null) {
                item { HubSectionTitle("History") }
                item {
                    HubRow(
                        SiteSettingsEntry(
                            key = "unified_history",
                            title = "Watch history · all sites",
                            subtitle = "Touch an item of another site to switch site, check the connection and open its video page",
                            icon = Icons.Default.History,
                            onClick = onOpenUnifiedHistory,
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun HubSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun HubRow(entry: SiteSettingsEntry) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = entry.onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
