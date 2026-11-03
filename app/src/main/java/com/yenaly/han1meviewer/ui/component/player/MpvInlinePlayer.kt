package com.yenaly.han1meviewer.ui.component.player

import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.USER_AGENT
import com.yenaly.han1meviewer.util.AnimeShaders.getCert
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay

private const val TAG = "MpvInlinePlayer"

@Composable
fun MpvInlinePlayer(
    url: String,
    modifier: Modifier = Modifier,
    referer: String? = null,
    startPositionMs: Long = 0L,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onPositionUpdate: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val main = remember { Handler(Looper.getMainLooper()) }

    var positionSec by remember(url) { mutableFloatStateOf(0f) }
    var durationSec by remember(url) { mutableFloatStateOf(0f) }
    var paused by remember(url) { mutableStateOf(false) }
    var buffering by remember(url) { mutableStateOf(true) }
    var controlsVisible by remember(url) { mutableStateOf(true) }
    var scrubbing by remember(url) { mutableStateOf(false) }
    var scrubValue by remember(url) { mutableFloatStateOf(0f) }
    var tick by remember(url) { mutableStateOf(0) }

    val observer = remember(url) {
        object : MPVLib.EventObserver {
            override fun eventProperty(property: String) = Unit

            override fun eventProperty(property: String, value: Long) = Unit

            override fun eventProperty(property: String, value: Boolean) {
                main.post {
                    when (property) {
                        "pause" -> paused = value
                        "paused-for-cache" -> buffering = value
                    }
                }
            }

            override fun eventProperty(property: String, value: String) = Unit

            override fun eventProperty(property: String, value: Double) {
                main.post {
                    when (property) {
                        "time-pos" -> positionSec = value.toFloat()
                        "duration" -> durationSec = value.toFloat()
                    }
                }
            }

            override fun event(eventId: Int) {
                main.post {
                    when (eventId) {
                        MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART -> buffering = false
                        MPVLib.mpvEventId.MPV_EVENT_START_FILE -> buffering = true
                    }
                }
            }
        }
    }

    DisposableEffect(url) {
        onDispose {
            runCatching {
                MPVLib.setPropertyBoolean("pause", true)
                MPVLib.command(arrayOf("loadfile", "", "replace"))
                MPVLib.setPropertyString("vo", "null")
                MPVLib.setOptionString("force-window", "no")
                MPVLib.detachSurface()
                MPVLib.removeObserver(observer)
            }.onFailure { Log.w(TAG, "release failed", it) }
        }
    }

    LaunchedEffect(url) {
        while (true) {
            delay(1000)
            if (durationSec > 0f) {
                onPositionUpdate((positionSec * 1000).toLong(), (durationSec * 1000).toLong())
            }
        }
    }

    LaunchedEffect(tick, paused, scrubbing) {
        if (!paused && !scrubbing && controlsVisible) {
            delay(3000)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            ) {
                controlsVisible = !controlsVisible
                tick++
            },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                var started = false
                var surface: Surface? = null
                TextureView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            st: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            val s = Surface(st)
                            surface = s
                            if (!started) {
                                started = true
                                initMpv(ctx, referer)
                                MPVLib.addObserver(observer)
                                if (startPositionMs > 0) {
                                    MPVLib.setOptionString("start", (startPositionMs / 1000.0).toString())
                                }
                                MPVLib.setOptionString("force-window", "yes")
                                MPVLib.attachSurface(s)
                                MPVLib.setPropertyString("android-surface-size", "${width}x$height")
                                MPVLib.command(arrayOf("loadfile", url))
                            } else {
                                MPVLib.attachSurface(s)
                                MPVLib.setPropertyString("android-surface-size", "${width}x$height")
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(
                            st: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            MPVLib.setPropertyString("android-surface-size", "${width}x$height")
                        }

                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            runCatching { MPVLib.detachSurface() }
                            surface?.release()
                            surface = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                    }
                }
            },
        )

        if (buffering) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
        }

        if (controlsVisible) {
            IconButton(
                onClick = {
                    MPVLib.setPropertyBoolean("pause", !paused)
                    tick++
                },
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.Black.copy(alpha = 0.45f), androidx.compose.foundation.shape.CircleShape),
            ) {
                Icon(
                    imageVector = if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatTime(if (scrubbing) scrubValue else positionSec),
                    color = Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
                Slider(
                    value = (if (scrubbing) scrubValue else positionSec)
                        .coerceIn(0f, durationSec.coerceAtLeast(1f)),
                    onValueChange = {
                        scrubbing = true
                        scrubValue = it
                        tick++
                    },
                    onValueChangeFinished = {
                        MPVLib.command(
                            arrayOf("seek", scrubValue.toString(), "absolute", "exact"),
                        )
                        scrubbing = false
                    },
                    valueRange = 0f..durationSec.coerceAtLeast(1f),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                Text(
                    text = formatTime(durationSec),
                    color = Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

private fun initMpv(context: android.content.Context, referer: String?) {
    val opts = linkedMapOf(
        "vo" to if (Preferences.enableGPUNextRenderer) "gpu-next" else "gpu",
        "profile" to when (Preferences.mpvProfile) {
            "gpu-hq" -> "gpu-hq"
            "fast" -> "fast"
            else -> "default"
        },
        "hwdec" to when (Preferences.mpvHwdec) {
            "Auto" -> "auto"
            "HW" -> "mediacodec-copy"
            "HW+" -> "mediacodec"
            "Vulkan" -> "vulkan-copy"
            "vulkan+" -> "vulkan"
            "SW" -> "no"
            else -> "auto"
        },
        "msg-level" to "all=warn",
        "cache" to "yes",
        "cache-secs" to Preferences.mpvCacheSecs.toString(),
        "vd-lavc-threads" to Runtime.getRuntime().availableProcessors().toString(),
        "framedrop" to if (Preferences.mpvFramedrop) "vo" else "no",
        "cache-pause" to "no",
        "network-timeout" to Preferences.mpvNetworkTimeout.toString(),
        "tls-ca-file" to getCert(context),
        "tls-verify" to if (Preferences.mpvTlsVerify) "no" else "yes",
        "user-agent" to USER_AGENT,
    )
    opts.forEach { (k, v) -> MPVLib.setOptionString(k, v) }
    if (!referer.isNullOrBlank()) MPVLib.setOptionString("referrer", referer)

    mapOf(
        "time-pos" to MPVLib.mpvFormat.MPV_FORMAT_DOUBLE,
        "duration" to MPVLib.mpvFormat.MPV_FORMAT_DOUBLE,
        "pause" to MPVLib.mpvFormat.MPV_FORMAT_FLAG,
        "paused-for-cache" to MPVLib.mpvFormat.MPV_FORMAT_FLAG,
    ).forEach { (name, type) -> MPVLib.observeProperty(name, type) }
}

private fun formatTime(sec: Float): String {
    val total = sec.toInt().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
