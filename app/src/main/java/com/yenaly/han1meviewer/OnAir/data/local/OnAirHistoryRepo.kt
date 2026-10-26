package com.yenaly.han1meviewer.OnAir.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object OnAirHistoryRepo {

    private const val MAX_SANE_DELTA_MS = 10_000L
    private const val COMPLETE_GRACE_MS = 3_000L
    private const val MIN_RESUME_THRESHOLD_MS = 5_000L

    @Volatile private var isInitialized = false
    @Volatile private lateinit var appContext: Context

    private val writeMutex = Mutex()

    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            appContext = context.applicationContext
            isInitialized = true
        }
    }

    private val dao: OnAirHistoryDao by lazy {
        check(isInitialized) { "OnAirHistoryRepo not initialized" }
        OnAirDatabase.getInstance(appContext).onAirHistoryDao()
    }

    fun loadAll(): Flow<List<OnAirHistoryEntity>> = dao.loadAll()

    suspend fun getAll(): List<OnAirHistoryEntity> = withContext(Dispatchers.IO) {
        runCatching { dao.getAll() }.getOrDefault(emptyList())
    }

    suspend fun getPage(limit: Int, offset: Int): List<OnAirHistoryEntity> =
        withContext(Dispatchers.IO) { dao.getPage(limit, offset) }

    suspend fun getTotalCount(): Int = withContext(Dispatchers.IO) { dao.getTotalCount() }

    suspend fun getByVideoId(videoId: String): OnAirHistoryEntity? =
        withContext(Dispatchers.IO) {
            runCatching { dao.getByVideoId(videoId) }.getOrNull()
        }

    suspend fun deleteByVideoId(videoId: String) = withContext(Dispatchers.IO) {
        dao.deleteByVideoId(videoId)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) { dao.deleteAll() }

    suspend fun saveProgress(
        videoId: String,
        title: String,
        coverUrl: String,
        uploaderName: String?,
        position: Long,
        duration: Long,
        isPlaying: Boolean,
        quality: String? = null,
    ) = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext
        writeMutex.withLock {
            val existing = dao.getByVideoId(videoId)
            val now = System.currentTimeMillis()

            val entity = if (existing != null) {
                val rawDelta = position - existing.lastPosition
                val creditedDelta =
                    if (isPlaying && rawDelta in 0L..MAX_SANE_DELTA_MS) rawDelta else 0L

                val completedFlag =
                    duration > 0 && position >= duration - COMPLETE_GRACE_MS

                val watchCountBump =
                    existing.completed.not() && completedFlag

                existing.copy(
                    title = title.ifBlank { existing.title },
                    coverUrl = coverUrl.ifBlank { existing.coverUrl },
                    uploaderName = uploaderName ?: existing.uploaderName,
                    lastPosition = position,
                    totalDuration = if (duration > 0) duration else existing.totalDuration,
                    watchDate = now,
                    watchDuration = existing.watchDuration + creditedDelta,
                    watchCount = existing.watchCount + if (watchCountBump) 1 else 0,
                    completed = completedFlag,
                    lastQuality = quality ?: existing.lastQuality,
                )
            } else {
                OnAirHistoryEntity(
                    videoId = videoId,
                    title = title,
                    coverUrl = coverUrl,
                    uploaderName = uploaderName,
                    lastPosition = position,
                    totalDuration = duration,
                    watchDate = now,
                    watchDuration = 0L,
                    watchCount = 1,
                    completed = duration > 0 && position >= duration - COMPLETE_GRACE_MS,
                    lastQuality = quality,
                )
            }
            dao.insertOrUpdate(entity)
        }
    }

    suspend fun markStartedOver(videoId: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val existing = dao.getByVideoId(videoId) ?: return@withLock
            dao.insertOrUpdate(
                existing.copy(
                    lastPosition = 0L,
                    completed = false,
                    watchDate = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun shouldOfferResume(videoId: String): Boolean = withContext(Dispatchers.IO) {
        val existing = dao.getByVideoId(videoId) ?: return@withContext false
        !existing.completed && existing.lastPosition > MIN_RESUME_THRESHOLD_MS
    }
}
