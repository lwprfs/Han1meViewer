package com.yenaly.han1meviewer.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val PARALLAX_FACTOR = 0.5f
private const val SCRIM_MAX_ALPHA = 0.65f
private const val SNAP_VELOCITY = 400f

private class CollapseState(private val scope: CoroutineScope) {
    var heightPx by mutableFloatStateOf(0f)
    var offsetPx by mutableFloatStateOf(0f)
    private var job: Job? = null

    fun cancelAnimation() {
        job?.cancel()
    }

    fun consume(dy: Float): Float {
        if (heightPx <= 0f) return 0f
        val old = offsetPx
        offsetPx = (old + dy).coerceIn(-heightPx, 0f)
        return offsetPx - old
    }

    fun animateTo(target: Float, velocity: Float = 0f) {
        job?.cancel()
        job = scope.launch {
            animate(
                initialValue = offsetPx,
                targetValue = target,
                initialVelocity = velocity,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) { value, _ ->
                offsetPx = value.coerceIn(-heightPx, 0f)
            }
        }
    }

    fun settle(vy: Float): Boolean {
        val h = heightPx
        if (h <= 0f) return false
        val off = offsetPx
        if (vy < -SNAP_VELOCITY && off > -h) {
            animateTo(-h, vy)
            return true
        }
        if (off < 0f && off > -h) {
            val target = when {
                vy > SNAP_VELOCITY -> 0f
                off < -h / 2f -> -h
                else -> 0f
            }
            animateTo(target, vy)
            return true
        }
        return false
    }
}

@Composable
fun CollapsingPlayerLayout(
    modifier: Modifier = Modifier,
    player: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state = remember { CollapseState(scope) }

    val connection = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy != 0f) state.cancelAnimation()

                if (dy < 0f) return Offset(0f, state.consume(dy))
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {

                if (available.y > 0f) return Offset(0f, state.consume(available.y))
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                return if (state.settle(available.y)) Velocity(0f, available.y) else Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y > 0f && state.offsetPx < 0f) {
                    state.animateTo(0f, available.y)
                    return Velocity(0f, available.y)
                }
                return Velocity.Zero
            }
        }
    }

    val dragState = rememberDraggableState { delta ->
        state.cancelAnimation()
        state.consume(delta)
    }

    Layout(
        modifier = modifier
            .clipToBounds()
            .nestedScroll(connection),
        content = {
            Box(
                modifier = Modifier.draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity -> state.settle(velocity) },
                ),
            ) { player() }
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val h = state.heightPx
                        val fraction = if (h > 0f) (-state.offsetPx / h).coerceIn(0f, 1f) else 0f
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
        state.heightPx = ph.toFloat()
        val offset = state.offsetPx.coerceIn(-ph.toFloat(), 0f).roundToInt()
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

            val parallaxY = (offset * PARALLAX_FACTOR).roundToInt()
            playerPlaceable.place(0, parallaxY)
            scrimPlaceable.place(0, parallaxY)
            listPlaceable.place(0, visiblePlayer)
        }
    }
}
