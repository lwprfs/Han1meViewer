package com.yenaly.han1meviewer.FapFolder.data.local

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object FapFolderHistoryRepo {

    private const val TAG = "FapFolderHistoryRepo"

    private const val MAX_SANE_DELTA_MS = 10_000L
    private const val COMPLETE_GRACE_MS = 3_000L
    private const val MIN_RESUME_THRESHOLD_MS = 5_000L
    private const val MIN_WATCH_DELTA_MS = 1_000L
    private const val MIN_WATCH_DATE_BUMP_MS = 5_000L

    @Volatile
    private var isInitialized = false

    @Volatile
    private lateinit var appContext: Context

    private val writeMutex = Mutex()

    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            appContext = context.applicationContext
            isInitialized = true
        }
    }

    private val dao: FapFolderHistoryDao by lazy {
        check(isInitialized) { "FapFolderHistoryRepo not initialized" }
        FapFolderDatabase.getInstance(appContext).fapFolderHistoryDao()
    }

    fun loadAll(): Flow<List<FapFolderHistoryEntity>> = dao.loadAll()

    suspend fun getAll(): List<FapFolderHistoryEntity> = withContext(Dispatchers.IO) {
        runCatching { dao.getAll() }.getOrDefault(emptyList())
    }

    suspend fun getPage(limit: Int, offset: Int): List<FapFolderHistoryEntity> =
        withContext(Dispatchers.IO) {
            if (limit <= 0) emptyList() else dao.getPage(limit, offset)
        }

    suspend fun getRecent(limit: Int): List<FapFolderHistoryEntity> =
        withContext(Dispatchers.IO) {
            if (limit <= 0) emptyList() else dao.getPage(limit, 0)
        }

    suspend fun getTotalCount(): Int = withContext(Dispatchers.IO) { dao.getTotalCount() }

    suspend fun getByPostId(postId: Long): FapFolderHistoryEntity? =
        withContext(Dispatchers.IO) {
            runCatching { dao.getByPostId(postId) }.getOrNull()
        }

    suspend fun getWatchedPostIds(postIds: List<Long>): Set<Long> =
        withContext(Dispatchers.IO) {
            if (postIds.isEmpty()) return@withContext emptySet()
            runCatching {
                dao.getByPostIds(postIds)
                    .asSequence()
                    .filter { it.completed || it.watchDuration > 0L }
                    .map { it.postId }
                    .toSet()
            }.getOrDefault(emptySet())
        }

    suspend fun deleteByPostId(postId: Long) = withContext(Dispatchers.IO) {
        dao.deleteByPostId(postId)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) { dao.deleteAll() }

    suspend fun markStartedOver(postId: Long) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val existing = dao.getByPostId(postId) ?: return@withLock
            dao.insertOrUpdate(
                existing.copy(
                    lastPosition = 0L,
                    completed = false,
                    watchDate = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun shouldOfferResume(postId: Long): Boolean = withContext(Dispatchers.IO) {
        val existing = dao.getByPostId(postId) ?: return@withContext false
        !existing.completed && existing.lastPosition > MIN_RESUME_THRESHOLD_MS
    }

    /**
     * Unified progress save. All fields are optional so this single method serves
     * both "new post detail opened" (metadata + position 0) and "player ticked"
     * (position updates only) call sites.
     *
     * Metadata fields (`title`, `coverUrl`, `authorName`, `postSlug`, `isVideo`)
     * are only applied when non-blank / non-null, so subsequent position-only
     * saves do not clobber the metadata stored on first insert.
     */
    suspend fun saveProgress(
        postId: Long,
        title: String = "",
        coverUrl: String = "",
        authorName: String? = null,
        postSlug: String? = null,
        isVideo: Boolean = false,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
        isPlaying: Boolean = false,
        completed: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        if (postId <= 0L) return@withContext
        writeMutex.withLock {
            val existing = dao.getByPostId(postId)
            val now = System.currentTimeMillis()

            val entity = if (existing != null) {
                val clampedPosition = when {
                    positionMs <= 0L -> existing.lastPosition
                    durationMs > 0L && positionMs > durationMs -> durationMs
                    else -> positionMs
                }
                val clampedDuration = if (durationMs > 0L) durationMs else existing.totalDuration

                val rawDelta = clampedPosition - existing.lastPosition
                val creditedDelta =
                    if (isPlaying && rawDelta in 0L..MAX_SANE_DELTA_MS) rawDelta else 0L

                val completedFlag = completed ||
                        (clampedDuration > 0L &&
                                clampedPosition >= clampedDuration - COMPLETE_GRACE_MS)

                val watchCountBump = existing.completed.not() && completedFlag
                val shouldBumpDate = now - existing.watchDate >= MIN_WATCH_DATE_BUMP_MS ||
                        creditedDelta >= MIN_WATCH_DELTA_MS ||
                        completedFlag

                existing.copy(
                    title = title.ifBlank { existing.title },
                    coverUrl = coverUrl.ifBlank { existing.coverUrl },
                    authorName = authorName ?: existing.authorName,
                    postSlug = postSlug ?: existing.postSlug,
                    isVideo = isVideo || existing.isVideo,
                    lastPosition = clampedPosition,
                    totalDuration = clampedDuration,
                    watchDate = if (shouldBumpDate) now else existing.watchDate,
                    watchDuration = existing.watchDuration + creditedDelta,
                    watchCount = existing.watchCount + if (watchCountBump) 1 else 0,
                    completed = completedFlag,
                )
            } else {
                val completedFlag = completed ||
                        (durationMs > 0L && positionMs >= durationMs - COMPLETE_GRACE_MS)
                FapFolderHistoryEntity(
                    postId = postId,
                    title = title,
                    coverUrl = coverUrl,
                    authorName = authorName,
                    postSlug = postSlug,
                    isVideo = isVideo,
                    lastPosition = positionMs,
                    totalDuration = durationMs,
                    watchDate = now,
                    watchDuration = 0L,
                    watchCount = 1,
                    completed = completedFlag,
                )
            }
            dao.insertOrUpdate(entity)
        }
    }

    /**
     * Backwards-compatible alias for [saveProgress] with only playback fields.
     * Existing call sites in the player can keep using this name.
     */
    suspend fun savePlaybackProgress(
        postId: Long,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        completed: Boolean = false,
    ) = saveProgress(
        postId = postId,
        positionMs = positionMs,
        durationMs = durationMs,
        isPlaying = isPlaying,
        completed = completed,
    )
}