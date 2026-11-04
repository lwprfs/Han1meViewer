package com.yenaly.han1meviewer.ui.component

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.logic.SiteCheckIn
import com.yenaly.han1meviewer.ui.navigation.NavigationManager
import com.yenaly.han1meviewer.worker.HanimeDownloadManagerV2
import com.yenaly.han1meviewer.worker.HanimeDownloadWorker
import com.yenaly.yenaly_libs.utils.applicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SiteVideoActionBar(
    site: SiteType,
    videoCode: String,
    title: String,
    coverUrl: String?,
    webUrl: String?,
    modifier: Modifier = Modifier,
    downloadUrl: String? = null,
    downloadQuality: String = "",

    downloadFolder: String? = null,

    resolveDownloadUrl: (suspend () -> String?)? = null,

    onDownloadClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCheckInDialog by remember { mutableStateOf(false) }
    var todayCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(site, videoCode) {
        todayCount = runCatching {
            withContext(Dispatchers.IO) { SiteCheckIn.todayCount(context) }
        }.getOrDefault(0)
    }

    if (showCheckInDialog) {
        SiteQuickCheckInDialog(
            title = title,
            onDismiss = { showCheckInDialog = false },
            onConfirm = { feeling ->
                showCheckInDialog = false
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        SiteCheckIn.quickCheckIn(
                            context = context,
                            site = site,
                            videoCode = videoCode,
                            title = title,
                            coverUrl = coverUrl,
                            feeling = feeling,
                        )
                        SiteCheckIn.todayCount(context)
                    }.onSuccess { count ->
                        withContext(Dispatchers.Main) {
                            todayCount = count
                            GlobalToasts.show(
                                context.getString(R.string.checkin),
                                level = GlobalToasts.ToastLevel.SUCCESS,
                            )
                        }
                    }.onFailure {
                        withContext(Dispatchers.Main) {
                            GlobalToasts.show(
                                it.message ?: context.getString(R.string.site_checkin_failed),
                                level = GlobalToasts.ToastLevel.ERROR,
                            )
                        }
                    }
                }
            },
        )
    }

    val checkInLabel = stringResource(R.string.quick_checkin) +
            if (todayCount > 0) " ($todayCount)" else ""

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val gettingLinkText = stringResource(R.string.site_getting_the_download_link)
        SiteVideoActionButton(
            iconRes = R.drawable.ic_baseline_check_circle_24,
            label = checkInLabel,
            onClick = { showCheckInDialog = true },
        )
        SiteVideoActionButton(
            iconRes = R.drawable.ic_baseline_download_24,
            label = stringResource(R.string.download),
            onClick = onClick@{
                if (onDownloadClick != null) {
                    onDownloadClick()
                    return@onClick
                }
                val code = downloadFolder?.takeIf { it.isNotBlank() }
                    ?.let { "${it.trim('/')}/$videoCode" } ?: videoCode
                if (downloadUrl.isNullOrBlank() && resolveDownloadUrl != null) {
                    GlobalToasts.show(
                        gettingLinkText,
                        level = GlobalToasts.ToastLevel.INFO,
                    )
                    scope.launch {
                        val resolved = runCatching { resolveDownloadUrl() }.getOrNull()
                        if (resolved.isNullOrBlank()) {
                            GlobalToasts.show(
                                applicationContext.getString(R.string.site_could_not_get_the_download_link),
                                level = GlobalToasts.ToastLevel.ERROR,
                            )
                        } else {
                            startSiteDownload(
                                context, site, code, title, coverUrl, resolved, downloadQuality,
                            )
                        }
                    }
                } else {
                    startSiteDownload(
                        context = context,
                        site = site,
                        videoCode = code,
                        title = title,
                        coverUrl = coverUrl,
                        downloadUrl = downloadUrl,
                        quality = downloadQuality,
                    )
                }
            },
        )
        if (!webUrl.isNullOrBlank()) {
            SiteVideoActionButton(
                iconRes = R.drawable.ic_baseline_share_24,
                label = stringResource(R.string.share),
                onClick = { shareSiteText(context, "$title\n$webUrl", title) },
                onLongClick = { shareSiteText(context, webUrl, title) },
            )
            SiteVideoActionButton(
                iconRes = R.drawable.ic_baseline_language_24,
                label = stringResource(R.string.jump_to_webpage),
                onClick = { openSiteWebPage(context, webUrl) },
            )
        }
        SiteVideoActionButton(
            iconRes = R.drawable.ic_baseline_home_24,
            label = stringResource(R.string.home_page),
            onClick = { NavigationManager.navigateToHome() },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SiteVideoActionButton(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .widthIn(min = 76.dp)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SiteQuickCheckInDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (feeling: String) -> Unit,
) {
    var feeling by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2) },
        text = {
            OutlinedTextField(
                value = feeling,
                onValueChange = { if (it.length <= 200) feeling = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.dialog_feeling_hint)) },
                minLines = 3,
                supportingText = { Text("${feeling.length}/200") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(feeling) }) {
                Text(stringResource(R.string.dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

fun shareSiteText(context: Context, text: String, title: String? = null) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        if (!title.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

fun openSiteWebPage(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun startSiteDownload(
    context: Context,
    site: SiteType,
    videoCode: String,
    title: String,
    coverUrl: String?,
    downloadUrl: String?,
    quality: String,
) {
    if (downloadUrl.isNullOrBlank()) {
        GlobalToasts.show(
            context.getString(R.string.no_video_links_found),
            level = GlobalToasts.ToastLevel.WARNING,
        )
        return
    }
    val path = downloadUrl.substringBefore('?').lowercase()
    val suffix = if (path.contains(".m3u8")) {
        "ts"
    } else {
        path.substringAfterLast('.', "").takeIf { it.length in 2..4 } ?: "mp4"
    }
    HanimeDownloadManagerV2.addTask(
        HanimeDownloadWorker.Args(
            quality = quality.ifBlank { "default" },
            downloadUrl = downloadUrl,
            videoType = suffix,
            hanimeName = title,
            videoCode = SiteCheckIn.dishCode(site, videoCode),
            coverUrl = coverUrl.orEmpty(),
        )
    )
    GlobalToasts.show(
        context.getString(R.string.added_to_download_queue),
        level = GlobalToasts.ToastLevel.SUCCESS,
    )
}
