package com.yenaly.han1meviewer.OnAir.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yenaly.han1meviewer.OnAir.common.OnAirUrlUtils
import com.yenaly.han1meviewer.OnAir.data.model.OnAirVideoCard
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetwork
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import com.yenaly.han1meviewer.ui.component.SiteVideoCard
import com.yenaly.han1meviewer.util.DisplayTextLocalizer
import com.yenaly.han1meviewer.ui.screen.RetryableImage
import androidx.compose.ui.platform.LocalClipboard

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnAirVideoCardItem(
    card: OnAirVideoCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: ((OnAirVideoCard) -> Unit)? = null,
) {
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    val defaultLongClick: (OnAirVideoCard) -> Unit = { c ->
        val link = OnAirUrlUtils.normalizeVideoUrl(
            "${OnAirNetwork.baseUrl.trimEnd('/')}/watch/${c.videoId}"
        )
        clipboardScope.launch {
                clipboard.setClipEntry(
                    ClipEntry(ClipData.newPlainText("", link))
                )
            }
        GlobalToasts.show(
            stringResource(R.string.onair_video_link_copied),
            level = GlobalToasts.ToastLevel.SUCCESS,
        )
    }
    val longClickHandler = onLongClick ?: defaultLongClick

    SiteVideoCard(
        modifier = modifier,
        title = card.title,
        coverUrl = card.thumbUrl,
        views = card.viewsRaw?.let { DisplayTextLocalizer.localizeViews(it) },
        duration = card.duration,
        subtitle = card.uploaderName,
        onClick = onClick,
        onLongClick = { longClickHandler(card) },
    )
}
