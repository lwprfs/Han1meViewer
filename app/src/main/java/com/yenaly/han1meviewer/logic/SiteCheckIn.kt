package com.yenaly.han1meviewer.logic

import android.content.Context
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.logic.dao.CheckInRecordDatabase
import com.yenaly.han1meviewer.logic.dao.HistoryDatabase
import com.yenaly.han1meviewer.logic.entity.CheckInRecordEntity
import com.yenaly.han1meviewer.logic.entity.CheckInType
import com.yenaly.han1meviewer.logic.entity.SideDishEntity
import com.yenaly.han1meviewer.logic.entity.WatchHistoryEntity
import com.yenaly.han1meviewer.FapFolder.data.local.FapFolderDatabase
import com.yenaly.han1meviewer.HentaiMama.data.local.HentaiMamaDatabase
import com.yenaly.han1meviewer.MissAV.data.local.MissAvDatabase
import com.yenaly.han1meviewer.OnAir.data.local.OnAirDatabase
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Check-in ("did you cum?") helpers shared by every site.
 *
 * Hanime / Javchu video codes are stored as-is (backwards compatible). Every other site is stored
 * with a `<site>:` prefix so codes from different sites can never collide inside the shared
 * `sidedishes` table.
 */
object SiteCheckIn {

    const val SEP = "\u001E"

    private fun prefixOf(site: SiteType): String? = when (site) {
        SiteType.HANIME, SiteType.JAVCHU -> null
        SiteType.MISSAV -> "missav"
        SiteType.HENTAIMAMA -> "hentaimama"
        SiteType.ONAIR -> "onair"
        SiteType.FAPFOLDER -> "fapfolder"
    }

    fun dishCode(site: SiteType, code: String): String {
        val prefix = prefixOf(site) ?: return code
        return "$prefix:$code"
    }

    /** Returns the site for a prefixed dish code, or null for Hanime-style plain codes. */
    fun siteOfDishCode(dishCode: String): SiteType? {
        val prefix = dishCode.substringBefore(":", "")
        if (prefix.isEmpty() || prefix == dishCode) return null
        return when (prefix) {
            "missav" -> SiteType.MISSAV
            "hentaimama" -> SiteType.HENTAIMAMA
            "onair" -> SiteType.ONAIR
            "fapfolder" -> SiteType.FAPFOLDER
            else -> null
        }
    }

    fun rawCodeOfDishCode(dishCode: String): String =
        if (siteOfDishCode(dishCode) != null) dishCode.substringAfter(":") else dishCode

    suspend fun todayCount(context: Context): Int {
        return CheckInRecordDatabase.getDatabase(context).checkInDao()
            .getCountByDate(LocalDate.now().toString())
    }

    suspend fun quickCheckIn(
        context: Context,
        site: SiteType,
        videoCode: String,
        title: String,
        coverUrl: String?,
        feeling: String,
    ) {
        val db = CheckInRecordDatabase.getDatabase(context)
        val code = dishCode(site, videoCode)
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val record = CheckInRecordEntity(
            date = LocalDate.now().toString(),
            time = time,
            type = CheckInType.MASTURBATION.storeName,
            sideDishes = "$title$SEP$code",
            feeling = feeling,
        )
        db.checkInDao().insert(record)
        if (!coverUrl.isNullOrBlank()) {
            db.sideDishDao().insertAll(
                listOf(SideDishEntity(videoCode = code, title = title, coverUrl = coverUrl))
            )
        }
    }

    /**
     * Recently watched videos of every site merged into [WatchHistoryEntity] rows (non Hanime
     * sites use prefixed video codes) sorted by watch date, newest first.
     */
    suspend fun recentWatchedAllSites(context: Context, limit: Int): List<WatchHistoryEntity> {
        val result = mutableListOf<WatchHistoryEntity>()
        runCatching {
            result += HistoryDatabase.instance.watchHistory.getRecentWatches(limit)
        }
        runCatching {
            MissAvDatabase.getInstance(context).missAvHistoryDao().getPage(limit, 0).forEach {
                result += WatchHistoryEntity(
                    coverUrl = it.coverUrl,
                    title = it.title,
                    releaseDate = 0L,
                    watchDate = it.watchDate,
                    videoCode = dishCode(SiteType.MISSAV, it.videoCode),
                )
            }
        }
        runCatching {
            HentaiMamaDatabase.getInstance(context).historyDao().getPage(limit, 0).forEach {
                result += WatchHistoryEntity(
                    coverUrl = it.coverUrl,
                    title = if (it.lastEpisodeTitle.isBlank()) it.title
                    else "${it.title} - ${it.lastEpisodeTitle}",
                    releaseDate = 0L,
                    watchDate = it.watchDate,
                    videoCode = dishCode(SiteType.HENTAIMAMA, it.videoCode),
                )
            }
        }
        runCatching {
            OnAirDatabase.getInstance(context).onAirHistoryDao().getPage(limit, 0).forEach {
                result += WatchHistoryEntity(
                    coverUrl = it.coverUrl,
                    title = it.title,
                    releaseDate = 0L,
                    watchDate = it.watchDate,
                    videoCode = dishCode(SiteType.ONAIR, it.videoId),
                )
            }
        }
        runCatching {
            FapFolderDatabase.getInstance(context).fapFolderHistoryDao().getPage(limit, 0)
                .forEach {
                    result += WatchHistoryEntity(
                        coverUrl = it.coverUrl,
                        title = it.title,
                        releaseDate = 0L,
                        watchDate = it.watchDate,
                        videoCode = dishCode(SiteType.FAPFOLDER, it.postId.toString()),
                    )
                }
        }
        return result.sortedByDescending { it.watchDate }.take(limit)
    }

    /** Stores covers of watched items so the check-in page can show thumbnails for them. */
    suspend fun rememberCover(context: Context, dishCode: String, title: String, coverUrl: String) {
        if (coverUrl.isBlank()) return
        CheckInRecordDatabase.getDatabase(context).sideDishDao()
            .insertAll(listOf(SideDishEntity(dishCode, title, coverUrl)))
    }
}
