package com.yenaly.han1meviewer.MissAV

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

object MissAvHistoryRepo {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val dao: MissAvHistoryDao by lazy {
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