package com.yenaly.han1meviewer.FapFolder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FapFolderHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: FapFolderHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<FapFolderHistoryEntity>)

    @Query("SELECT * FROM fapfolder_watch_history ORDER BY watchDate DESC")
    fun loadAll(): Flow<List<FapFolderHistoryEntity>>

    @Query("SELECT * FROM fapfolder_watch_history ORDER BY watchDate DESC")
    suspend fun getAll(): List<FapFolderHistoryEntity>

    @Query("SELECT * FROM fapfolder_watch_history ORDER BY watchDate DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<FapFolderHistoryEntity>

    @Query("SELECT COUNT(*) FROM fapfolder_watch_history")
    suspend fun getTotalCount(): Int

    @Query("SELECT * FROM fapfolder_watch_history WHERE postId = :postId LIMIT 1")
    suspend fun getByPostId(postId: Long): FapFolderHistoryEntity?

    @Query("SELECT * FROM fapfolder_watch_history WHERE postId IN (:postIds)")
    suspend fun getByPostIds(postIds: List<Long>): List<FapFolderHistoryEntity>

    @Query("UPDATE fapfolder_watch_history SET lastPosition = :position, totalDuration = :duration, watchDate = :watchDate WHERE postId = :postId")
    suspend fun updatePosition(
        postId: Long,
        position: Long,
        duration: Long,
        watchDate: Long,
    )

    @Query("UPDATE fapfolder_watch_history SET completed = 1, lastPosition = totalDuration, watchDate = :watchDate WHERE postId = :postId")
    suspend fun markCompleted(postId: Long, watchDate: Long)

    @Query("DELETE FROM fapfolder_watch_history WHERE postId = :postId")
    suspend fun deleteByPostId(postId: Long)

    @Query("DELETE FROM fapfolder_watch_history")
    suspend fun deleteAll()
}
