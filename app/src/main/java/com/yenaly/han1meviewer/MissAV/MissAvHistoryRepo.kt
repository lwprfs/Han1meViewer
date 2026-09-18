package com.yenaly.han1meviewer.MissAV

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object MissAvHistoryRepo {

    @Volatile private var isInitialized = false
    @Volatile private lateinit var appContext: Context

    private val writeMutex = Mutex()

    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            appContext = context.applicationContext
            isInitialized = true
            android.util.Log.d("MissAvHistoryRepo", "Initialized")
        }
    }

    private val dao: MissAvHistoryDao by lazy {
        check(isInitialized) { "MissAvHistoryRepo not initialized" }
        MissAvDatabase.getInstance(appContext).missAvHistoryDao()
    }

    suspend fun insertOrUpdate(history: MissAvHistoryEntity) {
        withContext(Dispatchers.IO) { dao.insertOrUpdate(history) }
    }

    suspend fun getAllHistory(): List<MissAvHistoryEntity> = withContext(Dispatchers.IO) {
        runCatching { dao.getAllHistorySync() }
            .onFailure { android.util.Log.e("MissAvHistoryRepo", "Error getting history", it) }
            .getOrDefault(emptyList())
    }

    fun loadAll(): Flow<List<MissAvHistoryEntity>> = dao.loadAll()

    suspend fun deleteAll() {
        withContext(Dispatchers.IO) { dao.deleteAll() }
    }

    suspend fun deleteByVideoCode(videoCode: String) {
        withContext(Dispatchers.IO) { dao.deleteByVideoCode(videoCode) }
    }

    suspend fun getByVideoCode(videoCode: String): MissAvHistoryEntity? =
        withContext(Dispatchers.IO) {
            runCatching { dao.getByVideoCode(videoCode) }.getOrNull()
        }

    suspend fun upsertWatchProgress(
        videoCode: String,
        title: String,
        coverUrl: String,
        currentPosition: Long,
        totalDuration: Long,
        isPlaying: Boolean,
        wasPlayed: Boolean,
    ) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val existing = dao.getByVideoCode(videoCode)
            val now = System.currentTimeMillis()

            val history = if (existing != null) {
                val newWatchCount =
                    if (wasPlayed) existing.watchCount + 1 else existing.watchCount
                val newPlayCount =
                    if (wasPlayed && isPlaying) existing.playCount + 1 else existing.playCount
                val newWatchDuration = if (isPlaying) {
                    existing.watchDuration +
                            (currentPosition - existing.lastPosition).coerceAtLeast(0)
                } else existing.watchDuration

                existing.copy(
                    watchDate = now,
                    watchDuration = newWatchDuration,
                    lastPosition = currentPosition,
                    totalDuration = totalDuration,
                    watchCount = newWatchCount,
                    playCount = newPlayCount,
                    isPlayed = wasPlayed || existing.isPlayed,
                    lastPlayedDate = if (wasPlayed && isPlaying) now else existing.lastPlayedDate,
                )
            } else {
                MissAvHistoryEntity(
                    videoCode = videoCode,
                    title = title,
                    coverUrl = coverUrl,
                    watchDate = now,
                    watchDuration = 0,
                    lastPosition = currentPosition,
                    totalDuration = totalDuration,
                    watchCount = if (wasPlayed) 1 else 0,
                    playCount = if (wasPlayed && isPlaying) 1 else 0,
                    isPlayed = wasPlayed,
                    lastPlayedDate = if (wasPlayed && isPlaying) now else null,
                )
            }
            dao.insertOrUpdate(history)
        }
    }
}
