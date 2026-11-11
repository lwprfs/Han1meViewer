package com.yenaly.han1meviewer.SupJav.ui.video

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.exoplayer.ExoPlayer
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.SupJav.common.SupJavImageLoader
import com.yenaly.han1meviewer.SupJav.data.remote.SupJavNetwork
import com.yenaly.han1meviewer.ui.component.player.SiteVideoPlayer

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun SupJavVideoPlayer(
    state: SupJavPlayerState,
    exoPlayer: ExoPlayer?,
    coverUrl: String,
    isFullscreen: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    onPositionUpdate: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
    showResumeButton: Boolean = false,
    savedPosition: Long = 0L,
    onResumeFromSaved: () -> Unit = {},
    onStartFromBeginning: () -> Unit = {},
    title: String = "",
    onBack: () -> Unit = {},
    onSpeedSelect: (Float) -> Unit = {},
    onQualitySelect: (String) -> Unit = {},
    referer: String = "",
    subtitleText: String = "",
) = SiteVideoPlayer(
    siteType = SiteType.SUPJAV,
    playerTag = "SupJavPlayer",
    state = state,
    exoPlayer = exoPlayer,
    coverUrl = coverUrl,
    isFullscreen = isFullscreen,
    onPlayPause = onPlayPause,
    onSeek = onSeek,
    onSkip = onSkip,
    onToggleFullscreen = onToggleFullscreen,
    onPositionUpdate = onPositionUpdate,
    modifier = modifier,
    showResumeButton = showResumeButton,
    savedPosition = savedPosition,
    onResumeFromSaved = onResumeFromSaved,
    onStartFromBeginning = onStartFromBeginning,
    title = title,
    onBack = onBack,
    onSpeedSelect = onSpeedSelect,
    onQualitySelect = onQualitySelect,
    subtitleText = subtitleText,
    imageLoader = { SupJavImageLoader.get(it) },
    mpvReferer = referer.ifBlank { SupJavNetwork.baseUrl.trimEnd('/') + "/" },
)
