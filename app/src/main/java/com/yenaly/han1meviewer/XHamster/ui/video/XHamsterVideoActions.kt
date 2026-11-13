package com.yenaly.han1meviewer.XHamster.ui.video

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.XHamster.common.XHamsterFormat
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterComment
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterEngagement
import com.yenaly.han1meviewer.XHamster.data.remote.XHamsterUserRepo

/** Pill-shaped button row under the player: like, dislike, favourite, watch later, playlist, report. */
@Composable
internal fun XHamsterEngagementBar(
    state: XHamsterEngagementState,
    loggedIn: Boolean,
    onLoginRequired: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onFavorite: () -> Unit,
    onWatchLater: () -> Unit,
    onPlaylists: () -> Unit,
    onReport: () -> Unit,
) {
    val eng = state.engagement
    fun guarded(action: () -> Unit): () -> Unit = { if (loggedIn) action() else onLoginRequired() }
    val liked = eng?.ratingState == XHamsterEngagement.RATING_LIKED
    val disliked = eng?.ratingState == XHamsterEngagement.RATING_DISLIKED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EngagementPill(
            iconRes = if (liked) R.drawable.ic_baseline_thumb_up_alt_24 else R.drawable.ic_baseline_thumb_up_off_alt_24,
            label = XHamsterFormat.count(eng?.likes) ?: stringResource(R.string.xhamster_like),
            active = liked,
            enabled = !state.busy && eng != null,
            onClick = guarded(onLike),
        )
        EngagementPill(
            iconRes = if (disliked) R.drawable.ic_baseline_thumb_down_alt_24 else R.drawable.ic_baseline_thumb_down_off_alt_24,
            label = XHamsterFormat.count(eng?.dislikes) ?: stringResource(R.string.xhamster_dislike),
            active = disliked,
            enabled = !state.busy && eng != null,
            onClick = guarded(onDislike),
        )
        EngagementPill(
            iconRes = if (state.inFavorites) R.drawable.ic_baseline_favorite_24 else R.drawable.ic_baseline_favorite_border_24,
            label = stringResource(if (state.inFavorites) R.string.xhamster_favorited else R.string.xhamster_favorite),
            active = state.inFavorites,
            enabled = !state.busy && eng != null,
            onClick = guarded(onFavorite),
        )
        EngagementPill(
            iconRes = R.drawable.ic_baseline_watch_later_24,
            label = stringResource(R.string.watch_later),
            active = state.inWatchLater,
            enabled = !state.busy && eng != null,
            onClick = guarded(onWatchLater),
        )
        EngagementPill(
            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
            label = stringResource(R.string.xhamster_add_to_playlist),
            active = false,
            enabled = !state.busy && eng != null,
            onClick = guarded(onPlaylists),
        )
        EngagementPill(
            iconRes = R.drawable.baseline_flag_24,
            label = stringResource(R.string.xhamster_report),
            active = false,
            enabled = true,
            onClick = onReport,
        )
    }
}

@Composable
private fun EngagementPill(
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    iconRes: Int? = null,
    icon: ImageVector? = null,
) {
    val container = if (active) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (active) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                iconRes != null -> Icon(painterResource(iconRes), null, Modifier.size(18.dp))
                icon != null -> Icon(icon, null, Modifier.size(18.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** Checklist of the user's playlists; saving replaces the video's playlist membership. */
@Composable
internal fun XHamsterPlaylistPickerDialog(
    state: XHamsterEngagementState,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    var selected by remember(state.engagement?.collectionIds) {
        mutableStateOf((state.engagement?.collectionIds ?: emptyList()).toSet())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.xhamster_add_to_playlist)) },
        text = {
            when {
                state.collectionsLoading && state.collections.isEmpty() ->
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))

                state.collections.isEmpty() -> Text(stringResource(R.string.xhamster_playlist_none))
                else -> Column(
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    state.collections.forEach { c ->
                        val checked = c.id in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = if (checked) selected - c.id else selected + c.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = {
                                selected = if (checked) selected - c.id else selected + c.id
                            })
                            Text(
                                text = c.title + (c.quantity?.let { " ($it)" } ?: ""),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(selected); onDismiss() },
                enabled = state.collections.isNotEmpty() && !state.busy,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private val REPORT_REASONS = listOf(
    R.string.xhamster_report_reason_illegal,
    R.string.xhamster_report_reason_underage,
    R.string.xhamster_report_reason_nonconsensual,
    R.string.xhamster_report_reason_copyright,
    R.string.xhamster_report_reason_other,
)

/**
 * The site only exposes reporting through its abuse form, so this collects the reason, copies
 * "video link + reason" to the clipboard and opens the form for the user to paste and send.
 */
@Composable
internal fun XHamsterReportDialog(
    videoTitle: String,
    videoUrl: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var reasonRes by rememberSaveable { mutableStateOf(REPORT_REASONS.first()) }
    val reasonText = stringResource(reasonRes)
    val copied = stringResource(R.string.xhamster_report_copied)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.xhamster_report_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.xhamster_report_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                REPORT_REASONS.forEach { res ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { reasonRes = res },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reasonRes == res, onClick = { reasonRes = res })
                        Text(stringResource(res), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val report = "Video: $videoTitle\n$videoUrl\nReason: $reasonText"
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("report", report))
                android.widget.Toast.makeText(context, copied, android.widget.Toast.LENGTH_LONG).show()
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(XHamsterUserRepo.reportUrl()))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                onDismiss()
            }) { Text(stringResource(R.string.xhamster_report_continue)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Text box + send button at the top of the comments tab; also used for replies. */
@Composable
internal fun XHamsterCommentComposer(
    replyTo: XHamsterComment?,
    sending: Boolean,
    postedTick: Int,
    enabled: Boolean,
    onCancelReply: () -> Unit,
    onSend: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(postedTick) { if (postedTick > 0) text = "" }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (replyTo != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_video_reply_24),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.xhamster_comment_replying_to, replyTo.author),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancelReply) { Text(stringResource(R.string.cancel)) }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && !sending,
            placeholder = { Text(stringResource(R.string.xhamster_comment_hint)) },
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            trailingIcon = {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(
                        onClick = { onSend(text) },
                        enabled = enabled && text.isNotBlank(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_video_send_24),
                            contentDescription = stringResource(R.string.xhamster_comment_send),
                        )
                    }
                }
            },
        )
    }
}

/** Shows a toast for every one-shot result raised by the ViewModel (success + failure). */
@Composable
internal fun XHamsterMessageEffect(viewModel: XHamsterVideoViewModel) {
    val context = LocalContext.current
    val message by viewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        val msg = message ?: return@LaunchedEffect
        val text = when (msg.type) {
            XHamsterMessage.LoginRequired -> context.getString(R.string.xhamster_action_login_required)
            XHamsterMessage.ActionFailed -> context.getString(R.string.xhamster_action_failed, msg.detail ?: "")
            XHamsterMessage.Favorited -> context.getString(R.string.xhamster_msg_favorited)
            XHamsterMessage.FavoriteRemoved -> context.getString(R.string.xhamster_msg_favorite_removed)
            XHamsterMessage.WatchLaterAdded -> context.getString(R.string.xhamster_msg_watch_later_added)
            XHamsterMessage.WatchLaterRemoved -> context.getString(R.string.xhamster_msg_watch_later_removed)
            XHamsterMessage.PlaylistsSaved -> context.getString(R.string.xhamster_msg_playlists_saved)
            XHamsterMessage.CommentPosted -> context.getString(R.string.xhamster_comment_posted)
            XHamsterMessage.CommentCaptcha -> context.getString(R.string.xhamster_comment_captcha)
            XHamsterMessage.CommentRejected ->
                context.getString(R.string.xhamster_comment_rejected, msg.detail ?: "")
        }
        android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
        viewModel.consumeMessage()
    }
}
