package com.yenaly.han1meviewer.OnAir.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OnAirPageProgressBar(
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val target = if (loading) 1f else 0f
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(300),
        label = "onair_page_progress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp),
    ) {
        if (progress > 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
