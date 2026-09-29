@file:Suppress("PLUGIN_IS_NOT_ENABLED")

package com.yenaly.han1meviewer.logic.entity

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.yenaly.han1meviewer.logic.model.MultiItemEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface HKeyframeType : MultiItemEntity {
    companion object {
        const val H_KEYFRAME = 0
        const val HEADER = 1
    }
}

@Serializable
@Entity
@TypeConverters(HKeyframeEntity.KeyframeTypeConverter::class)
data class HKeyframeEntity(
    @PrimaryKey val videoCode: String,
    val title: String,

    val keyframes: MutableList<Keyframe>,

    val lastModifiedTime: Long = -1,

    val createdTime: Long = -1,

    val author: String? = null,
) : HKeyframeType {

    @Ignore
    val group: String? = null

    @Ignore
    val episode: Int = -1

    @Ignore
    override val itemType: Int = HKeyframeType.H_KEYFRAME

    @Serializable
    data class Keyframe(

        val position: Long,

        val prompt: String?,
    )

    class KeyframeTypeConverter {
        @TypeConverter
        fun fromKeyframeList(keyframes: MutableList<Keyframe>): String =
            Json.encodeToString(keyframes)

        @TypeConverter
        fun toKeyframeList(keyframes: String): MutableList<Keyframe> =
            Json.decodeFromString(keyframes)
    }
}

data class HKeyframeHeader(
    val title: String,

    val attached: List<HKeyframeEntity>,
    override val itemType: Int = HKeyframeType.HEADER,
) : HKeyframeType
