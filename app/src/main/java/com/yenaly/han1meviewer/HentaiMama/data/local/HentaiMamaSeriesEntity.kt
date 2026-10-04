package com.yenaly.han1meviewer.HentaiMama.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hentaimama_series",
    indices = [Index(value = ["slug"], unique = true)],
)
data class HentaiMamaSeriesEntity(
    @PrimaryKey val slug: String,
    val url: String,
    val title: String,
    val altTitles: String,
    val thumbSmall: String?,
    val thumbFull: String,
    val posterAlt: String,
    val rating: Double?,
    val favorites: Int?,
    val postId: Int?,
    val nonce: String?,
    val studios: String,
    val studioUrls: String,
    val year: Int?,
    val viewsRaw: String?,
    val views: Long?,
    val episodeCount: Int?,
    val description: String?,
    val hasLongDescription: Boolean,
    val genres: String,
    val genreSlugs: String,
    val source: String,
    val fetchedAt: Long,
)
