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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
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
    onPlayerReady: (ExoPlayer) -> Unit,
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
) {
    val context = LocalContext.current

    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var lastPosition by remember { mutableLongStateOf(0L) }
    var surfaceReady by remember { mutableStateOf(false) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var hasRenderedFirstFrame by remember { mutableStateOf(false) }

    val sourceReady = state.isReady && state.url.isNotBlank()
    val isExoReady = playbackState == Player.STATE_READY
    val isBuffering = sourceReady && surfaceReady && !hasRenderedFirstFrame &&
            (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE)
    val effectiveIsPlaying = state.isPlaying && isExoReady

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
        if (sourceReady) {
            AndroidView(
                factory = { ctx ->
                    Log.d(PLAYER_TAG, "AndroidView factory")
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
                                    player?.setVideoTextureView(null)
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(
                                    surface: SurfaceTexture,
                                ) = Unit
                            }
                        }
                        addView(textureView)

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

                        val exo = ExoPlayer.Builder(ctx).build().also {
                            val base = HentaiMamaNetwork.baseUrl
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
                            it.setMediaSource(mediaSource)
                            it.prepare()
                            it.setPlaybackSpeed(state.speed)
                        }

                        player = exo
                        exo.setVideoTextureView(textureView)
                        onPlayerReady(exo)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { view ->
                    (view as? ViewGroup)?.removeAllViews()
                },
            )

            LaunchedEffect(state.url, player) {
                val exo = player ?: return@LaunchedEffect
                if (exo.currentMediaItem?.localConfiguration?.uri.toString() == state.url) {
                    return@LaunchedEffect
                }
                Log.d(PLAYER_TAG, "url changed, swapping media source")
                hasRenderedFirstFrame = false
                val base = HentaiMamaNetwork.baseUrl
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

            LaunchedEffect(state.speed, player) {
                player?.setPlaybackSpeed(state.speed)
            }

            LaunchedEffect(state.isPlaying, isExoReady, surfaceReady, player) {
                val exo = player ?: return@LaunchedEffect
                val shouldPlay = state.isPlaying && surfaceReady
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
                        player?.let { onPositionUpdate(it.currentPosition, it.duration) }
                    }

                    override fun onRenderedFirstFrame() {
                        hasRenderedFirstFrame = true
                    }
                }
                exo?.addListener(listener)
                onDispose { exo?.removeListener(listener) }
            }

            LaunchedEffect(player) {
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
                            onTap = { showControls = !showControls },
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

            AnimatedVisibility(
                visible = showControls && !isBuffering && !showResumeButton,
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

    DisposableEffect(player) {
        val exo: ExoPlayer? = player
        onDispose {
            Log.d(PLAYER_TAG, "Dispose: releasing player")
            exo?.release()
            player = null
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
