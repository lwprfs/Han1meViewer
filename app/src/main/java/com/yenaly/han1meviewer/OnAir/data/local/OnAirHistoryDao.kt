package com.yenaly.han1meviewer.OnAir.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OnAirHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(history: OnAirHistoryEntity)

    @Query("SELECT * FROM onair_watch_history ORDER BY watchDate DESC")
    fun loadAll(): Flow<List<OnAirHistoryEntity>>

    @Query("SELECT * FROM onair_watch_history ORDER BY watchDate DESC")
    suspend fun getAll(): List<OnAirHistoryEntity>

    @Query("SELECT * FROM onair_watch_history ORDER BY watchDate DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<OnAirHistoryEntity>

    @Query("SELECT COUNT(*) FROM onair_watch_history")
    suspend fun getTotalCount(): Int

    @Query("SELECT * FROM onair_watch_history WHERE videoId = :videoId LIMIT 1")
    suspend fun getByVideoId(videoId: String): OnAirHistoryEntity?

    @Query("UPDATE onair_watch_history SET lastPosition = :position WHERE videoId = :videoId")
    suspend fun updatePosition(videoId: String, position: Long)

    @Query("DELETE FROM onair_watch_history WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)

    @Query("DELETE FROM onair_watch_history")
    suspend fun deleteAll()
}
