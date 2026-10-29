package com.yenaly.han1meviewer.FapFolder.ui.video

import android.util.Log
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderLightboxResponse
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderMedia
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderPost
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderNetworkRepo
import com.yenaly.han1meviewer.logic.state.WebsiteState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull

data class FapFolderLightboxChainStep(
    val photoId: Long,
    val imageUrl: String?,
    val post: FapFolderPost?,
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

            val result = FapFolderNetworkRepo.openLightbox(currentId, context)
                .firstTerminalState() ?: break

            when (result) {
                is WebsiteState.Loading -> continue
                is WebsiteState.Error -> {
                    Log.w(TAG, "walkForward failed at $currentId: ${result.throwable.message}")
                    if (step == 0) emit(result)
                    break
                }
                is WebsiteState.Success -> {
                    val lb = result.info
                    emit(
                        WebsiteState.Success(
                            FapFolderLightboxChainStep(
                                photoId = lb.currentPhotoId,
                                imageUrl = imageUrlOf(lb),
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

        val backwards = mutableListOf<FapFolderLightboxResponse>()
        var cursor = startPhotoId
        val backSeen = mutableSetOf<Long>()
        var guard = 0
        while (cursor > 0L && guard < MAX_STEPS) {
            if (!backSeen.add(cursor)) break
            val result = FapFolderNetworkRepo.openLightbox(cursor, context)
                .firstTerminalState() ?: break
            if (result !is WebsiteState.Success) break
            backwards.add(result.info)
            val prev = result.info.prevPhotoId
            if (prev == null || prev <= 0L || prev == cursor) break
            cursor = prev
            guard++
        }

        val headId = backwards.lastOrNull()?.currentPhotoId ?: startPhotoId
        val emittedIds = mutableSetOf<Long>()
        var forwardCursor = headId
        var step = 0
        val forwardSeen = mutableSetOf<Long>()

        while (forwardCursor > 0L && step < MAX_STEPS) {
            if (!forwardSeen.add(forwardCursor)) break
            val result = FapFolderNetworkRepo.openLightbox(forwardCursor, context)
                .firstTerminalState() ?: break
            when (result) {
                is WebsiteState.Loading -> continue
                is WebsiteState.Error -> {
                    if (step == 0) emit(result)
                    break
                }
                is WebsiteState.Success -> {
                    val lb = result.info
                    if (emittedIds.add(lb.currentPhotoId)) {
                        emit(
                            WebsiteState.Success(
                                FapFolderLightboxChainStep(
                                    photoId = lb.currentPhotoId,
                                    imageUrl = imageUrlOf(lb),
                                    post = lb.post,
                                    stepIndex = step,
                                )
                            )
                        )
                    }
                    step++
                    val nextId = lb.nextPhotoId
                    if (nextId == null || nextId <= 0L || nextId == forwardCursor) break
                    forwardCursor = nextId
                }
            }
        }
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

    suspend fun collect(
        startPhotoId: Long,
        context: String,
        limit: Int = 60,
    ): List<FapFolderMedia.Image> {
        if (startPhotoId <= 0L || limit <= 0) return emptyList()
        val collected = mutableListOf<FapFolderMedia.Image>()
        var currentId = startPhotoId
        val seen = mutableSetOf<Long>()

        while (currentId > 0L && collected.size < limit) {
            if (!seen.add(currentId)) break
            val result = withTimeoutOrNull(15_000L) {
                FapFolderNetworkRepo.openLightbox(currentId, context).firstTerminalState()
            } ?: break

            when (result) {
                is WebsiteState.Loading -> break
                is WebsiteState.Error -> break
                is WebsiteState.Success -> {
                    val lb = result.info
                    val url = imageUrlOf(lb)
                    if (url != null) {
                        collected += FapFolderMedia.Image(
                            photoId = lb.currentPhotoId,
                            fullUrl = url,
                            previewUrl = null,
                            context = "photos",
                            alt = lb.post?.bodyPlain?.take(80),
                        )
                    }
                    val nextId = lb.nextPhotoId
                    if (nextId == null || nextId <= 0L || nextId == currentId) break
                    currentId = nextId
                }
            }
        }

        return collected
    }

    private fun imageUrlOf(lb: FapFolderLightboxResponse): String? =
        lb.currentImageUrl
            ?: lb.post?.primaryImage?.fullUrl
            ?: lb.post?.primaryVideo?.posterUrl
}

private suspend fun <T> Flow<T>.firstTerminalState(): T? {
    var value: T? = null
    try {
        collect { v ->
            value = v
            if (v !is WebsiteState.Loading) throw StopCollect()
        }
    } catch (_: StopCollect) {

    }
    return value
}

private class StopCollect : Throwable()
