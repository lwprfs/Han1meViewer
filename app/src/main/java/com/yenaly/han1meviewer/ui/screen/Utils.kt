package com.yenaly.han1meviewer.ui.screen

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.theme.SpacingLarge
import com.yenaly.han1meviewer.ui.theme.SpacingNormal
import com.yenaly.han1meviewer.ui.theme.VideoNormalCardMinWidth

@Composable
fun RetryableImage(
    model: Any,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    retryLimit: Int = 2,
    placeholder: Painter,
    error: Painter,
    contentScale: ContentScale? = ContentScale.Fit,
    imageLoader: ImageLoader? = null,
) {
    val context = LocalContext.current
    val resolvedLoader = imageLoader ?: SingletonImageLoader.get(context)

    var retryCount by remember(model) { mutableIntStateOf(0) }
    val request = remember(model, retryCount) {
        ImageRequest.Builder(context)
            .data(model)
            .crossfade(true)
            .memoryCacheKey("$model#r$retryCount")
            .diskCacheKey("$model#r$retryCount")
            .listener(
                onError = { _, result ->
                    Log.e("CoilError", "Image load failed for $model", result.throwable)
                }
            )
            .build()
    }

    AsyncImage(
        model = request,
        imageLoader = resolvedLoader,
        contentDescription = contentDescription,
        placeholder = placeholder,
        error = error,
        modifier = modifier,
        onError = {
            if (retryCount < retryLimit) {
                retryCount++
            }
        },
        contentScale = contentScale ?: ContentScale.Fit,
    )
}

@Composable
fun getColumnCount(itemWidth: Int): Int {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val screenWidthPx = windowInfo.containerSize.width
    val screenWidthDp = with(density) { screenWidthPx.toDp() }
    return maxOf(2, (screenWidthDp / itemWidth.dp).toInt())
}

@Composable
fun rememberCardResponsiveWidth(
    horizontalPadding: Dp = SpacingLarge,
    itemSpacing: Dp = SpacingNormal
): Pair<Dp, Float> {
    val containerWidth = LocalWindowInfo.current.containerSize.width
    val density = LocalDensity.current
    val currentWidthDp = with(density) { containerWidth.toDp() }

    val isPreview = LocalInspectionMode.current
    val itemsToShow = if (!isPreview) {
        Preferences.horizontalCardCountConfig.countForWidthDp(currentWidthDp.value.toInt())
    } else {
        val estimatedCardWidth = 160.dp
        maxOf(1f, ((currentWidthDp - (horizontalPadding * 2)) / (estimatedCardWidth + itemSpacing)))
    }

    val safeItemsToShow = maxOf(1f, itemsToShow)
    val cardWidth = (currentWidthDp - (horizontalPadding * 2) - (itemSpacing * (safeItemsToShow - 1))) / safeItemsToShow

    return Pair(cardWidth, safeItemsToShow)
}

@Composable
fun rememberVideoGridColumns(): Int {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val screenWidthPx = windowInfo.containerSize.width
    val screenWidthDp = with(density) { screenWidthPx.toDp() }

    val isPreview = LocalInspectionMode.current

    return if (!isPreview && Preferences.tabletMode) {
        Preferences.searchGridColumnsConfig.columnsForWidthDp(screenWidthDp.value.toInt())
    } else {
        maxOf(2, ((screenWidthDp + SpacingNormal) / (VideoNormalCardMinWidth + SpacingNormal)).toInt())
    }
}

@Composable
fun rememberRandomLoadingHint(): String {
    val placeholders = stringArrayResource(R.array.loading_hints)
    return remember(placeholders) { placeholders.random() }
}
