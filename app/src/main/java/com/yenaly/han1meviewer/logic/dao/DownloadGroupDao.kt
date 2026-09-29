package com.yenaly.han1meviewer.logic.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.yenaly.han1meviewer.logic.entity.download.DownloadGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadGroupDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultGroup(group: DownloadGroupEntity = DownloadGroupEntity(
        name = DownloadGroupEntity.DEFAULT_GROUP_NAME,
        orderIndex = 0,
        id = DownloadGroupEntity.DEFAULT_GROUP_ID
    )): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: DownloadGroupEntity): Long

    @Update
    suspend fun update(group: DownloadGroupEntity)

    @Delete
    suspend fun delete(group: DownloadGroupEntity)

    @Query("SELECT * FROM download_groups ORDER BY orderIndex ASC")
    fun getAllGroups(): Flow<List<DownloadGroupEntity>>

    @Query("SELECT * FROM download_groups ORDER BY orderIndex ASC")
    suspend fun getAllGroupsOnce(): List<DownloadGroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(groups: List<DownloadGroupEntity>)

    @Query("DELETE FROM download_groups")
    suspend fun deleteAll()

    @Query("SELECT * FROM download_groups WHERE id = :groupId")
    suspend fun getGroupById(groupId: Int): DownloadGroupEntity?

    @Query("SELECT * FROM download_groups WHERE name = :name LIMIT 1")
    suspend fun getGroupByName(name: String): DownloadGroupEntity?

    @Query("SELECT MAX(orderIndex) FROM download_groups")
    suspend fun getMaxOrderIndex(): Int?

    @Query("UPDATE HanimeDownloadEntity SET groupId = ${DownloadGroupEntity.DEFAULT_GROUP_ID} WHERE groupId = :oldGroupId")
    suspend fun resetVideosGroupToDefault(oldGroupId: Int)

    @Transaction
    suspend fun deleteGroup(group: DownloadGroupEntity) {
        resetVideosGroupToDefault(group.id)
        delete(group)
    }
}
