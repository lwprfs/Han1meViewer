package com.yenaly.han1meviewer.ui.component.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.screen.video.GestureIndicatorOverlay
import com.yenaly.han1meviewer.ui.screen.video.GestureIndicatorType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private tailrec fun Context.findPlayerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findPlayerActivity()
    else -> null
}

internal fun formatPlayerTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatSpeedLabel(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}.0x" else "${speed}x"

@Composable
fun SitePlayerOverlay(
    config: SitePlayerConfig,
    title: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    qualityLabel: String,
    speed: Float,
    isFullscreen: Boolean,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bufferedMs: Long = 0L,
    speedOptions: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f),
    onSpeedSelect: (Float) -> Unit = {},
    qualityOptions: List<String> = emptyList(),
    onQualitySelect: (String) -> Unit = {},
    stretched: Boolean = false,
    onToggleStretch: (() -> Unit)? = null,
    onTemporarySpeed: (Float?) -> Unit = {},
    extraActions: @Composable RowScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findPlayerActivity() }

    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var boosted by remember { mutableStateOf(false) }

    var indicatorVisible by remember { mutableStateOf(false) }
    var indicatorType by remember { mutableStateOf(GestureIndicatorType.Progress) }
    var indicatorPercent by remember { mutableFloatStateOf(0f) }
    var indicatorText by remember { mutableStateOf("") }
    var indicatorTick by remember { mutableLongStateOf(0L) }

    val isPlayingNow by rememberUpdatedState(isPlaying)
    val positionNow by rememberUpdatedState(positionMs)
    val durationNow by rememberUpdatedState(durationMs)
    val seekToNow by rememberUpdatedState(onSeekTo)
    val seekByNow by rememberUpdatedState(onSeekBy)
    val tempSpeedNow by rememberUpdatedState(onTemporarySpeed)

    LaunchedEffect(controlsVisible, isPlaying, scrubbing, menuOpen, locked, config.controlsHideMs) {
        if (controlsVisible && isPlaying && !scrubbing && !menuOpen) {
            delay(config.controlsHideMs)
            controlsVisible = false
        }
    }

    LaunchedEffect(indicatorTick) {
        if (indicatorVisible) {
            delay(600)
            indicatorVisible = false
        }
    }

    var clock by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            clock = fmt.format(Date())
            delay(30_000)
        }
    }

    fun showIndicator(type: GestureIndicatorType, percent: Float, text: String) {
        indicatorType = type
        indicatorPercent = percent.coerceIn(0f, 1f)
        indicatorText = text
        indicatorVisible = true
        indicatorTick = System.nanoTime()
    }

    Box(modifier = modifier.fillMaxSize()) {

        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(locked, config.doubleTapSeekMs, config.longPressSpeed) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            if (!locked) {
                                val back = offset.x < size.width / 2f
                                seekByNow(if (back) -config.doubleTapSeekMs else config.doubleTapSeekMs)
                                showIndicator(
                                    GestureIndicatorType.Progress,
                                    if (durationNow > 0) positionNow.toFloat() / durationNow else 0f,
                                    (if (back) "-" else "+") + (config.doubleTapSeekMs / 1000) + "s",
                                )
                            }
                        },
                        onLongPress = {
                            if (!locked && isPlayingNow) {
                                boosted = true
                                tempSpeedNow(config.longPressSpeed)
                            }
                        },
                        onPress = {
                            tryAwaitRelease()
                            if (boosted) {
                                boosted = false
                                tempSpeedNow(null)
                            }
                        },
                    )
                }
                .pointerInput(locked, config.gesturesEnabled, config.slideSensitivity) {
                    if (locked || !config.gesturesEnabled) return@pointerInput
                    val factor = 0.5f + config.slideSensitivity * 0.15f
                    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    var mode = 0
                    var dx = 0f
                    var dy = 0f
                    var startPos = 0L
                    var startBrightness = 0.5f
                    var startVolume = 0
                    var target = 0L
                    detectDragGestures(
                        onDragStart = { offset ->
                            mode = 0
                            dx = 0f
                            dy = 0f
                            startPos = positionNow
                            target = startPos
                            val w = activity?.window
                            val current = w?.attributes?.screenBrightness ?: -1f
                            startBrightness = if (current < 0f) 0.5f else current
                            startVolume = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0

                            mode = if (offset.x < size.width / 2f) -2 else -3
                        },
                        onDragEnd = {
                            if (mode == 1 && durationNow > 0) seekToNow(target)
                            mode = 0
                        },
                        onDragCancel = { mode = 0 },
                        onDrag = { change, drag ->
                            change.consume()
                            dx += drag.x
                            dy += drag.y
                            if (mode < 0) {

                                if (kotlin.math.abs(dx) > 24f && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                                    mode = 1
                                } else if (kotlin.math.abs(dy) > 24f) {
                                    mode = if (mode == -2) 2 else 3
                                }
                            }
                            when (mode) {
                                1 -> if (durationNow > 0) {
                                    val range = 30_000L + config.slideSensitivity * 15_000L
                                    target = (startPos + (dx / size.width * range).toLong())
                                        .coerceIn(0L, durationNow)
                                    showIndicator(
                                        GestureIndicatorType.Progress,
                                        target.toFloat() / durationNow,
                                        "${formatPlayerTime(target)} / ${formatPlayerTime(durationNow)}",
                                    )
                                }

                                2 -> {
                                    val value = (startBrightness - dy / size.height * factor)
                                        .coerceIn(0.02f, 1f)
                                    activity?.window?.let { w ->
                                        val attrs = w.attributes
                                        attrs.screenBrightness = value
                                        w.attributes = attrs
                                    }
                                    showIndicator(
                                        GestureIndicatorType.Brightness, value,
                                        "${(value * 100).toInt()}%",
                                    )
                                }

                                3 -> if (audio != null) {
                                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        .coerceAtLeast(1)
                                    val value = (startVolume.toFloat() / max - dy / size.height * factor)
                                        .coerceIn(0f, 1f)
                                    audio.setStreamVolume(
                                        AudioManager.STREAM_MUSIC, (value * max).toInt(), 0,
                                    )
                                    showIndicator(
                                        GestureIndicatorType.Volume, value,
                                        "${(value * 100).toInt()}%",
                                    )
                                }
                            }
                        },
                    )
                },
        )

        AnimatedVisibility(
            visible = controlsVisible && !locked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && !locked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            GlassBar(
                modifier = Modifier
                    .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                corner = 20.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = title,
                        color = Color.White.copy(alpha = 0.95f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    extraActions()
                    Box {
                        var open by remember { mutableStateOf(false) }
                        ChipText(formatSpeedLabel(speed)) {
                            open = true
                            menuOpen = true
                        }
                        DropdownMenu(
                            expanded = open,
                            onDismissRequest = {
                                open = false
                                menuOpen = false
                            },
                        ) {
                            speedOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(formatSpeedLabel(option)) },
                                    onClick = {
                                        open = false
                                        menuOpen = false
                                        onSpeedSelect(option)
                                    },
                                )
                            }
                        }
                    }
                    if (clock.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = clock,
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        if (locked) {
            if (isBuffering) {
                Box(Modifier.align(Alignment.Center)) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
                }
            }
        } else if (isBuffering || controlsVisible || !isPlaying) {

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(if (isPlaying) 60.dp else 72.dp),
                contentAlignment = Alignment.Center,
            ) {
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.fillMaxSize(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isPlaying) Color.Black.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                    ),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.site_pause) else stringResource(R.string.site_play),
                        tint = Color.White,
                        modifier = Modifier.size(if (isPlaying) 34.dp else 42.dp),
                    )
                }
                if (isBuffering) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            FilledIconButton(
                onClick = {
                    locked = !locked
                    controlsVisible = true
                },
                modifier = Modifier
                    .padding(end = 14.dp)
                    .size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.45f),
                ),
            ) {
                Icon(
                    imageVector = if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                    contentDescription = if (locked) stringResource(R.string.site_unlock) else stringResource(R.string.site_lock),
                    tint = Color.White,
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && !locked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            GlassBar(
                modifier = Modifier
                    .then(if (isFullscreen) Modifier.navigationBarsPadding() else Modifier)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                corner = 18.dp,
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    var scrub by remember { mutableStateOf<Float?>(null) }
                    val fraction = if (durationMs > 0) {
                        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    } else 0f
                    val buffered = if (durationMs > 0) {
                        (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    } else 0f
                    SitePlayerSlider(
                        value = scrub ?: fraction,
                        buffered = buffered,
                        onValueChange = {
                            scrubbing = true
                            scrub = it
                        },
                        onValueChangeFinished = {
                            scrub?.let { if (durationMs > 0) onSeekTo((it * durationMs).toLong()) }
                            scrub = null
                            scrubbing = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onPlayPause, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        val shownPos = scrub?.let { (it * durationMs).toLong() } ?: positionMs
                        Text(
                            text = "${formatPlayerTime(shownPos)} / ${formatPlayerTime(durationMs)}",
                            color = Color.White.copy(alpha = 0.88f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Spacer(Modifier.weight(1f))
                        if (qualityLabel.isNotBlank()) {
                            Box {
                                var open by remember { mutableStateOf(false) }
                                Text(
                                    text = qualityLabel,
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .then(
                                            if (qualityOptions.size > 1) Modifier.clickable {
                                                open = true
                                                menuOpen = true
                                            } else Modifier
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                                DropdownMenu(
                                    expanded = open,
                                    onDismissRequest = {
                                        open = false
                                        menuOpen = false
                                    },
                                ) {
                                    qualityOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = {
                                                open = false
                                                menuOpen = false
                                                onQualitySelect(option)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        if (onToggleStretch != null && isFullscreen) {
                            IconButton(onClick = onToggleStretch, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Outlined.AspectRatio,
                                    contentDescription = stringResource(R.string.site_stretch),
                                    tint = if (stretched) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Outlined.FullscreenExit
                                else Icons.Outlined.Fullscreen,
                                contentDescription = stringResource(R.string.site_fullscreen),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }

        if (config.showBottomProgress && !controlsVisible && durationMs > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.2f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((positionMs.toFloat() / durationMs).coerceIn(0f, 1f))
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }

        if (boosted) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "${formatSpeedLabel(config.longPressSpeed)} ▶▶",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        GestureIndicatorOverlay(
            visible = indicatorVisible,
            type = indicatorType,
            percent = indicatorPercent,
            modifier = Modifier.align(Alignment.Center),
            text = indicatorText,
        )
    }
}

@Composable
private fun GlassBar(
    modifier: Modifier = Modifier,
    corner: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(Color.Black.copy(alpha = 0.32f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(corner)),
    ) {
        content()
    }
}

@Composable
private fun ChipText(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.88f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
fun SitePlayerSlider(
    value: Float,
    buffered: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier,
        thumb = {
            Box(modifier = Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(Color.White.copy(alpha = 0.22f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color.White, CircleShape)
                )
            }
        },
        track = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(100))
                        .background(Color.White.copy(alpha = 0.14f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(buffered.coerceIn(0f, 1f))
                        .height(3.dp)
                        .clip(RoundedCornerShape(100))
                        .background(Color.White.copy(alpha = 0.32f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(value.coerceIn(0f, 1f))
                        .height(3.dp)
                        .clip(RoundedCornerShape(100))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
                                )
                            )
                        )
                )
            }
        },
    )
}
