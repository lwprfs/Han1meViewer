package com.yenaly.han1meviewer.HentaiMama.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HentaiMamaSeriesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HentaiMamaSeriesEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<HentaiMamaSeriesEntity>)

    @Query("SELECT * FROM hentaimama_series ORDER BY fetchedAt DESC")
    fun loadAll(): Flow<List<HentaiMamaSeriesEntity>>

    @Query("SELECT * FROM hentaimama_series WHERE slug = :slug LIMIT 1")
    suspend fun getBySlug(slug: String): HentaiMamaSeriesEntity?

    @Query("SELECT * FROM hentaimama_series WHERE source = :source ORDER BY fetchedAt DESC")
    suspend fun getBySource(source: String): List<HentaiMamaSeriesEntity>

    @Query("DELETE FROM hentaimama_series WHERE slug = :slug")
    suspend fun deleteBySlug(slug: String)

    @Query("DELETE FROM hentaimama_series")
    suspend fun deleteAll()
}
