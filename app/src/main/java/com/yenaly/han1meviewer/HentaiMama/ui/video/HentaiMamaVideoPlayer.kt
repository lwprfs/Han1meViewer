package com.yenaly.han1meviewer.HentaiMama.ui.video

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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetwork
import kotlinx.coroutines.delay

private const val PLAYER_TAG = "HentaiMamaPlayer"

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun HentaiMamaVideoPlayer(
    state: HentaiMamaPlayerState,
    coverUrl: String,
    isFullscreen: Boolean,
    isVisible: Boolean = true,
    playerOverride: ExoPlayer? = null,
    onPlayerReady: (ExoPlayer) -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    onPositionUpdate: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var ownsPlayer by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var subtitleView by remember { mutableStateOf<TextView?>(null) }
    var lastPosition by remember { mutableLongStateOf(0L) }
    var surfaceReady by remember { mutableStateOf(false) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var hasRenderedFirstFrame by remember { mutableStateOf(false) }

    Log.d(
        PLAYER_TAG,
        "compose: isReady=${state.isReady}, urlLen=${state.url.length}, " +
                "urlHead=${state.url.take(80)}, isFullscreen=$isFullscreen, " +
                "isVisible=$isVisible, playbackState=$playbackState, " +
                "hasOverride=${playerOverride != null}"
    )

    val surfaceModifier: Modifier = if (isFullscreen) {
        modifier.fillMaxSize()
    } else {
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    }

    val sourceReady = state.isReady && state.url.isNotBlank()
    val isExoReady = playbackState == Player.STATE_READY

    val isBuffering = when {
        !sourceReady -> false
        !surfaceReady -> false
        !hasRenderedFirstFrame && (playbackState == Player.STATE_BUFFERING ||
                playbackState == Player.STATE_IDLE) -> true
        playbackState == Player.STATE_BUFFERING -> true
        else -> false
    }

    val effectiveIsPlaying = state.isPlaying && isExoReady

    LaunchedEffect(isVisible, isExoReady) {
        val exo = player ?: return@LaunchedEffect
        if (!isVisible && exo.playWhenReady) {
            exo.playWhenReady = false
        }
    }

    Box(
        modifier = surfaceModifier
            .background(Color.Black)
            .clipToBounds()
            .onSizeChanged { size ->
                val ok = size.width > 0 && size.height > 0
                if (ok != surfaceReady) {
                    Log.d(
                        PLAYER_TAG,
                        "onSizeChanged: width=${size.width}, height=${size.height}, surfaceReady=$ok"
                    )
                    surfaceReady = ok
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (sourceReady && isVisible) {
            AndroidView(
                factory = { ctx ->
                    Log.d(
                        PLAYER_TAG,
                        "AndroidView factory: " +
                                "override=${playerOverride != null}"
                    )
                    FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )

                        val textureView = TextureView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(
                                    surface: SurfaceTexture,
                                    width: Int,
                                    height: Int,
                                ) {
                                    player?.setVideoTextureView(this@apply)
                                }

                                override fun onSurfaceTextureSizeChanged(
                                    surface: SurfaceTexture,
                                    width: Int,
                                    height: Int,
                                ) = Unit

                                override fun onSurfaceTextureDestroyed(
                                    surface: SurfaceTexture,
                                ): Boolean {
                                    if (ownsPlayer) {
                                        player?.setVideoTextureView(null)
                                    }
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(
                                    surface: SurfaceTexture,
                                ) = Unit
                            }
                        }
                        addView(textureView)

                        val subtitle = TextView(ctx).apply {
                            subtitleView = this
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

                        val exo: ExoPlayer
                        if (playerOverride != null) {
                            exo = playerOverride
                            ownsPlayer = false
                        } else {
                            exo = ExoPlayer.Builder(ctx).build()
                            ownsPlayer = true
                            val base: String = HentaiMamaNetwork.baseUrl
                            val dsFactory = DefaultDataSource.Factory(
                                ctx,
                                DefaultHttpDataSource.Factory().setDefaultRequestProperties(
                                    hashMapOf(
                                        "Referer" to "$base/",
                                        "Origin" to base.trimEnd('/'),
                                    )
                                )
                            )
                            val mediaItem: MediaItem = MediaItem.Builder()
                                .setUri(state.url)
                                .build()
                            val mediaSource = if (state.url.contains(".m3u8")) {
                                HlsMediaSource.Factory(dsFactory).createMediaSource(mediaItem)
                            } else {
                                ProgressiveMediaSource.Factory(dsFactory)
                                    .createMediaSource(mediaItem)
                            }
                            exo.setMediaSource(mediaSource)
                            exo.prepare()
                            exo.setPlaybackSpeed(state.speed)
                            Log.d(
                                PLAYER_TAG,
                                "ExoPlayer prepared with url=${state.url.take(80)}"
                            )
                        }

                        player = exo
                        exo.setVideoTextureView(textureView)
                        onPlayerReady(exo)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { view ->
                    player?.setVideoTextureView(null)
                    (view as? ViewGroup)?.removeAllViews()
                    subtitleView = null
                },
            )

            if (playerOverride == null) {
                LaunchedEffect(state.url) {
                    val exo: ExoPlayer = player ?: return@LaunchedEffect
                    Log.d(
                        PLAYER_TAG,
                        "url changed, swapping media source: ${state.url.take(80)}"
                    )
                    hasRenderedFirstFrame = false
                    val base: String = HentaiMamaNetwork.baseUrl
                    val dsFactory = DefaultDataSource.Factory(
                        context,
                        DefaultHttpDataSource.Factory().setDefaultRequestProperties(
                            hashMapOf(
                                "Referer" to "$base/",
                                "Origin" to base.trimEnd('/'),
                            )
                        )
                    )
                    val item: MediaItem = MediaItem.Builder().setUri(state.url).build()
                    val source = if (state.url.contains(".m3u8")) {
                        HlsMediaSource.Factory(dsFactory).createMediaSource(item)
                    } else {
                        ProgressiveMediaSource.Factory(dsFactory).createMediaSource(item)
                    }
                    val wasPlaying: Boolean = exo.playWhenReady
                    val pos: Long = exo.currentPosition
                    exo.setMediaSource(source)
                    exo.prepare()
                    exo.seekTo(pos)
                    exo.playWhenReady = wasPlaying
                }
            }

            LaunchedEffect(state.speed) {
                player?.setPlaybackSpeed(state.speed)
            }

            LaunchedEffect(state.isPlaying, isExoReady, isVisible, surfaceReady) {
                val exo: ExoPlayer = player ?: return@LaunchedEffect
                val shouldPlay = state.isPlaying && isVisible && surfaceReady
                if (exo.playWhenReady != shouldPlay) {
                    exo.playWhenReady = shouldPlay
                }
            }

            DisposableEffect(player) {
                val exo: ExoPlayer? = player
                val listener = object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        showControls = true
                    }

                    override fun onPlaybackStateChanged(newState: Int) {
                        playbackState = newState
                        val p: ExoPlayer = player ?: return
                        if (newState == Player.STATE_READY) {
                            onPositionUpdate(p.currentPosition, p.duration)
                        }
                        Log.d(PLAYER_TAG, "playbackState=$newState")
                    }

                    override fun onRenderedFirstFrame() {
                        hasRenderedFirstFrame = true
                        Log.d(PLAYER_TAG, "onRenderedFirstFrame")
                    }
                }
                exo?.addListener(listener)
                onDispose { exo?.removeListener(listener) }
            }

            LaunchedEffect(player, state.isPlaying) {
                val exo: ExoPlayer = player ?: return@LaunchedEffect
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

            LaunchedEffect(showControls, effectiveIsPlaying) {
                if (showControls && effectiveIsPlaying) {
                    delay(3000)
                    showControls = false
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                showControls = !showControls
                            },
                            onDoubleTap = { offset ->
                                val half: Float = size.width / 2f
                                if (offset.x < half) onSkip(-10_000L)
                                else onSkip(10_000L)
                            },
                        )
                    },
            )

            if (isBuffering) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = showControls && !isBuffering,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                PlayerControls(
                    state = state,
                    isFullscreen = isFullscreen,
                    effectiveIsPlaying = effectiveIsPlaying,
                    onPlayPause = onPlayPause,
                    onSeek = onSeek,
                    onSkip = onSkip,
                    onToggleFullscreen = onToggleFullscreen,
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

            if (isVisible && sourceReady && !surfaceReady) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = "Preparing player…",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else if (isVisible && !sourceReady) {
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
            } else if (isVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (ownsPlayer) {
                player?.let { exo: ExoPlayer ->
                    exo.setVideoTextureView(null)
                    exo.stop()
                    exo.release()
                }
            } else {
                player?.setVideoTextureView(null)
            }
            player = null
            ownsPlayer = false
        }
    }
}

@Composable
private fun PlayerControls(
    state: HentaiMamaPlayerState,
    isFullscreen: Boolean,
    effectiveIsPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onSkip(-10_000L) },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Filled.Replay10,
                    contentDescription = "Rewind 10 seconds",
                    tint = Color.White,
                )
            }

            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(if (isFullscreen) 72.dp else 60.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
            ) {
                Icon(
                    imageVector = if (effectiveIsPlaying) Icons.Filled.Pause
                    else Icons.Filled.PlayArrow,
                    contentDescription = if (effectiveIsPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(if (isFullscreen) 44.dp else 36.dp),
                )
            }

            IconButton(
                onClick = { onSkip(10_000L) },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Filled.Forward10,
                    contentDescription = "Forward 10 seconds",
                    tint = Color.White,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(if (isFullscreen) Modifier.navigationBarsPadding() else Modifier)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = formatTime(state.position),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(52.dp),
                )
                Slider(
                    value = if (state.duration > 0) {
                        state.position.toFloat() / state.duration
                    } else 0f,
                    onValueChange = { newValue: Float ->
                        onSeek((newValue * state.duration).toLong())
                    },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    ),
                )
                Text(
                    text = formatTime(state.duration),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(52.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.quality.ifBlank { "Auto" },
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall,
                )
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Filled.FullscreenExit
                        else Icons.Filled.Fullscreen,
                        contentDescription = "Fullscreen",
                        tint = Color.White,
                    )
                }
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
