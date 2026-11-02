package com.yenaly.han1meviewer.logic

import android.util.Log
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.logic.dao.DownloadDatabase
import com.yenaly.han1meviewer.logic.dao.HistoryDatabase
import com.yenaly.han1meviewer.logic.dao.MiscellanyDatabase
import com.yenaly.han1meviewer.logic.entity.HKeyframeEntity
import com.yenaly.han1meviewer.logic.entity.HKeyframeHeader
import com.yenaly.han1meviewer.logic.entity.HKeyframeType
import com.yenaly.han1meviewer.logic.entity.HanimeAdvancedSearchHistoryEntity
import com.yenaly.han1meviewer.logic.entity.SearchHistoryEntity
import com.yenaly.han1meviewer.logic.entity.WatchHistoryEntity
import com.yenaly.han1meviewer.logic.entity.download.DownloadGroupEntity
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.model.SearchOption
import com.yenaly.yenaly_libs.utils.applicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.FileNotFoundException

object DatabaseRepo {

    object HKeyframe {
        private val hKeyframeDao = MiscellanyDatabase.instance.hKeyframeDao

        fun loadAll(keyword: String? = null) =
            if (keyword != null) hKeyframeDao.loadAll(keyword)
            else hKeyframeDao.loadAll()

        @OptIn(ExperimentalSerializationApi::class)
        fun loadAllShared(): Flow<List<HKeyframeType>> = flow {
            val res = applicationContext.assets.let { assets ->
                assets.list("h_keyframes")?.asSequence()
                    ?.filter { it.endsWith(".json") }
                    ?.mapNotNull { fileName ->
                        try {
                            assets.open("h_keyframes/$fileName").use { inputStream ->
                                Json.decodeFromStream<HKeyframeEntity>(inputStream)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    }
                    ?.sortedWith(
                        compareBy<HKeyframeEntity> { it.group }.thenBy { it.episode }
                    )
                    ?.groupBy { it.group ?: "???" }
                    ?.flatMap { (group, entities) ->
                        listOf(HKeyframeHeader(title = group, attached = entities)) + entities
                    }
                    .orEmpty()
            }
            emit(res)
        }

        suspend fun findBy(videoCode: String) =
            hKeyframeDao.findBy(videoCode)

        @OptIn(ExperimentalSerializationApi::class)
        fun observe(videoCode: String): Flow<HKeyframeEntity?> {
            if (Preferences.sharedHKeyframesEnable) {
                return flow t@{
                    val find = hKeyframeDao.findBy(videoCode)
                    if (find == null || Preferences.sharedHKeyframesUseFirst) {
                        runCatching {
                            applicationContext.assets
                                .open("h_keyframes/$videoCode.json")
                                .use { inputStream ->
                                    val entity = Json.decodeFromStream<HKeyframeEntity>(inputStream)
                                    this@t.emit(entity)
                                }
                        }.onFailure { e ->

                            if (e is FileNotFoundException) {
                                Log.w("HKeyframe", "未找到关键帧文件: $videoCode.json")
                            } else {
                                Log.e("HKeyframe", "读取关键帧失败: ${e.message}", e)
                            }
                        }
                    } else {
                        hKeyframeDao.observe(videoCode).collect {
                            this@t.emit(it)
                        }
                    }
                }.catch t@{ e ->
                    e.printStackTrace()
                    hKeyframeDao.observe(videoCode).collect {
                        this@t.emit(it)
                    }
                }
            }
            return hKeyframeDao.observe(videoCode)
        }

        suspend fun insert(entity: HKeyframeEntity) = hKeyframeDao.insert(entity)

        suspend fun update(entity: HKeyframeEntity) = hKeyframeDao.update(entity)

        suspend fun delete(entity: HKeyframeEntity) =
            hKeyframeDao.delete(entity)

        suspend fun modifyKeyframe(
            videoCode: String,
            oldKeyframe: HKeyframeEntity.Keyframe, keyframe: HKeyframeEntity.Keyframe,
        ) = hKeyframeDao.modifyKeyframe(videoCode, oldKeyframe, keyframe)

        suspend fun appendKeyframe(
            videoCode: String, title: String,
            keyframe: HKeyframeEntity.Keyframe,
        ) = hKeyframeDao.appendKeyframe(videoCode, title, keyframe)

        suspend fun removeKeyframe(
            videoCode: String,
            keyframe: HKeyframeEntity.Keyframe,
        ) = hKeyframeDao.removeKeyframe(videoCode, keyframe)
    }

    object SearchHistory {
        private val searchHistoryDao = HistoryDatabase.instance.searchHistory

        @JvmOverloads
        fun loadAll(keyword: String? = null) =
            if (keyword.isNullOrBlank()) searchHistoryDao.loadAll()
            else searchHistoryDao.loadAll(keyword)

        suspend fun delete(history: SearchHistoryEntity) =
            searchHistoryDao.delete(history)

        suspend fun insert(history: SearchHistoryEntity) =
            searchHistoryDao.insertOrUpdate(history)

        suspend fun deleteByKeyword(query: String) =
            searchHistoryDao.deleteByKeyword(query)
    }

    object HanimeAdvancedSearchRepo {
        private val dao = HistoryDatabase.instance.hanimeAdvancedSearchHistory

        suspend fun saveSearch(
            query: String?,
            genre: String?,
            sort: String?,
            broad: Boolean?,
            date: String?,
            duration: String?,
            tags: Set<SearchOption>?,
            brands: Set<SearchOption>?
        ) {
            val entity = HanimeAdvancedSearchHistoryEntity(
                query = query,
                genre = genre,
                sort = sort,
                broad = broad,
                date = date,
                duration = duration,
                tags = tags?.toDbString(),
                brands = brands?.toDbString()
            )
            dao.insertHistory(entity)
        }

        fun getSearchHistories(limit: Int = 20) = dao.loadHistories(limit)
        suspend fun deleteHistory(id: Long) = dao.deleteHistory(id)
        fun Set<SearchOption>.toDbString(): String =
            mapNotNull { it.searchKey }.joinToString(",")
        fun String.toSearchOptionSet(): Set<SearchOption> =
            if (isBlank()) emptySet()
            else split(",").map { SearchOption(searchKey = it) }.toSet()
    }

    object WatchHistory {
        private val watchHistoryDao = HistoryDatabase.instance.watchHistory

        fun loadAll() =
            watchHistoryDao.loadAll()

        suspend fun delete(history: WatchHistoryEntity) =
            watchHistoryDao.delete(history)

        suspend fun deleteAll() =
            watchHistoryDao.deleteAll()

        suspend fun update(history: WatchHistoryEntity) =
            watchHistoryDao.update(history)

        suspend fun updateProgress(videoCode: String,progress: Long) =
            watchHistoryDao.updateProgress(videoCode, progress)

        suspend fun insert(history: WatchHistoryEntity) =
            watchHistoryDao.insertOrUpdate(history)

        suspend fun findBy(videoCode: String) =
            watchHistoryDao.findBy(videoCode)

        suspend fun getWatched(resultList: List<String>) =
            watchHistoryDao.getWatchedCodes(resultList)

        suspend fun markWatched(videoCode: String) =
            watchHistoryDao.markWatched(videoCode)
    }

    object HanimeDownload {
        private val hanimeDownloadDao = DownloadDatabase.instance.hanimeDownloadDao
        private val downloadGroupDao = DownloadDatabase.instance.downloadGroupDao
        fun loadAllDownloadingHanime() =
            hanimeDownloadDao.loadAllDownloadingHanime()

        fun loadAllDownloadedHanime(
            sortedBy: HanimeDownloadEntity.SortedBy,
            ascending: Boolean,
        ) = when (sortedBy) {
            HanimeDownloadEntity.SortedBy.TITLE ->
                hanimeDownloadDao.loadAllDownloadedHanimeByTitle(ascending)

            HanimeDownloadEntity.SortedBy.ID ->
                hanimeDownloadDao.loadAllDownloadedHanimeById(ascending)
        }
        suspend fun delete(videoCode: String, quality: String) =
            hanimeDownloadDao.delete(videoCode, quality)

        suspend fun delete(videoCode: String) =
            hanimeDownloadDao.delete(videoCode)

        suspend fun pauseAll() =
            hanimeDownloadDao.pauseAll()

        suspend fun delete(entity: HanimeDownloadEntity) =
            hanimeDownloadDao.delete(entity)

        suspend fun insert(entity: HanimeDownloadEntity) =
            hanimeDownloadDao.insert(entity)

        suspend fun update(entity: HanimeDownloadEntity) =
            hanimeDownloadDao.update(entity)

        suspend fun find(videoCode: String, quality: String) =
            hanimeDownloadDao.find(videoCode, quality)

        suspend fun find(videoCode: String) =
            hanimeDownloadDao.find(videoCode)

        suspend fun insertDefaultGroup() =
            downloadGroupDao.insertDefaultGroup()

        fun getAllGroups()=
            downloadGroupDao.getAllGroups()

        suspend fun getGroupById(id: Int)=
            downloadGroupDao.getGroupById(id)

        suspend fun findGroupByName(name: String)=
            downloadGroupDao.getGroupByName(name)

        suspend fun updateVideoGroup(videoCode: String, newGroupId: Int)=
            hanimeDownloadDao.updateVideoGroup(videoCode, newGroupId)

        suspend fun findGroupIdOfSeries(videoCodes: List<String>) =
            hanimeDownloadDao.findGroupIdOfSeries(videoCodes)

        suspend fun createNewGroup(name: String): Long{
            val maxIndex = downloadGroupDao.getMaxOrderIndex() ?: 0
            val newIndex = maxIndex + 1
            val newGroup = DownloadGroupEntity(
                name = name,
                orderIndex = newIndex
            )
            return downloadGroupDao.insert(newGroup)
        }
        suspend fun deleteGroup(group: DownloadGroupEntity) {
            downloadGroupDao.deleteGroup(group)
        }

        suspend fun updateGroup(group: DownloadGroupEntity)=
            downloadGroupDao.update(group)

        private const val SITE_GROUP_ID_PREFIX = "download_site_group_id_"
        private const val SITE_GROUPS_MIGRATED = "download_site_groups_migrated_v1"

        fun siteGroupName(site: SiteType): String = when (site) {
            SiteType.HANIME, SiteType.JAVCHU -> "Hanime1"
            SiteType.MISSAV -> "MissAV"
            SiteType.HENTAIMAMA -> "HentaiMama"
            SiteType.ONAIR -> "OnAir"
            SiteType.FAPFOLDER -> "FapFolder"
        }

        fun siteOfVideoCode(videoCode: String): SiteType =
            SiteCheckIn.siteOfDishCode(videoCode) ?: SiteType.HANIME

        /**
         * Id of the default "site name" group. The id is remembered so renaming the group
         * keeps working; if the group was deleted it is created again.
         */
        suspend fun siteGroupId(site: SiteType): Int {
            val key = SITE_GROUP_ID_PREFIX + (if (site == SiteType.JAVCHU) SiteType.HANIME else site).name
            val sp = Preferences.preferenceSp
            val saved = sp.getInt(key, -1)
            if (saved > 0 && downloadGroupDao.getGroupById(saved) != null) return saved
            val name = siteGroupName(site)
            val id = downloadGroupDao.getGroupByName(name)?.id ?: createNewGroup(name).toInt()
            sp.edit().putInt(key, id).apply()
            return id
        }

        suspend fun groupIdForVideoCode(videoCode: String): Int =
            siteGroupId(siteOfVideoCode(videoCode))

        /**
         * One-time: create a group per site and move existing downloads that are still in the
         * default group into their site's group. Later manual moves are never touched again.
         */
        suspend fun migrateToSiteGroupsOnce() {
            val sp = Preferences.preferenceSp
            if (sp.getBoolean(SITE_GROUPS_MIGRATED, false)) return
            downloadGroupDao.insertDefaultGroup()
            SiteType.entries.filter { it != SiteType.JAVCHU }.forEach { siteGroupId(it) }
            hanimeDownloadDao.getAll()
                .filter { it.groupId == DownloadGroupEntity.DEFAULT_GROUP_ID }
                .map { it.videoCode }
                .distinct()
                .forEach { code ->
                    hanimeDownloadDao.updateVideoGroup(code, groupIdForVideoCode(code))
                }
            sp.edit().putBoolean(SITE_GROUPS_MIGRATED, true).apply()
        }
    }
}


