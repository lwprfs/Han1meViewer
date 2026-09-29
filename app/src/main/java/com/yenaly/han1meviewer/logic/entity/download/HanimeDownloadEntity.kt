package com.yenaly.han1meviewer.logic.entity.download

import androidx.annotation.IntRange
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.yenaly.han1meviewer.HFileManager
import com.yenaly.han1meviewer.logic.state.DownloadState
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = DownloadGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_DEFAULT
        )
    ],
    indices = [
        Index(value = ["groupId"])
    ]
)
@TypeConverters(HanimeDownloadEntity.StateTypeConverter::class)
data class HanimeDownloadEntity(

    val groupId: Int = DownloadGroupEntity.DEFAULT_GROUP_ID,

    val coverUrl: String,

    var coverUri: String?,

    val title: String,

    val addDate: Long,

    val videoCode: String,

    val videoUri: String,

    val quality: String,

    val videoUrl: String,

    val length: Long,

    val downloadedLength: Long,

    val state: DownloadState = DownloadState.Unknown,

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
) {

    @get:IntRange(from = 0, to = 100)
    val progress get() = if (length <= 0) 0 else (downloadedLength * 100 / length).toInt()

    val isDownloaded get() = state == DownloadState.Finished

    val isDownloading get() = state == DownloadState.Downloading

    val suffix get() = videoUri.substringAfterLast(".", HFileManager.DEF_VIDEO_TYPE)

    enum class SortedBy {
        ID, TITLE
    }

    class StateTypeConverter {
        @TypeConverter
        fun from(state: DownloadState): Int = state.mask

        @TypeConverter
        fun to(state: Int): DownloadState = DownloadState.from(state)
    }
}
