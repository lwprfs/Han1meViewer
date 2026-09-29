package com.yenaly.han1meviewer.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.ui.preview.ComponentPreview

private val IndicatorSelectedWidth = 18.dp

private val IndicatorIdleWidth = 6.dp

private val IndicatorHeight = 6.dp

private val IndicatorSpacing = 6.dp

@Composable
fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    unselectedColor: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    if (pageCount <= 1) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(pageCount) { index ->
            val isSelected = currentPage == index
            val width by animateDpAsState(
                targetValue = if (isSelected) IndicatorSelectedWidth else IndicatorIdleWidth,
                label = "pageIndicatorWidth"
            )
            val color by animateColorAsState(
                targetValue = if (isSelected) selectedColor else unselectedColor,
                label = "pageIndicatorColor"
            )
            Box(
                modifier = Modifier
                    .width(width)
                    .height(IndicatorHeight)
                    .clip(CircleShape)
                    .background(color)
            )
            if (index < pageCount - 1) Spacer(Modifier.width(IndicatorSpacing))
        }
    }
}

@Preview(showBackground = true, name = "页码指示器")
@Composable
private fun PageIndicatorPreview() {
    ComponentPreview {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PageIndicator(pageCount = 5, currentPage = 0)
            PageIndicator(pageCount = 5, currentPage = 2)
            PageIndicator(pageCount = 1, currentPage = 0)
        }
    }
}
