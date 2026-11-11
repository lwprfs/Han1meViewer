package com.yenaly.han1meviewer.XHamster.data.local
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface XHamsterHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(histories: List<XHamsterHistoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(history: XHamsterHistoryEntity)

    @Query("SELECT * FROM xhamster_watch_history ORDER BY watchDate DESC")
    fun loadAll(): Flow<List<XHamsterHistoryEntity>>

    @Query("SELECT * FROM xhamster_watch_history ORDER BY watchDate DESC")
    suspend fun getAllHistorySync(): List<XHamsterHistoryEntity>

    @Query("SELECT * FROM xhamster_watch_history ORDER BY watchDate DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<XHamsterHistoryEntity>

    @Query("SELECT COUNT(*) FROM xhamster_watch_history")
    suspend fun getTotalCount(): Int

    @Query("DELETE FROM xhamster_watch_history")
    suspend fun deleteAll()

    @Query("DELETE FROM xhamster_watch_history WHERE videoCode = :videoCode")
    suspend fun deleteByVideoCode(videoCode: String)

    @Query("SELECT * FROM xhamster_watch_history WHERE videoCode = :videoCode LIMIT 1")
    suspend fun getByVideoCode(videoCode: String): XHamsterHistoryEntity?
}
