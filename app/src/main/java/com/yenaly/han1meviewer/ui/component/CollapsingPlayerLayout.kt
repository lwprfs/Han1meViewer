package com.yenaly.han1meviewer.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

@Composable
fun CollapsingPlayerLayout(
    modifier: Modifier = Modifier,
    player: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    var playerHeightPx by remember { mutableFloatStateOf(0f) }
    var offsetPx by remember { mutableFloatStateOf(0f) }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
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
        }
    }

    Layout(
        modifier = modifier
            .clipToBounds()
            .nestedScroll(connection),
        content = {

            Box { player() }
            Box { content() }
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

        val listPlaceable = measurables[1].measure(
            Constraints(
                minWidth = width,
                maxWidth = width,
                minHeight = listHeight,
                maxHeight = listHeight,
            ),
        )

        layout(width, height) {
            playerPlaceable.place(0, offset)
            listPlaceable.place(0, visiblePlayer)
        }
    }
}
