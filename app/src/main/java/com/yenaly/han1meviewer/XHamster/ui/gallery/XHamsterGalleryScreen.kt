package com.yenaly.han1meviewer.XHamster.ui.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yenaly.han1meviewer.FapFolder.ui.components.rememberFapFolderImageGate
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.XHamster.common.XHamsterConstants
import com.yenaly.han1meviewer.XHamster.common.XHamsterFormat
import com.yenaly.han1meviewer.XHamster.common.XHamsterImageLoader
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterComment
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterGalleryInfo
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterPhoto
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterPhotoDetail
import com.yenaly.han1meviewer.XHamster.data.remote.XHamsterRepo
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteErrorContent
import com.yenaly.han1meviewer.ui.component.SiteLoadingContent
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar
import com.yenaly.han1meviewer.ui.screen.RetryableImage
import com.yenaly.han1meviewer.util.SiteImageSaver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val GALLERY_ID_REGEX = Regex("/photos/gallery/(\\d+)/")

private fun saveHeaders() = mapOf("Referer" to XHamsterConstants.BASE_URL + "/")

private fun photoGalleryId(info: XHamsterGalleryInfo?, photo: XHamsterPhoto?): Long =
    info?.id?.takeIf { it > 0L }
        ?: photo?.pageUrl?.let { GALLERY_ID_REGEX.find(it)?.groupValues?.get(1)?.toLongOrNull() }
        ?: 0L

private fun safeName(s: String): String =
    s.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(60).ifBlank { "gallery" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XHamsterGalleryScreen(
    url: String,
    title: String,
    onBack: () -> Unit,
    onOpenAuthor: (path: String, title: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageLoader = XHamsterImageLoader.get(context)
    val imageGate = rememberFapFolderImageGate()

    var info by remember(url) { mutableStateOf<XHamsterGalleryInfo?>(null) }
    var photos by remember(url) { mutableStateOf<List<XHamsterPhoto>>(emptyList()) }
    var nextUrl by remember(url) { mutableStateOf<String?>(null) }
    var loading by remember(url) { mutableStateOf(true) }
    var loadingMore by remember(url) { mutableStateOf(false) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var viewerIndex by remember(url) { mutableStateOf<Int?>(null) }
    var savingAll by remember { mutableStateOf(false) }

    LaunchedEffect(url, reload) {
        loading = true
        error = null
        XHamsterRepo.galleryPage(url)
            .onSuccess {
                info = it.info
                photos = it.photos
                nextUrl = it.nextUrl
            }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
        loading = false
    }

    fun loadMore() {
        val next = nextUrl ?: return
        if (loadingMore) return
        loadingMore = true
        scope.launch {
            XHamsterRepo.galleryPage(next)
                .onSuccess { page ->
                    val known = photos.map { it.id }.toHashSet()
                    photos = photos + page.photos.filter { it.id !in known }
                    nextUrl = page.nextUrl?.takeIf { it != next }
                }
                .onFailure { nextUrl = null }
            loadingMore = false
        }
    }

    val gridState = rememberLazyGridState()
    val nearEnd by remember(gridState) {
        derivedStateOf {
            val li = gridState.layoutInfo
            val last = li.visibleItemsInfo.lastOrNull()?.index ?: 0
            li.totalItemsCount > 0 && last >= li.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, photos.size, nextUrl) { if (nearEnd && nextUrl != null) loadMore() }

    val folderName = safeName(info?.title ?: title)
    val savedMsg = stringResource(R.string.fap_photo_saved)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SiteTopAppBar(
                title = { Text(info?.title ?: title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { SiteBackButton(onClick = onBack) },
                actions = {
                    IconButton(
                        enabled = photos.isNotEmpty() && !savingAll,
                        onClick = {
                            imageGate {
                                savingAll = true
                                SiteImageSaver.saveBatchInBackground(
                                    context = context,
                                    urls = photos.map { it.fullUrl },
                                    segments = listOf("xHamster", "Photos", folderName),
                                    baseName = "photo",
                                    client = XHamsterImageLoader.downloadClient(),
                                    headers = saveHeaders(),
                                ) { result ->
                                    savingAll = false
                                    when (result) {
                                        is SiteImageSaver.SaveResult.Error -> GlobalToasts.show(
                                            result.message, level = GlobalToasts.ToastLevel.ERROR,
                                        )
                                        is SiteImageSaver.SaveResult.Saved -> GlobalToasts.show(
                                            savedMsg + " (" + result.count + ")",
                                            level = GlobalToasts.ToastLevel.SUCCESS,
                                        )
                                    }
                                }
                            }
                        },
                    ) {
                        if (savingAll) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Download, stringResource(R.string.xhamster_download_all))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading && photos.isEmpty() -> SiteLoadingContent()
                photos.isEmpty() -> SiteErrorContent(message = error, onRetry = { reload++ })
                else -> LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        GalleryHeader(info, photos.size, onOpenAuthor)
                    }
                    itemsIndexed(photos, key = { _, p -> p.id }) { index, photo ->
                        RetryableImage(
                            model = photo.thumbUrl,
                            contentDescription = photo.alt,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { viewerIndex = index },
                            placeholder = painterResource(R.drawable.h_chan_loading),
                            error = painterResource(R.drawable.h_chan_load_failed),
                            contentScale = ContentScale.Crop,
                            imageLoader = imageLoader,
                        )
                    }
                    if (loadingMore) item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }

    viewerIndex?.let { start ->
        XHamsterPhotoViewer(
            info = info,
            photos = photos,
            initialIndex = start,
            hasMore = nextUrl != null,
            onLoadMore = { loadMore() },
            folderName = folderName,
            onDismiss = { viewerIndex = null },
            onOpenAuthor = onOpenAuthor,
        )
    }
}

@Composable
private fun GalleryHeader(
    info: XHamsterGalleryInfo?,
    loaded: Int,
    onOpenAuthor: (String, String) -> Unit,
) {
    info ?: return
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(info.title, style = MaterialTheme.typography.titleMedium)
        val stats = listOfNotNull(
            "${XHamsterFormat.count(info.photosTotal ?: loaded.toLong())}",
            info.views?.let { XHamsterFormat.count(it) }?.let { stringResource(R.string.xhamster_n_views, it) },
        ).joinToString(" · ")
        Text(stats, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val authorPath = info.authorPath
        if (!info.authorName.isNullOrBlank()) {
            Text(
                stringResource(R.string.xhamster_gallery_by, info.authorName),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = if (authorPath != null) {
                    Modifier.clickable { onOpenAuthor(authorPath, info.authorName) }
                } else Modifier,
            )
        }
        if (!info.description.isNullOrBlank()) {
            Text(info.description, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun XHamsterPhotoViewer(
    info: XHamsterGalleryInfo?,
    photos: List<XHamsterPhoto>,
    initialIndex: Int,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    folderName: String,
    onDismiss: () -> Unit,
    onOpenAuthor: (String, String) -> Unit,
) {
    if (photos.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageGate = rememberFapFolderImageGate()
    val imageLoader = XHamsterImageLoader.get(context)
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, photos.lastIndex),
        pageCount = { photos.size },
    )
    var zoomed by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    val savedMsg = stringResource(R.string.fap_photo_saved)
    val alreadyMsg = stringResource(R.string.fap_photo_already_saved)

    BackHandler(onBack = { if (showComments) showComments = false else onDismiss() })
    LaunchedEffect(pagerState.currentPage) { zoomed = false }
    LaunchedEffect(pagerState.currentPage, photos.size, hasMore) {
        if (hasMore && pagerState.currentPage >= photos.size - 3) onLoadMore()
    }
    LaunchedEffect(controls) {
        if (controls) {
            delay(3000)
            controls = false
        }
    }

    val current = photos.getOrNull(pagerState.currentPage)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = !zoomed,
            ) { page ->
                ZoomablePhoto(
                    photo = photos[page],
                    imageLoader = imageLoader,
                    isActive = pagerState.currentPage == page,
                    onZoomChange = { z -> if (pagerState.currentPage == page) zoomed = z },
                    onTap = { controls = !controls },
                )
            }

            AnimatedVisibility(visible = controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            val photo = current ?: return@IconButton
                            if (saving) return@IconButton
                            imageGate {
                                saving = true
                                scope.launch {
                                    val result = SiteImageSaver.saveOne(
                                        context = context,
                                        url = photo.fullUrl,
                                        segments = listOf("xHamster", "Photos", folderName),
                                        baseName = "photo_${photo.id}",
                                        client = XHamsterImageLoader.downloadClient(),
                                        headers = saveHeaders(),
                                    )
                                    saving = false
                                    when (result) {
                                        is SiteImageSaver.SaveResult.Error -> GlobalToasts.show(
                                            result.message, level = GlobalToasts.ToastLevel.ERROR,
                                        )
                                        is SiteImageSaver.SaveResult.Saved -> GlobalToasts.show(
                                            if (result.count > 0) savedMsg else alreadyMsg,
                                            level = GlobalToasts.ToastLevel.SUCCESS,
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                    ) {
                        if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        else Icon(Icons.Default.Download, stringResource(R.string.fap_save_photo), tint = Color.White)
                    }
                    Row(
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                            .padding(bottom = 24.dp)
                            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "${pagerState.currentPage + 1} / ${photos.size}" + if (hasMore) "+" else "",
                            color = Color.White, style = MaterialTheme.typography.labelMedium,
                        )
                        TextButton(onClick = { showComments = true }) {
                            Text(
                                stringResource(R.string.xhamster_comments_button) +
                                        (current?.commentsCount?.takeIf { it > 0 }?.let { " ($it)" } ?: ""),
                                color = Color.White,
                            )
                        }
                    }
                }
            }

            if (showComments && current != null) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(onDismissRequest = { showComments = false }, sheetState = sheetState) {
                    PhotoCommentsSheet(
                        galleryId = photoGalleryId(info, current),
                        photo = current,
                        imageLoader = imageLoader,
                        onOpenAuthor = { path, name ->
                            showComments = false
                            onDismiss()
                            onOpenAuthor(path, name)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomablePhoto(
    photo: XHamsterPhoto,
    imageLoader: coil3.ImageLoader,
    isActive: Boolean,
    onZoomChange: (Boolean) -> Unit,
    onTap: () -> Unit,
) {
    var scale by remember(photo.id) { mutableFloatStateOf(1f) }
    var offset by remember(photo.id) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(scale, isActive) { if (isActive) onZoomChange(scale > 1.01f) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        RetryableImage(
            model = photo.fullUrl,
            contentDescription = photo.alt,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(photo.id, scale) {
                    detectTapGestures(
                        onTap = { if (scale <= 1.01f) onTap() },
                        onDoubleTap = {
                            if (scale > 1.01f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else scale = 2f
                        },
                    )
                }
                .then(
                    if (scale > 1.01f) Modifier.pointerInput(photo.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            offset = if (scale <= 1f) Offset.Zero else offset + pan
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
            imageLoader = imageLoader,
        )
    }
}

@Composable
private fun PhotoCommentsSheet(
    galleryId: Long,
    photo: XHamsterPhoto,
    imageLoader: coil3.ImageLoader,
    onOpenAuthor: (String, String) -> Unit,
) {
    var detail by remember(photo.id) { mutableStateOf<XHamsterPhotoDetail?>(null) }
    var comments by remember(photo.id) { mutableStateOf<List<XHamsterComment>>(emptyList()) }
    var page by remember(photo.id) { mutableIntStateOf(1) }
    var maxPages by remember(photo.id) { mutableIntStateOf(1) }
    var loading by remember(photo.id) { mutableStateOf(true) }
    var loadingMore by remember(photo.id) { mutableStateOf(false) }
    var error by remember(photo.id) { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(photo.id, reload) {
        loading = true
        error = null
        XHamsterRepo.photoDetail(galleryId, photo.id)
            .onSuccess {
                detail = it
                comments = it.comments.items
                page = it.comments.page
                maxPages = it.comments.maxPages
            }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
        loading = false
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding()) {
        val d = detail
        val header = buildList {
            add(stringResource(R.string.xhamster_comments_button) + " (" + (d?.commentsTotal ?: photo.commentsCount.toLong()) + ")")
            d?.likes?.let { XHamsterFormat.count(it) }?.let { add(stringResource(R.string.xhamster_photo_likes, it)) }
            d?.views?.let { XHamsterFormat.count(it) }?.let { add(stringResource(R.string.xhamster_n_views, it)) }
        }.joinToString("  ·  ")
        Text(header, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry)) }
            }
            comments.isEmpty() -> Text(
                stringResource(
                    if (d?.comments?.writeDenied == true) R.string.xhamster_photo_comments_closed
                    else R.string.xhamster_no_comments
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(comments, key = { _, c -> c.id }) { _, c ->
                    CommentItem(c, imageLoader, onOpenAuthor, isReply = false)
                    c.replies.forEach { r ->
                        Box(Modifier.padding(start = 24.dp, top = 10.dp)) {
                            CommentItem(r, imageLoader, onOpenAuthor, isReply = true)
                        }
                    }
                }
                if (page < maxPages) item {
                    TextButton(
                        enabled = !loadingMore,
                        onClick = {
                            loadingMore = true
                            scope.launch {
                                XHamsterRepo.photoComments(galleryId, photo.id, page + 1)
                                    .onSuccess {
                                        val known = comments.map { c -> c.id }.toHashSet()
                                        comments = comments + it.items.filter { c -> c.id !in known }
                                        page = it.page
                                        maxPages = it.maxPages
                                    }
                                    .onFailure { maxPages = page }
                                loadingMore = false
                            }
                        },
                    ) { Text(if (loadingMore) "…" else stringResource(R.string.xhamster_comments_load_more)) }
                }
            }
        }
        Spacer(Modifier.size(16.dp))
    }
}

@Composable
private fun CommentItem(
    comment: XHamsterComment,
    imageLoader: coil3.ImageLoader,
    onOpenAuthor: (String, String) -> Unit,
    isReply: Boolean,
) {
    val size = if (isReply) 28.dp else 34.dp
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!comment.avatarUrl.isNullOrBlank()) {
            RetryableImage(
                model = comment.avatarUrl,
                modifier = Modifier.size(size).clip(CircleShape),
                placeholder = painterResource(R.drawable.h_chan_loading),
                error = painterResource(R.drawable.h_chan_load_failed),
                contentScale = ContentScale.Crop,
                imageLoader = imageLoader,
            )
        } else {
            Surface(Modifier.size(size), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Text(comment.author.take(1).uppercase(), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    comment.author,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = comment.authorPath?.let { p -> Modifier.clickable { onOpenAuthor(p, comment.author) } }
                        ?: Modifier,
                )
                comment.date?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!comment.replyToName.isNullOrBlank()) {
                Text("@" + comment.replyToName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
            comment.likes?.takeIf { it > 0 }?.let {
                Text("♥ $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
