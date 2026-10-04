package com.yenaly.han1meviewer.HentaiMama.data.local

import android.content.Context
import com.yenaly.han1meviewer.HentaiMama.data.model.SeriesCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object HentaiMamaSeriesRepo {

    @Volatile private var isInitialized = false
    @Volatile private lateinit var appContext: Context

    private val writeMutex = Mutex()

    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            appContext = context.applicationContext
            isInitialized = true
        }
    }

    private val dao: HentaiMamaSeriesDao by lazy {
        check(isInitialized) { "HentaiMamaSeriesRepo not initialized" }
        HentaiMamaDatabase.getInstance(appContext).seriesDao()
    }

    fun loadAll(): Flow<List<HentaiMamaSeriesEntity>> = dao.loadAll()

    suspend fun getBySlug(slug: String): HentaiMamaSeriesEntity? =
        withContext(Dispatchers.IO) {
            runCatching { dao.getBySlug(slug) }.getOrNull()
        }

    suspend fun getBySource(source: String): List<HentaiMamaSeriesEntity> =
        withContext(Dispatchers.IO) {
            runCatching { dao.getBySource(source) }.getOrDefault(emptyList())
        }

    suspend fun upsert(card: SeriesCard, source: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            dao.upsert(card.toEntity(source))
        }
    }

    suspend fun upsertAll(cards: List<SeriesCard>, source: String) =
        withContext(Dispatchers.IO) {
            if (cards.isEmpty()) return@withContext
            writeMutex.withLock {
                dao.upsertAll(cards.map { it.toEntity(source) })
            }
        }

    suspend fun deleteBySlug(slug: String) = withContext(Dispatchers.IO) {
        dao.deleteBySlug(slug)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) { dao.deleteAll() }

    private fun SeriesCard.toEntity(source: String): HentaiMamaSeriesEntity =
        HentaiMamaSeriesEntity(
            slug = slug,
            url = url,
            title = title,
            altTitles = altTitles.joinToString("|"),
            thumbSmall = thumbSmall,
            thumbFull = thumbFull,
            posterAlt = posterAlt,
            rating = rating,
            favorites = favorites,
            postId = postId,
            nonce = nonce,
            studios = studios.joinToString("|"),
            studioUrls = studioUrls.joinToString("|"),
            year = year,
            viewsRaw = viewsRaw,
            views = views,
            episodeCount = episodeCount,
            description = description,
            hasLongDescription = hasLongDescription,
            genres = genres.joinToString("|"),
            genreSlugs = genreSlugs.joinToString("|"),
            source = source,
            fetchedAt = System.currentTimeMillis(),
        )
}
