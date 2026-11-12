package com.yenaly.han1meviewer.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.DefaultSiteSwitchOptions
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import com.yenaly.han1meviewer.ui.component.SettingSwitchItem

@Composable
fun SiteVisibilitySettings() {
    var disabled by remember { mutableStateOf(Preferences.disabledSites) }
    val currentSiteMessage = stringResource(R.string.site_visibility_current_site)
    val hiddenSummary = stringResource(R.string.site_visibility_hidden)

    Column {
        DefaultSiteSwitchOptions.forEach { option ->
            val enabled = option.siteType !in disabled
            SettingSwitchItem(
                title = option.name,
                summary = if (enabled) option.description else hiddenSummary,
                checked = enabled,
                onCheckedChange = { on ->
                    if (Preferences.setSiteEnabled(option.siteType, on)) {
                        disabled = Preferences.disabledSites
                    } else {
                        GlobalToasts.show(currentSiteMessage, level = GlobalToasts.ToastLevel.WARNING)
                    }
                },
            )
        }
    }
}
