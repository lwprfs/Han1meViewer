package com.yenaly.han1meviewer.HentaiMama.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaImageLoader
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.SiteVideoSectionHeader
import com.yenaly.han1meviewer.ui.screen.RetryableImage

private val PREVIEW_THUMB_WIDTH = 150.dp

@Composable
fun HentaiMamaEpisodeGallery(
    previewUrls: List<String>,
    columns: Int,
    modifier: Modifier = Modifier,
) {
    if (previewUrls.isEmpty()) return

    val context = LocalContext.current
    val imageLoader = remember(context) { HentaiMamaImageLoader.get(context) }

    var fullscreenIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {

        SiteVideoSectionHeader(title = "Previews")

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(items = previewUrls) { index, url ->
                Box(
                    modifier = Modifier
                        .width(PREVIEW_THUMB_WIDTH)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { fullscreenIndex = index },
                ) {
                    RetryableImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        placeholder = painterResource(R.drawable.h_chan_loading),
                        error = painterResource(R.drawable.h_chan_load_failed),
                        contentScale = ContentScale.Crop,
                        imageLoader = imageLoader,
                    )
                }
            }
        }
    }

    fullscreenIndex?.let { startIndex ->
        GalleryFullscreenDialog(
            urls = previewUrls,
            initialIndex = startIndex,
            onDismiss = { fullscreenIndex = null },
        )
    }
}

@Composable
private fun GalleryFullscreenDialog(
    urls: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { HentaiMamaImageLoader.get(context) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val pagerState = rememberPagerState(
            initialPage = initialIndex.coerceIn(0, urls.lastIndex),
            pageCount = { urls.size },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                        ) { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    RetryableImage(
                        model = urls[page],
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        placeholder = painterResource(R.drawable.h_chan_loading),
                        error = painterResource(R.drawable.h_chan_load_failed),
                        contentScale = ContentScale.Fit,
                        imageLoader = imageLoader,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                    )
                }
                Text(
                    text = "${pagerState.currentPage + 1} / ${urls.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text(
                text = "Tap to close",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Suppress("unused")
private fun unusedIconImportShim() {
    val icons = listOf(Icons.AutoMirrored.Filled.ArrowBack)
    PaddingValues(0.dp)
}
