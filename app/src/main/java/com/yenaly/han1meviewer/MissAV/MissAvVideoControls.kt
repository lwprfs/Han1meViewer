package com.yenaly.han1meviewer.MissAV

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets

data class SubtitleCue(
    val startTime: Long,
    val endTime: Long,
    val text: String
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
                    while (index < lines.size && lines[index].isBlank()) {
                        index++
                    }
                    if (index >= lines.size) break
                    
                    if (lines[index].matches(Regex("^\\d+$"))) {
                        index++
                    }
                    if (index >= lines.size) break
                    
                    val timeLine = lines[index]
                    val timeMatch = Regex("""(\d{2}:\d{2}:\d{2}[,.]\d{3})\s*-->\s*(\d{2}:\d{2}:\d{2}[,.]\d{3})""").find(timeLine)
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
                            val text = textLines.joinToString("\n")
                            cues.add(SubtitleCue(startTime, endTime, text))
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
        if (bytes.size >= 3) {
            if (bytes[0].toInt() == 0xEF && bytes[1].toInt() == 0xBB && bytes[2].toInt() == 0xBF) {
                return StandardCharsets.UTF_8
            }
            if (bytes.size >= 2 && bytes[0].toInt() == 0xFF && bytes[1].toInt() == 0xFE) {
                return StandardCharsets.UTF_16LE
            }
            if (bytes.size >= 2 && bytes[0].toInt() == 0xFE && bytes[1].toInt() == 0xFF) {
                return StandardCharsets.UTF_16BE
            }
        }
        try {
            String(bytes, StandardCharsets.UTF_8)
            return StandardCharsets.UTF_8
        } catch (_: Exception) {}
        
        return StandardCharsets.UTF_8
    }

    private fun parseTimeToMillis(timeStr: String): Long {
        val normalized = timeStr.replace(',', '.')
        val parts = normalized.split(Regex("[:.]"))
        if (parts.size >= 4) {
            try {
                val hours = parts[0].toInt()
                val minutes = parts[1].toInt()
                val seconds = parts[2].toInt()
                val millis = parts[3].toDouble().toInt()
                return (hours * 3600000L) + (minutes * 60000L) + (seconds * 1000L) + millis
            } catch (_: NumberFormatException) {
                try {
                    val timeParts = normalized.split(Regex("[:]"))
                    if (timeParts.size >= 3) {
                        val hours = timeParts[0].toInt()
                        val minutes = timeParts[1].toInt()
                        val secParts = timeParts[2].split(".")
                        val seconds = secParts[0].toInt()
                        val millis = if (secParts.size > 1) secParts[1].toInt() else 0
                        return (hours * 3600000L) + (minutes * 60000L) + (seconds * 1000L) + millis
                    }
                } catch (_: NumberFormatException) {}
            }
        }
        return 0L
    }
}

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
    onResumeFromSaved: () -> Unit = {},
    onStartFromBeginning: () -> Unit = {},
    onPlayerCreated: (ExoPlayer) -> Unit,
    onContainerCreated: (FrameLayout) -> Unit,
    onSubtitleTextViewCreated: (TextView) -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
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
) {
    val context = LocalContext.current
    var playerReady by remember { mutableStateOf(false) }

    LaunchedEffect(exoPlayer, isPlaying) {
        if (exoPlayer != null) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        playerReady = true
                        onPositionUpdate(exoPlayer.currentPosition, exoPlayer.duration)
                    }
                }
                
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    onPositionUpdate(exoPlayer.currentPosition, exoPlayer.duration)
                }
                
                override fun onIsPlayingChanged(playing: Boolean) {
                    if (playing) {
                        onPositionUpdate(exoPlayer.currentPosition, exoPlayer.duration)
                    }
                }
            }
            exoPlayer.addListener(listener)
            
            while (isActive) {
                delay(200)
                exoPlayer?.let { player ->
                    if (player.playWhenReady) {
                        onPositionUpdate(player.currentPosition, player.duration)
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        contentAlignment = Alignment.Center
    ) {
        if (playerStarted && currentUrl.isNotEmpty()) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        onContainerCreated(this)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        val surface = SurfaceView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                        addView(surface)
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
                            visibility = android.view.View.VISIBLE
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                gravity = Gravity.BOTTOM
                                bottomMargin = 160
                                leftMargin = 32
                                rightMargin = 32
                            }
                        }
                        addView(subtitleText)
                        val player = ExoPlayer.Builder(ctx).build()
                        onPlayerCreated(player)
                        player.setVideoSurfaceView(surface)
                        player.playWhenReady = true
                        val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                            override fun onDoubleTap(e: MotionEvent): Boolean {
                                if (e.x < width / 2) {
                                    player.seekTo((player.currentPosition - 10000).coerceAtLeast(0))
                                } else {
                                    player.seekTo((player.currentPosition + 10000).coerceAtMost(player.duration))
                                }
                                return true
                            }
                            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                                onToggleControls()
                                return true
                            }
                        })
                        setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event); true }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (showResumeButton && !isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Resume from ${MissAvVideoUtils.formatTime(savedPosition)}?",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                modifier = Modifier.clickable { onResumeFromSaved() },
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Resume",
                                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            
                            Surface(
                                modifier = Modifier.clickable { onStartFromBeginning() },
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Start Over",
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }

            if (showControls) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onSubtitleToggle,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (hasSubtitle) Icons.Filled.ClosedCaption else Icons.Filled.Subtitles,
                            contentDescription = if (hasSubtitle) "Remove Subtitle" else "Add Subtitle",
                            tint = if (hasSubtitle) Color.Green else Color.White
                        )
                    }

                    Box {
                        IconButton(
                            onClick = onToggleSpeedMenu,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.Speed, contentDescription = "Speed", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = onDismissSpeedMenu,
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.95f))
                        ) {
                            availableSpeeds.forEach { speed ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${speed}x",
                                            color = if (currentSpeed == speed) MaterialTheme.colorScheme.primary else Color.White
                                        )
                                    },
                                    onClick = { onSpeedChange(speed) }
                                )
                            }
                        }
                    }
                    if (qualityMap.size > 1) {
                        Box {
                            IconButton(
                                onClick = onToggleQualityMenu,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = "Quality", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = showQualityMenu,
                                onDismissRequest = onDismissQualityMenu,
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.95f))
                            ) {
                                qualityMap.keys.forEach { quality ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                quality,
                                                color = if (selectedQuality == quality) MaterialTheme.colorScheme.primary else Color.White
                                            )
                                        },
                                        onClick = { onQualityChange(quality) }
                                    )
                                }
                            }
                        }
                    }
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = Color.White
                        )
                    }
                }

                if (isFullscreen) {
                    IconButton(
                        onClick = onExitFullscreen,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit Fullscreen", tint = Color.White)
                    }
                }

                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(64.dp)
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            MissAvVideoUtils.formatTime(currentPosition),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(45.dp)
                        )
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onValueChange = { newValue ->
                                onSeek((newValue * duration).toLong())
                            },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        Text(
                            MissAvVideoUtils.formatTime(duration),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(45.dp)
                        )
                    }
                }
            }
        } else if (isExtractingUrl) {
            AndroidView(factory = { ctx ->
                WebView(ctx).apply {
                    onWebViewRefChange(this)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
                    settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val url = request?.url?.toString() ?: ""
                            if (url.contains("video.m3u8", ignoreCase = true)) {
                                onUrlCaptured(url)
                            }
                            return null
                        }
                        @Suppress("DEPRECATION")
                        override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
                            if (url != null && url.contains("video.m3u8", ignoreCase = true)) {
                                onUrlCaptured(url)
                            }
                            return null
                        }
                        override fun onLoadResource(view: WebView?, url: String?) {
                            super.onLoadResource(view, url)
                            if (url != null && url.contains("video.m3u8", ignoreCase = true)) {
                                onUrlCaptured(url)
                            }
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            view?.evaluateJavascript(
                                "(function(){var m=document.documentElement.outerHTML.match(/https?:\\/\\/[^\"'<>\\s]*video\\.m3u8[^\"'<>\\s]*/g);return m?JSON.stringify(m):'[]';})()"
                            ) { result ->
                                try {
                                    val urls = result?.trim('"')?.let {
                                        kotlinx.serialization.json.Json.decodeFromString<List<String>>(it)
                                    } ?: emptyList()
                                    if (urls.isNotEmpty()) {
                                        onUrlCaptured(urls.first())
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                    loadUrl(videoPageUrl)
                }
            }, modifier = Modifier.fillMaxSize())
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Extracting video URL...", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else if (extractionFailed) {
            ErrorContent(
                message = "Failed to extract video URL",
                onRetry = onRetryExtraction
            )
        } else {
            if (coverUrl.isNotEmpty()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = "Video cover",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                )
            ) {
                Icon(Icons.Filled.PlayArrow, "Play", modifier = Modifier.size(36.dp))
            }
        }
    }
}