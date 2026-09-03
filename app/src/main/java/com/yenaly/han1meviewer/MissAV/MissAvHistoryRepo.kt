package com.yenaly.han1meviewer.MissAV

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

object MissAvHistoryRepo {

    @Volatile
    private var isInitialized = false

    @Volatile
    private lateinit var appContext: Context

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
        if (!isInitialized) {
            throw IllegalStateException("MissAvHistoryRepo not initialized")
        }
        MissAvDatabase.getInstance(appContext).missAvHistoryDao()
    }

    suspend fun insertOrUpdate(history: MissAvHistoryEntity) {
        withContext(Dispatchers.IO) {
            dao.insertOrUpdate(history)
        }
    }

    suspend fun getAllHistory(): List<MissAvHistoryEntity> = withContext(Dispatchers.IO) {
        try {
            dao.getAllHistorySync()
        } catch (e: Exception) {
            android.util.Log.e("MissAvHistoryRepo", "Error getting history", e)
            emptyList()
        }
    }

    fun loadAll(): Flow<List<MissAvHistoryEntity>> = dao.loadAll()

    suspend fun deleteAll() {
        withContext(Dispatchers.IO) {
            dao.deleteAll()
        }
    }

    suspend fun deleteByVideoCode(videoCode: String) {
        withContext(Dispatchers.IO) {
            dao.deleteByVideoCode(videoCode)
        }
    }

    suspend fun getByVideoCode(videoCode: String): MissAvHistoryEntity? = withContext(Dispatchers.IO) {
        try {
            dao.getByVideoCode(videoCode)
        } catch (e: Exception) {
            null
        }
    }
}