package com.yenaly.han1meviewer.RouVideo.data.local
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RouVideoHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(histories: List<RouVideoHistoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(history: RouVideoHistoryEntity)

    @Query("SELECT * FROM rouvideo_watch_history ORDER BY watchDate DESC")
    fun loadAll(): Flow<List<RouVideoHistoryEntity>>

    @Query("SELECT * FROM rouvideo_watch_history ORDER BY watchDate DESC")
    suspend fun getAllHistorySync(): List<RouVideoHistoryEntity>

    @Query("SELECT * FROM rouvideo_watch_history ORDER BY watchDate DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<RouVideoHistoryEntity>

    @Query("SELECT COUNT(*) FROM rouvideo_watch_history")
    suspend fun getTotalCount(): Int

    @Query("DELETE FROM rouvideo_watch_history")
    suspend fun deleteAll()

    @Query("DELETE FROM rouvideo_watch_history WHERE videoCode = :videoCode")
    suspend fun deleteByVideoCode(videoCode: String)

    @Query("SELECT * FROM rouvideo_watch_history WHERE videoCode = :videoCode LIMIT 1")
    suspend fun getByVideoCode(videoCode: String): RouVideoHistoryEntity?
}
