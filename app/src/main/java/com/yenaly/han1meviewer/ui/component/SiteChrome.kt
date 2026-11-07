package com.yenaly.han1meviewer.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.screen.RetryableImage

@Composable
fun SiteSearchTopBar(
    hint: String,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            leading()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onSearchClick() }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = hint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            actions()
        }
    }
}

@Composable
fun SiteBarIconButton(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun SiteSwitchButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(R.drawable.ic_baseline_switch_24),
            contentDescription = stringResource(R.string.switch_site),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun SiteOverflowMenu(items: List<Pair<String, () -> Unit>>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.more),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
        }
    }
}

@Composable
private fun SiteAccentBar() {
    Box(
        modifier = Modifier
            .width(4.dp)
            .height(18.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.primary),
    )
}

@Composable
fun SiteSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onMoreClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SiteAccentBar()
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onMoreClick != null) {
            Text(
                text = stringResource(R.string.more),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onMoreClick() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun SiteVideoSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteAccentBar()
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!actionText.isNullOrBlank() && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(actionText)
            }
        }
    }
}

@Composable
fun SiteVideoCard(
    title: String,
    coverUrl: Any,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    aspectRatio: Float = 16f / 9f,
    views: String? = null,
    duration: String? = null,
    subtitle: String? = null,
    reviews: String? = null,
    uploadTime: String? = null,
    topEndBadge: String? = null,
    bottomStartBadge: String? = null,
    isWatched: Boolean = false,
    imageLoader: ImageLoader? = null,
    contentScale: ContentScale = ContentScale.FillWidth,
    placeholder: androidx.compose.ui.graphics.painter.Painter? = null,
) {
    val iconSize = dimensionResource(id = R.dimen.view_view_and_time_icon_size)

    val resolvedContentScale =
        if (contentScale == ContentScale.FillWidth && aspectRatio != 16f / 9f) {
            ContentScale.Crop
        } else {
            contentScale
        }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio),
            ) {
                RetryableImage(
                    model = coverUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = placeholder ?: painterResource(R.drawable.h_chan_loading),
                    error = painterResource(R.drawable.h_chan_load_failed),
                    contentScale = resolvedContentScale,
                    imageLoader = imageLoader,
                )

                val topEndText = when {
                    isWatched -> stringResource(R.string.played)
                    else -> topEndBadge
                }
                if (!topEndText.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 6.dp, end = 6.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.70f),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = topEndText,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                if (!bottomStartBadge.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 6.dp, start = 6.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = bottomStartBadge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                if (!views.isNullOrBlank() || !duration.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.72f),
                                    ),
                                ),
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        if (!views.isNullOrBlank()) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_play_circle_outline_24),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(iconSize),
                            )
                            Text(
                                modifier = Modifier.padding(horizontal = 2.dp),
                                text = views,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (!duration.isNullOrBlank()) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_access_time_24),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(iconSize),
                            )
                            Text(
                                modifier = Modifier.padding(horizontal = 2.dp),
                                text = duration,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Text(
                text = title,
                maxLines = 2,
                minLines = 2,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 8.dp),
            )

            Text(
                text = subtitle.orEmpty(),
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
                    .fillMaxWidth(),
            ) {
                if (!reviews.isNullOrEmpty()) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_thumb_up_alt_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(iconSize),
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = reviews,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (!uploadTime.isNullOrEmpty()) {
                    Text(
                        text = uploadTime,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (reviews.isNullOrEmpty() && uploadTime.isNullOrEmpty()) {

                    Spacer(modifier = Modifier.height(iconSize))
                }
            }
        }
    }
}

@Composable
fun SiteBackButton(onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick) {
        Icon(
            painter = painterResource(R.drawable.ic_baseline_arrow_back_24),
            contentDescription = stringResource(R.string.back),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SiteLoadingContent(
    modifier: Modifier = Modifier,
    message: String = "Loading…",
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LoadingIndicator()
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SiteErrorContent(
    modifier: Modifier = Modifier,
    title: String? = null,
    message: String? = null,
    onRetry: (() -> Unit)? = null,
    retryText: String = stringResource(R.string.retry),
) {
    val resolvedTitle = title ?: stringResource(R.string.load_failed_retry)
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                modifier = Modifier.size(150.dp),
                painter = painterResource(R.drawable.h_chan_sad),
                contentDescription = resolvedTitle,
            )
            Text(
                text = resolvedTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onRetry != null) {
                FilledTonalButton(onClick = onRetry) {
                    Text(retryText)
                }
            }
        }
    }
}

@Composable
fun SiteEmptyContent(
    hint: String,
    subHint: String = "",
    picRes: Int = R.drawable.h_chan_speechless,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                modifier = Modifier
                    .padding(16.dp)
                    .width(150.dp),
                painter = painterResource(picRes),
                contentDescription = stringResource(R.string.here_is_empty),
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (subHint.isNotBlank()) {
                Text(
                    text = subHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun SiteSkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val transition = rememberInfiniteTransition(label = "siteSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "siteSkeletonAlpha",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
    )
}

@Composable
fun SiteIconBadge(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun SiteVideoRow(
    title: String,
    coverUrl: Any,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    progress: Float? = null,
    imageLoader: ImageLoader? = null,
    trailing: @Composable () -> Unit = {},
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(128.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp)),
            ) {
                RetryableImage(
                    model = coverUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = painterResource(R.drawable.h_chan_loading),
                    error = painterResource(R.drawable.h_chan_load_failed),
                    contentScale = ContentScale.Crop,
                    imageLoader = imageLoader,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(2.dp)),
                    )
                }
            }
            trailing()
        }
    }
}

@Composable
fun <T> SiteChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    key: ((T) -> Any)? = null,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = key) { item ->
            FilterChip(
                selected = item == selected,
                onClick = { onSelect(item) },
                label = { Text(label(item)) },
                shape = RoundedCornerShape(20.dp),
            )
        }
    }
}
