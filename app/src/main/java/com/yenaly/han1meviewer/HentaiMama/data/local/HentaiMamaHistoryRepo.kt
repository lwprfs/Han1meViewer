package com.yenaly.han1meviewer.HentaiMama.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object HentaiMamaHistoryRepo {

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

    private val dao: HentaiMamaHistoryDao by lazy {
        check(isInitialized) { "HentaiMamaHistoryRepo not initialized" }
        HentaiMamaDatabase.getInstance(appContext).historyDao()
    }

    fun loadAll(): Flow<List<HentaiMamaHistoryEntity>> = dao.loadAll()

    suspend fun getAll(): List<HentaiMamaHistoryEntity> = withContext(Dispatchers.IO) {
        runCatching { dao.getAll() }.getOrDefault(emptyList())
    }

    suspend fun getPage(limit: Int, offset: Int): List<HentaiMamaHistoryEntity> =
        withContext(Dispatchers.IO) { dao.getPage(limit, offset) }

    suspend fun getTotalCount(): Int = withContext(Dispatchers.IO) { dao.getTotalCount() }

    suspend fun getByVideoCode(videoCode: String): HentaiMamaHistoryEntity? =
        withContext(Dispatchers.IO) { runCatching { dao.getByVideoCode(videoCode) }.getOrNull() }

    suspend fun deleteByVideoCode(videoCode: String) = withContext(Dispatchers.IO) {
        dao.deleteByVideoCode(videoCode)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) { dao.deleteAll() }

    suspend fun saveProgress(
        videoCode: String,
        title: String,
        coverUrl: String,
        episodeUrl: String,
        episodeNumber: Float,
        episodeTitle: String,
        position: Long,
        duration: Long,
        isPlaying: Boolean,
        completed: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val existing = dao.getByVideoCode(videoCode)
            val now = System.currentTimeMillis()

            val entity = if (existing != null) {
                val delta = (position - existing.lastPosition).coerceAtLeast(0L)
                existing.copy(
                    title = title.ifBlank { existing.title },
                    coverUrl = coverUrl.ifBlank { existing.coverUrl },
                    lastEpisodeUrl = episodeUrl,
                    lastEpisodeNumber = episodeNumber,
                    lastEpisodeTitle = episodeTitle,
                    lastPosition = position,
                    totalDuration = if (duration > 0) duration else existing.totalDuration,
                    watchDate = now,
                    watchDuration = existing.watchDuration + if (isPlaying) delta else 0L,
                    completed = completed,
                )
            } else {
                HentaiMamaHistoryEntity(
                    videoCode = videoCode,
                    title = title,
                    coverUrl = coverUrl,
                    lastEpisodeUrl = episodeUrl,
                    lastEpisodeNumber = episodeNumber,
                    lastEpisodeTitle = episodeTitle,
                    lastPosition = position,
                    totalDuration = duration,
                    watchDate = now,
                    watchDuration = 0L,
                    watchCount = 1,
                    completed = completed,
                )
            }
            dao.insertOrUpdate(entity)
        }
    }
}
