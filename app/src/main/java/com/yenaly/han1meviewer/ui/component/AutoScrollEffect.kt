package com.yenaly.han1meviewer.ui.component

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

val AutoScrollInterval = 5.seconds

@Composable
fun AutoScrollEffect(
    pagerState: PagerState,
    pageCount: Int,
    interval: Duration = AutoScrollInterval,
) {
    if (LocalInspectionMode.current || pageCount <= 1) return

    val lifecycleOwner = LocalLifecycleOwner.current
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(pagerState, pageCount, isDragged, interval) {
        if (isDragged) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var settledPage = pagerState.currentPage
            while (true) {
                delay(interval)

                if (pagerState.isScrollInProgress) continue

                if (pagerState.currentPage != settledPage) {
                    settledPage = pagerState.currentPage
                    continue
                }
                pagerState.animateScrollToPage((settledPage + 1) % pageCount)
                settledPage = pagerState.currentPage
            }
        }
    }
}
