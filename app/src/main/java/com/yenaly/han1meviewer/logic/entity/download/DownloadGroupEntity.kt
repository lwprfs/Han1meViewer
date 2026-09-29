package com.yenaly.han1meviewer.logic.entity.download

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "download_groups")
data class DownloadGroupEntity(
    val name: String,

    val orderIndex: Int,

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0
) {
    companion object {
        const val DEFAULT_GROUP_ID = 1
        const val DEFAULT_GROUP_NAME = "Default Group"
    }
}
