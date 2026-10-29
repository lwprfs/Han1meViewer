package com.yenaly.han1meviewer.FapFolder.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "fapfolder_watch_history")
data class FapFolderHistoryEntity(
    @PrimaryKey
    val postId: Long,

    val title: String,
    val coverUrl: String,
    val authorName: String?,
    val postSlug: String?,
    val isVideo: Boolean,

    val lastPosition: Long,
    val totalDuration: Long,
    val watchDate: Long,
    val watchDuration: Long = 0L,
    val watchCount: Int = 1,
    val completed: Boolean = false,
) {
    val progressPercent: Float
        get() = if (totalDuration > 0) {
            (lastPosition.toFloat() / totalDuration).coerceIn(0f, 1f)
        } else 0f
}
