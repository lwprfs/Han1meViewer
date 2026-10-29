package com.yenaly.han1meviewer.FapFolder.ui.video

import android.util.Log
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderLightboxResponse
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderMedia
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetworkRepo
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

data class FapFolderLightboxChainStep(
    val photoId: Long,
    val imageUrl: String?,
    val post: com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPost?,
    val stepIndex: Int,
)

object FapFolderLightboxChain {

    private const val TAG = "FapFolderLightboxChain"
    private const val MAX_STEPS = 200

    fun walkForward(
        startPhotoId: Long,
        context: String,
    ): Flow<WebsiteState<FapFolderLightboxChainStep>> = flow {
        var currentId = startPhotoId
        var step = 0
        val seen = mutableSetOf<Long>()

        while (currentId > 0L && step < MAX_STEPS) {
            if (!seen.add(currentId)) break

            val first: WebsiteState<FapFolderLightboxResponse> =
                FapFolderNetworkRepo.openLightbox(currentId, context)
                    .firstOrNullState()
                    ?: break

            when (first) {
                is WebsiteState.Loading -> continue
                is WebsiteState.Error -> {
                    Log.w(TAG, "walkForward failed at $currentId: ${first.throwable.message}")
                    if (step == 0) emit(first)
                    break
                }
                is WebsiteState.Success -> {
                    val lb = first.info
                    val imageUrl = lb.currentImageUrl
                        ?: lb.post?.primaryImage?.fullUrl
                        ?: lb.post?.primaryVideo?.posterUrl

                    emit(
                        WebsiteState.Success(
                            FapFolderLightboxChainStep(
                                photoId = lb.currentPhotoId,
                                imageUrl = imageUrl,
                                post = lb.post,
                                stepIndex = step,
                            )
                        )
                    )
                    step++

                    val nextId = lb.nextPhotoId
                    if (nextId == null || nextId <= 0L || nextId == currentId) break
                    currentId = nextId
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    fun walkAll(
        startPhotoId: Long,
        context: String,
    ): Flow<WebsiteState<FapFolderLightboxChainStep>> = flow {
        var currentId = startPhotoId
        var step = 0
        val seen = mutableSetOf<Long>()

        while (currentId > 0L && step < MAX_STEPS) {
            if (!seen.add(currentId)) break

            val first: WebsiteState<FapFolderLightboxResponse> =
                FapFolderNetworkRepo.openLightbox(currentId, context)
                    .firstOrNullState()
                    ?: break

            when (first) {
                is WebsiteState.Loading -> continue
                is WebsiteState.Error -> {
                    if (step == 0) emit(first)
                    break
                }
                is WebsiteState.Success -> {
                    val lb = first.info
                    val imageUrl = lb.currentImageUrl
                        ?: lb.post?.primaryImage?.fullUrl
                        ?: lb.post?.primaryVideo?.posterUrl

                    if (step == 0) {
                        emit(
                            WebsiteState.Success(
                                FapFolderLightboxChainStep(
                                    photoId = lb.currentPhotoId,
                                    imageUrl = imageUrl,
                                    post = lb.post,
                                    stepIndex = step,
                                )
                            )
                        )
                        step++
                    }

                    val prevId = lb.prevPhotoId
                    if (prevId == null || prevId <= 0L || prevId == currentId) break
                    currentId = prevId
                }
            }
        }

        if (seen.isEmpty()) return@flow

        val firstPhoto = seen.minOrNull() ?: return@flow
        currentId = startPhotoId
        step = 0
        var emittedStart = false

        while (currentId > 0L && step < MAX_STEPS) {
            val first: WebsiteState<FapFolderLightboxResponse> =
                FapFolderNetworkRepo.openLightbox(currentId, context)
                    .firstOrNullState()
                    ?: break

            when (first) {
                is WebsiteState.Loading -> continue
                is WebsiteState.Error -> break
                is WebsiteState.Success -> {
                    val lb = first.info
                    if (!emittedStart) emittedStart = true else {
                        val imageUrl = lb.currentImageUrl
                            ?: lb.post?.primaryImage?.fullUrl
                            ?: lb.post?.primaryVideo?.posterUrl
                        emit(
                            WebsiteState.Success(
                                FapFolderLightboxChainStep(
                                    photoId = lb.currentPhotoId,
                                    imageUrl = imageUrl,
                                    post = lb.post,
                                    stepIndex = step,
                                )
                            )
                        )
                    }
                    step++
                    val nextId = lb.nextPhotoId
                    if (nextId == null || nextId <= 0L || nextId == currentId) break
                    currentId = nextId
                }
            }
        }

        if (firstPhoto <= 0L) Unit
    }.flowOn(Dispatchers.IO)

    fun toImageList(
        steps: List<FapFolderLightboxChainStep>,
    ): List<FapFolderMedia.Image> =
        steps.mapNotNull { step ->
            val url = step.imageUrl ?: return@mapNotNull null
            FapFolderMedia.Image(
                photoId = step.photoId,
                fullUrl = url,
                previewUrl = null,
                context = "photos",
                alt = step.post?.bodyPlain?.take(80),
            )
        }

    fun collect(
        startPhotoId: Long,
        context: String,
        limit: Int = 60,
    ): List<FapFolderMedia.Image> {
        val collected = mutableListOf<FapFolderMedia.Image>()
        var currentId = startPhotoId
        val seen = mutableSetOf<Long>()

        kotlinx.coroutines.runBlocking {
            while (currentId > 0L && collected.size < limit) {
                if (!seen.add(currentId)) break
                val result = kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                    walkForward(currentId, context).firstOrNullState()
                } ?: break

                when (result) {
                    is WebsiteState.Error -> break
                    is WebsiteState.Loading -> break
                    is WebsiteState.Success -> {
                        val step = result.info
                        val url = step.imageUrl
                        if (url != null) {
                            collected += FapFolderMedia.Image(
                                photoId = step.photoId,
                                fullUrl = url,
                                previewUrl = null,
                                context = "photos",
                                alt = step.post?.bodyPlain?.take(80),
                            )
                        }
                        val nextId = step.photoId
                        if (nextId == currentId) break
                        val resp = kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                            FapFolderNetworkRepo.openLightbox(currentId, context)
                                .firstOrNullState()
                        } as? WebsiteState.Success ?: break
                        val next = resp.info.nextPhotoId ?: break
                        currentId = next
                    }
                }
            }
        }

        return collected
    }
}

private suspend fun <T> Flow<T>.firstOrNullState(): T? {
    var value: T? = null
    try {
        collect { v ->
            value = v
            throw StopCollect()
        }
    } catch (_: StopCollect) {
    }
    return value
}

private class StopCollect : Throwable()
