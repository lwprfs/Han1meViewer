package com.yenaly.han1meviewer.FapFolder.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderMedia
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.screen.RetryableImage

@Composable
fun FapFolderImageLightboxDialog(
    images: List<FapFolderMedia.Image>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    if (images.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, images.lastIndex),
        pageCount = { images.size },
    )
    var isCurrentZoomed by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

    BackHandler(onBack = onDismiss)

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            kotlinx.coroutines.delay(2500)
            controlsVisible = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = !isCurrentZoomed,
                verticalAlignment = Alignment.CenterVertically,
            ) { page ->
                ZoomableImage(
                    image = images[page],
                    isActivePage = pagerState.currentPage == page,
                    onZoomStateChange = { zoomed ->
                        if (pagerState.currentPage == page) isCurrentZoomed = zoomed
                    },
                    onTap = { controlsVisible = !controlsVisible },
                    onDismiss = onDismiss,
                )
            }

            LaunchedEffect(pagerState.currentPage) {
                isCurrentZoomed = false
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White,
                        )
                    }

                    if (images.size > 1) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 24.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.72f),
                                    RoundedCornerShape(999.dp),
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            repeat(images.size.coerceAtMost(12)) { index ->
                                val selected = pagerState.currentPage == index
                                Box(
                                    modifier = Modifier
                                        .size(if (selected) 8.dp else 6.dp)
                                        .background(
                                            color = if (selected) Color.White
                                            else Color.White.copy(alpha = 0.45f),
                                            shape = CircleShape,
                                        ),
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${pagerState.currentPage + 1} / ${images.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    image: FapFolderMedia.Image,
    isActivePage: Boolean,
    onZoomStateChange: (Boolean) -> Unit,
    onTap: () -> Unit,
    onDismiss: () -> Unit,
) {
    var scale by remember(image.fullUrl) { mutableFloatStateOf(1f) }
    var offset by remember(image.fullUrl) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(scale, isActivePage) {
        if (isActivePage) onZoomStateChange(scale > 1.01f)
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        RetryableImage(
            model = image.fullUrl,
            contentDescription = image.alt,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(image.fullUrl, scale) {
                    detectTapGestures(
                        onTap = {
                            if (scale <= 1.01f) onTap()
                        },
                        onDoubleTap = {
                            if (scale > 1.01f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2f
                            }
                        },
                    )
                }
                .then(
                    if (scale > 1.01f) {
                        Modifier.pointerInput(image.fullUrl) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                offset = if (scale <= 1f) Offset.Zero else offset + pan
                            }
                        }
                    } else Modifier
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            placeholder = painterResource(R.drawable.h_chan_loading),
            error = painterResource(R.drawable.h_chan_load_failed),
            contentScale = ContentScale.Fit,
        )
    }
}
