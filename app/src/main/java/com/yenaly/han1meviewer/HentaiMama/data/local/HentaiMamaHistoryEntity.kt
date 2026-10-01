package com.yenaly.han1meviewer.HentaiMama.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "hentaimama_watch_history")
data class HentaiMamaHistoryEntity(
    @PrimaryKey
    val videoCode: String,
    val title: String,
    val coverUrl: String,
    val lastEpisodeUrl: String,
    val lastEpisodeNumber: Float,
    val lastEpisodeTitle: String,
    val lastPosition: Long,
    val totalDuration: Long,
    val watchDate: Long,
    val watchDuration: Long = 0L,
    val watchCount: Int = 1,
    val completed: Boolean = false,
)
