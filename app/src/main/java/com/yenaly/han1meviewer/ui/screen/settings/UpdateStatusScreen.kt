package com.yenaly.han1meviewer.ui.screen.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yenaly.han1meviewer.BuildConfig
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.model.github.Latest
import com.yenaly.han1meviewer.logic.state.WebsiteState
import com.yenaly.han1meviewer.logic.update.UpdateManager
import com.yenaly.han1meviewer.logic.update.UpdateMethod
import com.yenaly.han1meviewer.logic.update.UpdatePhase
import com.yenaly.han1meviewer.logic.update.UpdatePlan
import com.yenaly.han1meviewer.logic.update.UpdateStage
import com.yenaly.han1meviewer.logic.update.UpdateUiState
import com.yenaly.han1meviewer.shizuku.UpdateInstaller
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import com.yenaly.han1meviewer.ui.viewmodel.AppViewModel
import com.yenaly.han1meviewer.util.requestPostNotificationPermission
import kotlinx.coroutines.launch

@Composable
fun UpdateStatusScreen(activity: MainActivity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui by UpdateManager.state.collectAsStateWithLifecycle()
    val versionState by AppViewModel.versionFlow.collectAsStateWithLifecycle()
    val previewPair by UpdateManager.preview.collectAsStateWithLifecycle()

    val availableFromCheck = (versionState as? WebsiteState.Success)?.info
    val latest: Latest? = ui.latest ?: availableFromCheck

    var showDiscardConfirm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(latest?.nodeId) {
        UpdateManager.syncWithDisk(context, latest)
        if (latest != null) runCatching { UpdateManager.preview(latest) }
    }

    val startDownload: () -> Unit = {
        val target = latest
        if (target != null) {
            scope.launch {

                activity.requestPostNotificationPermission()
                UpdateManager.start(activity.applicationContext, target)
            }
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(R.string.upd_discard_title)) },
            text = { Text(stringResource(R.string.upd_discard_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    UpdateManager.discard(activity)
                }) { Text(stringResource(R.string.upd_btn_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    val plan: UpdatePlan? = when (ui.phase) {
        UpdatePhase.IDLE, UpdatePhase.CANCELLED ->
            previewPair?.takeIf { it.first == latest?.nodeId }?.second
        else -> ui.plan.takeIf { it.method != UpdateMethod.UNDECIDED || it.fullName.isNotEmpty() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StatusCard(
                ui = ui,
                latest = latest,
                versionState = versionState,
                onCheck = { AppViewModel.getLatestVersion(forceCheck = true, silent = true) },
                onDownload = startDownload,
                onCancelDownload = { UpdateManager.cancel(activity) },
                onInstall = { scope.launch { UpdateManager.install(activity) } },
                onDiscard = { showDiscardConfirm = true },
                onDismiss = { UpdateManager.dismiss() },
                onStopWaiting = { UpdateManager.stopWaiting() },
            )
        }
        if (plan != null && latest != null && ui.phase != UpdatePhase.INSTALLING) {
            item { PlanCard(plan = plan, showSmallerHint = ui.phase == UpdatePhase.DOWNLOADING) }
        }
        item { HowDeltaWorksCard() }
        item { VersionInfoCard(latest = latest) }
        if (latest != null && latest.changelog.isNotBlank()) {
            item { ChangelogCard(latest = latest) }
        }
    }
}

@Composable
private fun StatusCard(
    ui: UpdateUiState,
    latest: Latest?,
    versionState: WebsiteState<Latest?>,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
    onStopWaiting: () -> Unit,
) {
    val context = LocalContext.current
    val checkFailed = (versionState as? WebsiteState.Error)?.throwable?.message
        ?: stringResource(R.string.check_update_failed)
    val channelName = stringResource(
        if (Preferences.useCIUpdateChannel) R.string.upd_channel_ci else R.string.upd_channel_release
    )

    val title: String
    val subtitle: String?
    val icon: Int
    val isError: Boolean

    when (ui.phase) {
        UpdatePhase.DOWNLOADING -> {
            title = stringResource(R.string.upd_status_downloading)
            subtitle = stageLabel(ui.stage)
            icon = R.drawable.ic_update_download
            isError = false
        }

        UpdatePhase.DOWNLOADED -> {
            title = stringResource(R.string.upd_status_downloaded)
            subtitle = stringResource(R.string.upd_status_downloaded_sub, latest?.version.orEmpty())
            icon = R.drawable.ic_update_installer
            isError = false
        }

        UpdatePhase.INSTALLING -> {
            title = stringResource(R.string.upd_status_installing)
            subtitle = stringResource(R.string.upd_installing_hint)
            icon = R.drawable.ic_update_installer
            isError = false
        }

        UpdatePhase.FAILED -> {
            title = stringResource(R.string.upd_status_failed)
            subtitle = ui.error
            icon = R.drawable.baseline_error_outline_24
            isError = true
        }

        UpdatePhase.CANCELLED -> {
            title = stringResource(R.string.upd_status_cancelled)
            subtitle = stringResource(R.string.upd_status_cancelled_sub)
            icon = R.drawable.ic_baseline_cancel_24
            isError = false
        }

        UpdatePhase.IDLE -> when (versionState) {
            is WebsiteState.Loading -> {
                title = stringResource(R.string.upd_status_checking)
                subtitle = null
                icon = R.drawable.ic_baseline_update_24
                isError = false
            }

            is WebsiteState.Error -> {
                title = stringResource(R.string.upd_status_check_failed)
                subtitle = checkFailed
                icon = R.drawable.baseline_error_outline_24
                isError = true
            }

            is WebsiteState.Success -> if (versionState.info == null) {
                title = stringResource(R.string.upd_status_up_to_date)
                subtitle = stringResource(
                    R.string.upd_status_up_to_date_sub,
                    BuildConfig.VERSION_NAME,
                    channelName,
                )
                icon = R.drawable.ic_baseline_check_circle_24
                isError = false
            } else {
                title = stringResource(R.string.upd_status_available)
                subtitle = stringResource(R.string.upd_status_available_sub, versionState.info.version)
                icon = R.drawable.ic_update_available
                isError = false
            }
        }
    }

    val container = if (isError) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.secondaryContainer
    val onContainer = if (isError) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onSecondaryContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (!subtitle.isNullOrBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            when (ui.phase) {
                UpdatePhase.DOWNLOADING -> DownloadProgress(ui)
                UpdatePhase.INSTALLING -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                UpdatePhase.IDLE ->
                    if (versionState is WebsiteState.Loading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                else -> Unit
            }

            if (ui.phase == UpdatePhase.DOWNLOADED && !ui.error.isNullOrBlank()) {
                Text(
                    text = ui.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (ui.phase == UpdatePhase.FAILED && !ui.errorDetail.isNullOrBlank()) {
                SelectionContainer {
                    Text(
                        text = ui.errorDetail,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            ActionRow(
                ui = ui,
                versionState = versionState,
                onCheck = onCheck,
                onDownload = onDownload,
                onCancelDownload = onCancelDownload,
                onInstall = onInstall,
                onDiscard = onDiscard,
                onDismiss = onDismiss,
                onStopWaiting = onStopWaiting,
                onCopyDetails = {
                    val text = listOfNotNull(ui.error, ui.errorDetail).joinToString("\n")
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                        ?.setPrimaryClip(ClipData.newPlainText("update", text))
                    GlobalToasts.show(
                        context.getString(R.string.upd_details_copied),
                        level = GlobalToasts.ToastLevel.INFO,
                    )
                },
            )
        }
    }
}

@Composable
private fun DownloadProgress(ui: UpdateUiState) {
    val context = LocalContext.current
    val indeterminate = ui.stage == UpdateStage.PREPARING ||
            ui.stage == UpdateStage.CHECKING_DELTA ||
            ui.stage == UpdateStage.APPLYING_PATCH ||
            ui.stage == UpdateStage.VERIFYING ||
            ui.totalBytes <= 0L
    if (indeterminate) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(
            progress = { ui.progress / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${ui.progress}%  ·  " + stringResource(
                    R.string.upd_progress_bytes,
                    Formatter.formatShortFileSize(context, ui.downloadedBytes),
                    Formatter.formatShortFileSize(context, ui.totalBytes),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (ui.speedBytesPerSec > 0) {
                Text(
                    text = stringResource(
                        R.string.upd_speed,
                        Formatter.formatShortFileSize(context, ui.speedBytesPerSec),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    ui: UpdateUiState,
    versionState: WebsiteState<Latest?>,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
    onStopWaiting: () -> Unit,
    onCopyDetails: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (ui.phase) {
            UpdatePhase.DOWNLOADING -> {
                Button(onClick = onCancelDownload, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.upd_btn_cancel_download))
                }
            }

            UpdatePhase.DOWNLOADED -> {
                Button(onClick = onInstall, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.upd_btn_install))
                }
                OutlinedButton(onClick = onDiscard) {
                    Text(stringResource(R.string.upd_btn_discard))
                }
            }

            UpdatePhase.INSTALLING -> {
                OutlinedButton(onClick = onStopWaiting, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.upd_btn_stop_waiting))
                }
            }

            UpdatePhase.FAILED -> {
                Button(onClick = onDownload, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.upd_btn_retry))
                }
                OutlinedButton(onClick = onCopyDetails) {
                    Text(stringResource(R.string.upd_btn_copy_details))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.upd_btn_dismiss))
                }
            }

            UpdatePhase.CANCELLED -> {
                Button(onClick = onDownload, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.upd_btn_download))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.upd_btn_dismiss))
                }
            }

            UpdatePhase.IDLE -> {
                if (versionState is WebsiteState.Success && versionState.info != null) {
                    Button(onClick = onDownload, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.upd_btn_download))
                    }
                    OutlinedButton(onClick = onCheck) {
                        Text(stringResource(R.string.upd_btn_check))
                    }
                } else if (versionState !is WebsiteState.Loading) {
                    Button(onClick = onCheck, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.upd_btn_check))
                    }
                }
            }
        }
    }
}

@Composable
private fun stageLabel(stage: UpdateStage): String = stringResource(
    when (stage) {
        UpdateStage.PREPARING -> R.string.upd_stage_preparing
        UpdateStage.CHECKING_DELTA -> R.string.upd_stage_checking_delta
        UpdateStage.DOWNLOADING_DELTA -> R.string.upd_stage_downloading_delta
        UpdateStage.APPLYING_PATCH -> R.string.upd_stage_applying_patch
        UpdateStage.VERIFYING -> R.string.upd_stage_verifying
        UpdateStage.DOWNLOADING_FULL -> R.string.upd_stage_downloading_full
    }
)

@Composable
private fun SectionCard(
    title: String,
    iconRes: Int,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        SelectionContainer(modifier = Modifier.weight(0.6f)) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (mono) FontFamily.Monospace else null,
            )
        }
    }
}

@Composable
private fun PlanCard(plan: UpdatePlan, showSmallerHint: Boolean) {
    val context = LocalContext.current
    val unknown = stringResource(R.string.upd_plan_unknown)
    fun size(bytes: Long) = if (bytes > 0) Formatter.formatShortFileSize(context, bytes) else unknown

    SectionCard(
        title = stringResource(R.string.upd_plan_title),
        iconRes = if (plan.method == UpdateMethod.DELTA) R.drawable.ic_update_delta
        else R.drawable.ic_update_download,
    ) {
        InfoRow(
            label = stringResource(R.string.upd_plan_method),
            value = stringResource(
                when (plan.method) {
                    UpdateMethod.DELTA -> R.string.upd_method_delta
                    UpdateMethod.FULL -> R.string.upd_method_full
                    UpdateMethod.UNDECIDED -> R.string.upd_method_undecided
                }
            ),
        )
        if (plan.activeName.isNotEmpty()) {
            InfoRow(label = stringResource(R.string.upd_plan_file), value = plan.activeName, mono = true)
        }
        InfoRow(label = stringResource(R.string.upd_plan_size), value = size(plan.activeSize))
        if (plan.method == UpdateMethod.DELTA) {
            InfoRow(label = stringResource(R.string.upd_plan_full_size), value = size(plan.fullSize))
            if (plan.fullSize > 0 && plan.deltaSize in 1 until plan.fullSize) {
                val saved = plan.fullSize - plan.deltaSize
                Text(
                    text = stringResource(
                        R.string.upd_plan_saving,
                        Formatter.formatShortFileSize(context, saved),
                        (saved * 100 / plan.fullSize).toInt(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (plan.deltaBasePrefix.isNotEmpty()) {
                InfoRow(
                    label = stringResource(R.string.upd_plan_base),
                    value = plan.deltaBasePrefix,
                    mono = true,
                )
            }
        }
        if (plan.installedBuildPrefix.isNotEmpty()) {
            InfoRow(
                label = stringResource(R.string.upd_plan_installed_build),
                value = plan.installedBuildPrefix,
                mono = true,
            )
        }

        val reason = plan.reason
        if (plan.method == UpdateMethod.FULL && reason != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = stringResource(R.string.upd_plan_why_full),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                text = stringResource(reason.titleRes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(reason.explainRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!plan.reasonDetail.isNullOrBlank()) {
                SelectionContainer {
                    Text(
                        text = plan.reasonDetail,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (showSmallerHint && plan.method == UpdateMethod.DELTA) {
            Text(
                text = stringResource(R.string.upd_plan_smaller_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HowDeltaWorksCard() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard(
        title = stringResource(R.string.upd_how_title),
        iconRes = R.drawable.ic_baseline_help_24,
    ) {
        if (expanded) {
            Text(
                text = stringResource(R.string.upd_how_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.upd_how_safe),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.upd_how_body),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.upd_show_less else R.string.upd_show_more))
        }
    }
}

@Composable
private fun VersionInfoCard(latest: Latest?) {
    val installerLabel = stringResource(
        if (Preferences.updateInstaller == UpdateInstaller.SHIZUKU) R.string.update_installer_shizuku
        else R.string.update_installer_system
    )
    val isCi = latest?.isCi ?: Preferences.useCIUpdateChannel
    SectionCard(
        title = stringResource(R.string.upd_info_title),
        iconRes = R.drawable.ic_baseline_info_24,
    ) {
        val commit = BuildConfig.COMMIT_SHA.take(7).takeIf { it.isNotBlank() }
        InfoRow(
            label = stringResource(R.string.upd_info_current),
            value = BuildConfig.VERSION_NAME + (commit?.let { " ($it)" } ?: ""),
        )
        if (latest != null) {
            InfoRow(label = stringResource(R.string.upd_info_target), value = latest.version)
        }
        InfoRow(
            label = stringResource(R.string.upd_info_channel),
            value = stringResource(if (isCi) R.string.upd_channel_ci else R.string.upd_channel_release),
        )
        InfoRow(label = stringResource(R.string.upd_info_installer), value = installerLabel)
    }
}

@Composable
private fun ChangelogCard(latest: Latest) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard(
        title = stringResource(R.string.upd_changelog_title),
        iconRes = R.drawable.ic_baseline_newspaper_24,
    ) {
        SelectionContainer {
            Text(
                text = latest.changelog,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (latest.changelog.lines().size > 8 || latest.changelog.length > 400) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.upd_show_less else R.string.upd_show_more))
            }
        }
    }
}
