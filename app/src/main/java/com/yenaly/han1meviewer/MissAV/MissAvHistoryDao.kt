package com.yenaly.han1meviewer.MissAV

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MissAvHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(history: MissAvHistoryEntity)

    @Query("SELECT * FROM missav_watch_history ORDER BY watchDate DESC")
    fun loadAll(): Flow<List<MissAvHistoryEntity>>

    @Query("SELECT * FROM missav_watch_history ORDER BY watchDate DESC")
    suspend fun getAllHistorySync(): List<MissAvHistoryEntity>

    // New: Proper pagination
    @Query("SELECT * FROM missav_watch_history ORDER BY watchDate DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<MissAvHistoryEntity>

    @Query("SELECT COUNT(*) FROM missav_watch_history")
    suspend fun getTotalCount(): Int

    @Query("DELETE FROM missav_watch_history")
    suspend fun deleteAll()

    @Query("DELETE FROM missav_watch_history WHERE videoCode = :videoCode")
    suspend fun deleteByVideoCode(videoCode: String)

    @Query("SELECT * FROM missav_watch_history WHERE videoCode = :videoCode LIMIT 1")
    suspend fun getByVideoCode(videoCode: String): MissAvHistoryEntity?
}