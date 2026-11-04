package com.yenaly.han1meviewer.MissAV.ui.video

import com.yenaly.han1meviewer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.saveable.rememberSaveable
import com.yenaly.han1meviewer.ui.component.VideoFitState
import com.yenaly.han1meviewer.ui.component.fitVideoSurface
import com.yenaly.han1meviewer.ui.component.installVideoFit
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.yenaly_libs.utils.applicationContext
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets

import com.yenaly.han1meviewer.MissAV.data.remote.MISSAV_QUALITY_AUTO
import com.yenaly.han1meviewer.MissAV.data.remote.MissAvVideoUtils

data class SubtitleCue(
    val startTime: Long,
    val endTime: Long,
    val text: String,
)

object SubtitleParser {
    fun parseSRT(uri: Uri, context: Context): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        try {
            val inputStream: InputStream? = when (uri.scheme) {
                "file" -> {
                    val file = File(uri.path ?: "")
                    if (file.exists()) file.inputStream() else null
                }
                else -> context.contentResolver.openInputStream(uri)
            }

            inputStream?.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.isEmpty()) return@use

                val charset = detectCharset(bytes)
                val content = String(bytes, charset)
                val lines = content.lines()

                var index = 0
                while (index < lines.size) {
                    while (index < lines.size && lines[index].isBlank()) index++
                    if (index >= lines.size) break

                    if (lines[index].matches(Regex("^\\d+$"))) index++
                    if (index >= lines.size) break

                    val timeLine = lines[index]
                    val timeMatch = Regex(
                        """(\d{2}:\d{2}:\d{2}[,.]\d{3})\s*-->\s*(\d{2}:\d{2}:\d{2}[,.]\d{3})"""
                    ).find(timeLine)

                    if (timeMatch != null) {
                        val startTime = parseTimeToMillis(timeMatch.groupValues[1])
                        val endTime = parseTimeToMillis(timeMatch.groupValues[2])
                        index++

                        val textLines = mutableListOf<String>()
                        while (index < lines.size && lines[index].isNotBlank()) {
                            textLines.add(lines[index].trim())
                            index++
                        }

                        if (textLines.isNotEmpty()) {
                            cues.add(
                                SubtitleCue(
                                    startTime,
                                    endTime,
                                    textLines.joinToString("\n"),
                                )
                            )
                        }
                    } else {
                        index++
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SubtitleParser", "Error parsing SRT", e)
        }
        return cues.sortedBy { it.startTime }
    }

    private fun detectCharset(bytes: ByteArray): java.nio.charset.Charset {
        if (bytes.size >= 3 &&
            bytes[0].toInt() == 0xEF &&
            bytes[1].toInt() == 0xBB &&
            bytes[2].toInt() == 0xBF
        ) return StandardCharsets.UTF_8
        if (bytes.size >= 2 && bytes[0].toInt() == 0xFF && bytes[1].toInt() == 0xFE)
            return StandardCharsets.UTF_16LE
        if (bytes.size >= 2 && bytes[0].toInt() == 0xFE && bytes[1].toInt() == 0xFF)
            return StandardCharsets.UTF_16BE
        return runCatching {
            String(bytes, StandardCharsets.UTF_8)
            StandardCharsets.UTF_8
        }.getOrDefault(StandardCharsets.UTF_8)
    }

    private fun parseTimeToMillis(timeStr: String): Long {
        val normalized = timeStr.replace(',', '.')
        val parts = normalized.split(Regex("[:.]"))
        if (parts.size >= 4) {
            return runCatching {
                val h = parts[0].toInt()
                val m = parts[1].toInt()
                val s = parts[2].toInt()
                val ms = parts[3].toInt()
                h * 3600000L + m * 60000L + s * 1000L + ms
            }.getOrDefault(0L)
        }
        return 0L
    }
}

private val MISSAV_MASTER_PLAYLIST = Regex("/[0-9a-fA-F-]{36}/playlist\\.m3u8", RegexOption.IGNORE_CASE)

private fun isMissAvStreamUrl(url: String): Boolean =
    url.contains("video.m3u8", ignoreCase = true) || MISSAV_MASTER_PLAYLIST.containsMatchIn(url)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MissAvVideoPlayer(
    playerStarted: Boolean,
    currentUrl: String,
    isExtractingUrl: Boolean,
    extractionFailed: Boolean,
    capturedUrl: String,
    videoPageUrl: String,
    coverUrl: String,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    showControls: Boolean,
    isFullscreen: Boolean,
    hasSubtitle: Boolean,
    qualityMap: Map<String, String>,
    selectedQuality: String,
    availableSpeeds: List<Float>,
    currentSpeed: Float,
    showSpeedMenu: Boolean,
    showQualityMenu: Boolean,
    exoPlayer: ExoPlayer?,
    subtitleTextView: TextView?,
    showResumeButton: Boolean = false,
    savedPosition: Long = 0L,
    playerListener: Player.Listener,
    onResumeFromSaved: () -> Unit = {},
    onStartFromBeginning: () -> Unit = {},
    onPlayerCreated: (ExoPlayer) -> Unit,
    onSubtitleTextViewCreated: (TextView) -> Unit,
    onSurfaceReleased: () -> Unit = {},
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Long) -> Unit = {},
    onToggleControls: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onQualityChange: (String) -> Unit,
    onToggleFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
    onToggleSpeedMenu: () -> Unit,
    onToggleQualityMenu: () -> Unit,
    onDismissSpeedMenu: () -> Unit,
    onDismissQualityMenu: () -> Unit,
    onSubtitleToggle: () -> Unit,
    onPlayClick: () -> Unit,
    onRetryExtraction: () -> Unit,
    onPositionUpdate: (Long, Long) -> Unit,
    webViewRef: WebView?,
    onWebViewRefChange: (WebView?) -> Unit,
    onUrlCaptured: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val playerConfig = com.yenaly.han1meviewer.ui.component.player.SitePlayerSettings
        .config(com.yenaly.han1meviewer.SiteType.MISSAV)

    var isBuffering by remember { mutableStateOf(false) }
    var playingHeight by remember { mutableStateOf(0) }
    var hasRenderedFirstFrame by remember { mutableStateOf(false) }
    var lastSurfaceView by remember { mutableStateOf<SurfaceView?>(null) }
    val fitState = remember { VideoFitState() }
    var stretched by rememberSaveable { mutableStateOf(com.yenaly.han1meviewer.ui.component.player.SitePlayerSettings.config(com.yenaly.han1meviewer.SiteType.MISSAV).stretchFullscreen) }
    fitState.stretched = stretched && isFullscreen

    val effectiveIsPlaying = isPlaying && !isBuffering

    DisposableEffect(exoPlayer) {
        val exo = exoPlayer
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING ||
                        (playbackState == Player.STATE_IDLE && !hasRenderedFirstFrame)
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
                isBuffering = false
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                playingHeight = videoSize.height
                fitState.updateFrom(videoSize)
                val frame = lastSurfaceView?.parent as? FrameLayout
                if (frame != null) lastSurfaceView?.let { fitVideoSurface(frame, it, fitState) }
            }
        }
        exo?.videoSize?.let { fitState.updateFrom(it) }
        exo?.addListener(listener)
        onDispose { exo?.removeListener(listener) }
    }

    LaunchedEffect(currentUrl) {
        hasRenderedFirstFrame = false
        if (currentUrl.isNotBlank()) {
            isBuffering = true
        } else {
            isBuffering = false
        }
    }

    val surfaceModifier = if (isFullscreen) {
        modifier.fillMaxSize()
    } else {
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    }

    Box(
        modifier = surfaceModifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (playerStarted && currentUrl.isNotEmpty()) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )

                        val surface = SurfaceView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }
                        addView(surface)
                        lastSurfaceView = surface
                        installVideoFit(this, surface, fitState)

                        val subtitleText = TextView(ctx).apply {
                            onSubtitleTextViewCreated(this)
                            setTextColor(AndroidColor.WHITE)
                            setBackgroundColor(AndroidColor.TRANSPARENT)
                            setShadowLayer(4f, 0f, 0f, AndroidColor.BLACK)
                            textSize = 22f
                            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                            setPadding(32, 16, 32, 48)
                            maxLines = 3
                            setEllipsize(android.text.TextUtils.TruncateAt.END)
                            visibility = android.view.View.GONE
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            ).apply {
                                gravity = Gravity.BOTTOM
                                bottomMargin = if (isFullscreen) 200 else 160
                                leftMargin = 32
                                rightMargin = 32
                            }
                        }
                        addView(subtitleText)

                        val player = exoPlayer ?: com.yenaly.han1meviewer.ui.component.player.createSitePlayer(ctx).also {
                            onPlayerCreated(it)
                        }
                        player.setVideoSurfaceView(surface)

                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { frame ->
                    fitState.stretched = stretched && isFullscreen
                    lastSurfaceView?.let { fitVideoSurface(frame, it, fitState) }
                },
                onRelease = { view ->

                    (view as? ViewGroup)?.getChildAt(0)?.let { child ->
                        (child as? SurfaceView)?.let { exoPlayer?.clearVideoSurfaceView(it) }
                    }
                    onSurfaceReleased()
                    lastSurfaceView = null
                    (view as? ViewGroup)?.removeAllViews()
                },
            )

            if (showResumeButton && !isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.site_resume_from, MissAvVideoUtils.formatTime(savedPosition)),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Surface(
                                modifier = Modifier.clickable { onResumeFromSaved() },
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.site_resume),
                                    modifier = Modifier.padding(
                                        horizontal = 32.dp,
                                        vertical = 12.dp,
                                    ),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            Surface(
                                modifier = Modifier.clickable { onStartFromBeginning() },
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.missav_start_over),
                                    modifier = Modifier.padding(
                                        horizontal = 24.dp,
                                        vertical = 12.dp,
                                    ),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
            }

            if (!showResumeButton) {
                val realQualities = remember(qualityMap) {
                    qualityMap.entries.distinctBy { it.value }.map { it.key }
                        .filter { it != QUALITY_UNAVAILABLE }
                }
                com.yenaly.han1meviewer.ui.component.player.SitePlayerOverlay(
                    config = playerConfig,
                    title = title,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    positionMs = currentPosition,
                    durationMs = duration,
                    bufferedMs = exoPlayer?.bufferedPosition?.coerceAtLeast(0L) ?: 0L,
                    qualityLabel = when {
                        selectedQuality == QUALITY_UNAVAILABLE -> ""
                        selectedQuality == MISSAV_QUALITY_AUTO && playingHeight > 0 ->
                            "$MISSAV_QUALITY_AUTO · ${playingHeight}p"
                        else -> selectedQuality
                    },
                    speed = currentSpeed,
                    isFullscreen = isFullscreen,
                    onPlayPause = onPlayPause,
                    onSeekTo = onSeek,
                    onSeekBy = onSkip,
                    onToggleFullscreen = onToggleFullscreen,
                    onBack = if (isFullscreen) onExitFullscreen else onBack,
                    speedOptions = availableSpeeds,
                    onSpeedSelect = onSpeedChange,
                    qualityOptions = if (com.yenaly.han1meviewer.Preferences.missAvLockQuality) {
                        emptyList()
                    } else {
                        realQualities
                    },
                    onQualitySelect = onQualityChange,
                    stretched = stretched,
                    onToggleStretch = { stretched = !stretched },
                    onTemporarySpeed = { exoPlayer?.setPlaybackSpeed(it ?: currentSpeed) },
                    extraActions = {
                        if (isFullscreen) {
                            SkipButton(text = "+10m", onClick = { onSkip(10 * 60_000L) })
                            SkipButton(text = "+30m", onClick = { onSkip(30 * 60_000L) })
                        }
                        IconButton(onClick = onSubtitleToggle, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (hasSubtitle) Icons.Filled.ClosedCaption else Icons.Filled.Subtitles,
                                contentDescription = if (hasSubtitle) stringResource(R.string.missav_remove_subtitle) else stringResource(R.string.missav_add_subtitle),
                                tint = if (hasSubtitle) Color.Green else Color.White,
                            )
                        }
                    },
                )
            }
        } else if (isExtractingUrl && capturedUrl.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.missav_loading_resolutions), style = MaterialTheme.typography.bodySmall)
                }
            }
        } else if (isExtractingUrl) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        onWebViewRefChange(this)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mixedContentMode =
                            android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.userAgentString = com.yenaly.han1meviewer.USER_AGENT
                        settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): WebResourceResponse? {
                                val url = request?.url?.toString() ?: ""
                                if (isMissAvStreamUrl(url)) {
                                    onUrlCaptured(url)
                                }
                                return null
                            }

                            @Deprecated("Deprecated in Java")
                            @Suppress("DEPRECATION")
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                url: String?,
                            ): WebResourceResponse? {
                                if (url != null && isMissAvStreamUrl(url)) {
                                    onUrlCaptured(url)
                                }
                                return null
                            }

                            override fun onLoadResource(view: WebView?, url: String?) {
                                super.onLoadResource(view, url)
                                if (url != null && isMissAvStreamUrl(url)) {
                                    onUrlCaptured(url)
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                view?.evaluateJavascript(
                                    "(function(){var m=document.documentElement.outerHTML.match(/https?:\\/\\/[^\"'<>\\s]*video\\.m3u8[^\"'<>\\s]*/g);return m?JSON.stringify(m):'[]';})()"
                                ) { result ->
                                    runCatching {
                                        val urls = result?.trim('"')?.let {
                                            kotlinx.serialization.json.Json.decodeFromString<List<String>>(
                                                it
                                            )
                                        } ?: emptyList()
                                        if (urls.isNotEmpty()) onUrlCaptured(urls.first())
                                    }
                                }
                            }
                        }
                        loadUrl(videoPageUrl)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { view ->
                    onWebViewRefChange(null)
                    view.stopLoading()
                    (view as? ViewGroup)?.removeView(view)
                    view.destroy()
                },
            )
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.missav_extracting_video_url), style = MaterialTheme.typography.bodySmall)
                }
            }
        } else if (extractionFailed) {
            ErrorContent(
                message = applicationContext.getString(R.string.missav_failed_to_extract_video_url),
                onRetry = onRetryExtraction,
            )
        } else {
            if (coverUrl.isNotEmpty()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = applicationContext.getString(R.string.missav_video_cover),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                ),
            ) {
                Icon(Icons.Filled.PlayArrow, stringResource(R.string.site_play), modifier = Modifier.size(36.dp))
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { }
    }
}

@Composable
private fun SkipButton(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.5f),
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Filled.FastForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
