package com.yenaly.han1meviewer.HentaiMama.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HentaiMamaHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(history: HentaiMamaHistoryEntity)

    @Query("SELECT * FROM hentaimama_watch_history ORDER BY watchDate DESC")
    fun loadAll(): Flow<List<HentaiMamaHistoryEntity>>

    @Query("SELECT * FROM hentaimama_watch_history ORDER BY watchDate DESC")
    suspend fun getAll(): List<HentaiMamaHistoryEntity>

    @Query("SELECT * FROM hentaimama_watch_history ORDER BY watchDate DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<HentaiMamaHistoryEntity>

    @Query("SELECT COUNT(*) FROM hentaimama_watch_history")
    suspend fun getTotalCount(): Int

    @Query("SELECT * FROM hentaimama_watch_history WHERE videoCode = :videoCode LIMIT 1")
    suspend fun getByVideoCode(videoCode: String): HentaiMamaHistoryEntity?

    @Query("DELETE FROM hentaimama_watch_history WHERE videoCode = :videoCode")
    suspend fun deleteByVideoCode(videoCode: String)

    @Query("DELETE FROM hentaimama_watch_history")
    suspend fun deleteAll()
}
