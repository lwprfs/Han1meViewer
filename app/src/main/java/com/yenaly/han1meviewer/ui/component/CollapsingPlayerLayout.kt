package com.yenaly.han1meviewer.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val PARALLAX_FACTOR = 0.5f
private const val SCRIM_MAX_ALPHA = 0.65f
private const val SNAP_VELOCITY = 400f

/**
 * hanime1.me style collapsing video player.
 *
 * - Scrolling the list down collapses the player first (parallax + dimming).
 * - It only expands again once the list is back at the top.
 * - When released half-way, the player animates (spring) to fully collapsed / expanded,
 *   and flings are used to collapse / expand it.
 */
@Composable
fun CollapsingPlayerLayout(
    modifier: Modifier = Modifier,
    player: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    var playerHeightPx by remember { mutableFloatStateOf(0f) }
    var offsetPx by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    val connection = remember {
        var animJob: Job? = null

        fun animateTo(target: Float, velocity: Float = 0f) {
            animJob?.cancel()
            animJob = scope.launch {
                animate(
                    initialValue = offsetPx,
                    targetValue = target,
                    initialVelocity = velocity,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) { value, _ ->
                    offsetPx = value.coerceIn(-playerHeightPx, 0f)
                }
            }
        }

        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy != 0f) animJob?.cancel()
                if (dy < 0f && playerHeightPx > 0f) {
                    val old = offsetPx
                    val new = (old + dy).coerceIn(-playerHeightPx, 0f)
                    offsetPx = new
                    return Offset(0f, new - old)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val dy = available.y
                if (dy > 0f && playerHeightPx > 0f) {
                    val old = offsetPx
                    val new = (old + dy).coerceIn(-playerHeightPx, 0f)
                    offsetPx = new
                    return Offset(0f, new - old)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val h = playerHeightPx
                if (h <= 0f) return Velocity.Zero
                val off = offsetPx
                val vy = available.y
                // Fast swipe up: collapse the player before the list flings.
                if (vy < -SNAP_VELOCITY && off > -h) {
                    animateTo(-h, vy)
                    return Velocity(0f, vy)
                }
                // Released half-way (the list is at top then): snap to an end.
                if (off < 0f && off > -h) {
                    val target = when {
                        vy > SNAP_VELOCITY -> 0f
                        off < -h / 2f -> -h
                        else -> 0f
                    }
                    animateTo(target, vy)
                    return Velocity(0f, vy)
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // List was flung down to its top: bring the player back.
                if (available.y > 0f && offsetPx < 0f) {
                    animateTo(0f, available.y)
                    return Velocity(0f, available.y)
                }
                return Velocity.Zero
            }
        }
    }

    Layout(
        modifier = modifier
            .clipToBounds()
            .nestedScroll(connection),
        content = {
            Box { player() }
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val h = playerHeightPx
                        val fraction = if (h > 0f) (-offsetPx / h).coerceIn(0f, 1f) else 0f
                        alpha = fraction * SCRIM_MAX_ALPHA
                    }
                    .background(Color.Black),
            )
            Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                content()
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight

        val playerPlaceable = measurables[0].measure(
            Constraints(minWidth = width, maxWidth = width, minHeight = 0, maxHeight = height),
        )

        val ph = playerPlaceable.height
        playerHeightPx = ph.toFloat()
        val offset = offsetPx.coerceIn(-ph.toFloat(), 0f).roundToInt()
        val visiblePlayer = (ph + offset).coerceAtLeast(0)
        val listHeight = (height - visiblePlayer).coerceAtLeast(0)

        val scrimPlaceable = measurables[1].measure(Constraints.fixed(width, ph))

        val listPlaceable = measurables[2].measure(
            Constraints(
                minWidth = width,
                maxWidth = width,
                minHeight = listHeight,
                maxHeight = listHeight,
            ),
        )

        layout(width, height) {
            // Parallax: the player slides slower than the list and is covered by it.
            val parallaxY = (offset * PARALLAX_FACTOR).roundToInt()
            playerPlaceable.place(0, parallaxY)
            scrimPlaceable.place(0, parallaxY)
            listPlaceable.place(0, visiblePlayer)
        }
    }
}
