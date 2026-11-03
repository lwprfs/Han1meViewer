package com.yenaly.han1meviewer.HentaiMama.ui.video

import com.yenaly.han1meviewer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.saveable.rememberSaveable
import com.yenaly.han1meviewer.ui.component.VideoFitState
import com.yenaly.han1meviewer.ui.component.fitVideoSurface
import com.yenaly.han1meviewer.ui.component.installVideoFit
import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Gravity
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.ui.component.player.SitePlayerOverlay
import com.yenaly.han1meviewer.ui.component.player.SitePlayerSettings
import kotlinx.coroutines.delay

private const val PLAYER_TAG = "HentaiMamaPlayer"

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun HentaiMamaVideoPlayer(
    state: HentaiMamaPlayerState,
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
) {
    val config = SitePlayerSettings.config(SiteType.HENTAIMAMA)
    var tempSpeed by remember { mutableStateOf<Float?>(null) }
    var lastPosition by remember { mutableLongStateOf(0L) }
    var surfaceReady by remember { mutableStateOf(false) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var hasRenderedFirstFrame by remember { mutableStateOf(false) }
    var isRebuffering by remember { mutableStateOf(false) }
    var playbackError by remember(state.url) { mutableStateOf<PlaybackException?>(null) }
    val context = LocalContext.current

    val sourceReady = state.isReady && state.url.isNotBlank()
    val isExoReady = playbackState == Player.STATE_READY
    val isInitialLoading = sourceReady && !hasRenderedFirstFrame && playbackError == null
    val isBuffering = sourceReady && (
            isInitialLoading ||
                    isRebuffering ||
                    playbackState == Player.STATE_BUFFERING
            )
    val effectiveIsPlaying = state.isPlaying && isExoReady

    DisposableEffect(exoPlayer) {
        val exo = exoPlayer
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(newState: Int) {
                playbackState = newState
                if (newState == Player.STATE_READY) playbackError = null
                isRebuffering = newState == Player.STATE_BUFFERING && hasRenderedFirstFrame
                exo?.let { onPositionUpdate(it.currentPosition, it.duration) }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(PLAYER_TAG, "onPlayerError code=${error.errorCodeName}", error)
                playbackError = error
                if (error.isHentaiMamaDecoderProblem()) {
                    // This device can't decode the stream: let the bundled mpv player take over.
                    val base = com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetwork
                        .baseUrl.trimEnd('/')
                    com.yenaly.han1meviewer.ui.component.player.StreamFallback
                        .playInMpv(context, state.url, "$base/")
                }
            }

            override fun onRenderedFirstFrame() {
                Log.d(PLAYER_TAG, "onRenderedFirstFrame")
                hasRenderedFirstFrame = true
                isRebuffering = false
            }
        }
        exo?.addListener(listener)
        onDispose { exo?.removeListener(listener) }
    }

    LaunchedEffect(state.url, exoPlayer) {

        hasRenderedFirstFrame = exoPlayer?.playbackState == Player.STATE_READY
        isRebuffering = false
    }

    LaunchedEffect(state.speed, tempSpeed, exoPlayer) {
        exoPlayer?.setPlaybackSpeed(tempSpeed ?: state.speed)
    }

    LaunchedEffect(state.isPlaying, isExoReady, surfaceReady, exoPlayer) {
        val exo = exoPlayer ?: return@LaunchedEffect
        val shouldPlay = state.isPlaying && surfaceReady
        if (exo.playWhenReady != shouldPlay) {
            exo.playWhenReady = shouldPlay
        }
    }

    LaunchedEffect(exoPlayer) {
        val exo: ExoPlayer = exoPlayer ?: return@LaunchedEffect
        while (true) {
            delay(500)
            val pos: Long = exo.currentPosition
            val dur: Long = exo.duration
            if (pos != lastPosition) {
                lastPosition = pos
                onPositionUpdate(pos, dur)
            }
        }
    }

    val fitState = remember { VideoFitState() }
    var stretched by rememberSaveable { mutableStateOf(com.yenaly.han1meviewer.ui.component.player.SitePlayerSettings.config(com.yenaly.han1meviewer.SiteType.HENTAIMAMA).stretchFullscreen) }
    fitState.stretched = stretched && isFullscreen
    var fitFrame by remember { mutableStateOf<FrameLayout?>(null) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                fitState.updateFrom(videoSize)
                fitFrame?.let { frame ->
                    frame.getChildAt(0)?.let { fitVideoSurface(frame, it, fitState) }
                }
            }
        }
        exoPlayer?.videoSize?.let { fitState.updateFrom(it) }
        exoPlayer?.addListener(listener)
        onDispose { exoPlayer?.removeListener(listener) }
    }

    val surfaceModifier = if (isFullscreen) {
        modifier.fillMaxSize()
    } else {
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    }

    Box(
        modifier = surfaceModifier
            .background(Color.Black)
            .clipToBounds()
            .onSizeChanged { size ->
                val ok = size.width > 0 && size.height > 0
                if (ok != surfaceReady) {
                    Log.d(PLAYER_TAG, "onSizeChanged: surfaceReady=$ok")
                    surfaceReady = ok
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (sourceReady && exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    Log.d(PLAYER_TAG, "AndroidView factory: attaching surface")
                    FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )

                        val textureView = TextureView(ctx)
                        textureView.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                surface: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {
                                Log.d(PLAYER_TAG, "surface available, attaching to player")
                                exoPlayer.setVideoTextureView(textureView)
                            }

                            override fun onSurfaceTextureSizeChanged(
                                surface: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) = Unit

                            override fun onSurfaceTextureDestroyed(
                                surface: SurfaceTexture,
                            ): Boolean {
                                exoPlayer.clearVideoTextureView(textureView)
                                return true
                            }

                            override fun onSurfaceTextureUpdated(
                                surface: SurfaceTexture,
                            ) = Unit
                        }
                        addView(textureView)
                        fitFrame = this
                        installVideoFit(this, textureView, fitState)

                        val subtitle = TextView(ctx).apply {
                            setTextColor(AndroidColor.WHITE)
                            setBackgroundColor(AndroidColor.TRANSPARENT)
                            setShadowLayer(4f, 0f, 0f, AndroidColor.BLACK)
                            textSize = 22f
                            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                            setPadding(32, 16, 32, 56)
                            maxLines = 3
                            setEllipsize(android.text.TextUtils.TruncateAt.END)
                            visibility = android.view.View.GONE
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            ).apply {
                                gravity = Gravity.BOTTOM
                                bottomMargin = if (isFullscreen) 220 else 160
                                leftMargin = 32
                                rightMargin = 32
                            }
                        }
                        addView(subtitle)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { frame ->
                    fitState.stretched = stretched && isFullscreen
                    frame.getChildAt(0)?.let { fitVideoSurface(frame, it, fitState) }
                },
                onRelease = { view ->
                    Log.d(PLAYER_TAG, "AndroidView onRelease: detaching surface")
                    (view as? ViewGroup)?.getChildAt(0)?.let { child ->
                        (child as? TextureView)?.let { exoPlayer.clearVideoTextureView(it) }
                    }
                    (view as? ViewGroup)?.removeAllViews()
                },
            )

            playbackError?.let { err ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = if (err.isHentaiMamaDecoderProblem())
                            "This phone can't decode this video (10-bit H.264)."
                        else "Playback failed: ${err.errorCodeName}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.size(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { openHentaiMamaExternal(context, state.url) }) {
                            Text("Open in external player")
                        }
                        Button(onClick = {
                            playbackError = null
                            exoPlayer.prepare()
                        }) { Text("Retry") }
                    }
                }
            }

            if (showResumeButton && !state.isPlaying) {
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
                            text = "Resume from ${formatTime(savedPosition)}?",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(onClick = onResumeFromSaved) {
                                Text("Resume")
                            }
                            Button(onClick = onStartFromBeginning) {
                                Text("Start Over")
                            }
                        }
                    }
                }
            }

            if (!showResumeButton) {
                SitePlayerOverlay(
                    config = config,
                    title = title,
                    isPlaying = state.isPlaying,
                    isBuffering = isBuffering,
                    positionMs = state.position,
                    durationMs = state.duration,
                    bufferedMs = exoPlayer.bufferedPosition.coerceAtLeast(0L),
                    qualityLabel = state.quality.ifBlank { "Auto" },
                    speed = state.speed,
                    isFullscreen = isFullscreen,
                    onPlayPause = onPlayPause,
                    onSeekTo = onSeek,
                    onSeekBy = onSkip,
                    onToggleFullscreen = onToggleFullscreen,
                    onBack = onBack,
                    speedOptions = state.availableSpeeds,
                    onSpeedSelect = onSpeedSelect,
                    qualityOptions = state.qualityOptions.keys.toList(),
                    onQualitySelect = onQualitySelect,
                    stretched = stretched,
                    onToggleStretch = { stretched = !stretched },
                    onTemporarySpeed = { tempSpeed = it },
                )
            }
        } else {
            if (coverUrl.isNotBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = "Fetching video link…",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

internal fun formatTime(ms: Long): String {
    val total: Long = (ms / 1000).coerceAtLeast(0L)
    val h: Long = total / 3600
    val m: Long = (total % 3600) / 60
    val s: Long = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
