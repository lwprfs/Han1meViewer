package com.yenaly.han1meviewer.logic

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.yenaly.han1meviewer.BuildConfig
import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.common.FapFolderSecurePrefs
import com.yenaly.han1meviewer.FapFolder.data.local.FapFolderDatabase
import com.yenaly.han1meviewer.FapFolder.data.local.FapFolderHistoryEntity
import com.yenaly.han1meviewer.FapFolder.data.remote.FapFolderCookieJar
import com.yenaly.han1meviewer.HentaiMama.data.local.HentaiMamaDatabase
import com.yenaly.han1meviewer.HentaiMama.data.local.HentaiMamaHistoryEntity
import com.yenaly.han1meviewer.MissAV.data.local.MissAvDatabase
import com.yenaly.han1meviewer.MissAV.data.local.MissAvHistoryEntity
import com.yenaly.han1meviewer.OnAir.data.local.OnAirDatabase
import com.yenaly.han1meviewer.OnAir.data.local.OnAirHistoryEntity
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.dao.CheckInRecordDatabase
import com.yenaly.han1meviewer.logic.dao.DownloadDatabase
import com.yenaly.han1meviewer.logic.dao.HistoryDatabase
import com.yenaly.han1meviewer.logic.dao.MiscellanyDatabase
import com.yenaly.han1meviewer.logic.entity.CheckInRecordEntity
import com.yenaly.han1meviewer.logic.entity.HKeyframeEntity
import com.yenaly.han1meviewer.logic.entity.SideDishEntity
import com.yenaly.han1meviewer.logic.entity.WatchHistoryEntity
import com.yenaly.han1meviewer.logic.entity.download.DownloadCategoryEntity
import com.yenaly.han1meviewer.logic.entity.download.DownloadGroupEntity
import com.yenaly.han1meviewer.logic.entity.download.HanimeCategoryCrossRef
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.OutputStream

object BackupManager {
    private const val BACKUP_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    /** What can be picked on the export dialog. */
    enum class Section(val label: String, val group: Group) {
        SETTINGS("App settings (all sites)", Group.DATA),
        CHECK_IN("Check-in records & dishes", Group.DATA),
        KEYFRAMES("H keyframes", Group.DATA),
        HANIME_HISTORY("Hanime / Javchu watch history", Group.DATA),
        DOWNLOADS("Download list", Group.DATA),
        MISSAV_HISTORY("MissAV watch history", Group.DATA),
        HENTAIMAMA_HISTORY("HentaiMama watch history", Group.DATA),
        ONAIR_HISTORY("OnAir watch history", Group.DATA),
        FAPFOLDER_HISTORY("FapFolder watch history", Group.DATA),
        COOKIES_HANIME("Hanime / Javchu cookies", Group.COOKIES),
        COOKIES_MISSAV("MissAV cookies", Group.COOKIES),
        COOKIES_HENTAIMAMA("HentaiMama cookies", Group.COOKIES),
        COOKIES_ONAIR("OnAir cookies", Group.COOKIES),
        COOKIES_FAPFOLDER("FapFolder cookies", Group.COOKIES);

        enum class Group { DATA, COOKIES }
    }

    private const val KEY_ONAIR_COOKIES = "onair_cookies_raw"

    private fun cookieSectionOfKey(key: String): Section? = when {
        key == com.yenaly.han1meviewer.LOGIN_COOKIE ||
                key == com.yenaly.han1meviewer.CLOUDFLARE_COOKIE -> Section.COOKIES_HANIME
        key.startsWith("missav_cf_") -> Section.COOKIES_MISSAV
        key.startsWith("hm_cf_") -> Section.COOKIES_HENTAIMAMA
        key == KEY_ONAIR_COOKIES -> Section.COOKIES_ONAIR
        else -> null
    }

    @Serializable
    private data class BackupData(
        val version: Int = BACKUP_VERSION,
        val appVersionCode: Int = BuildConfig.VERSION_CODE,
        val appVersionName: String = BuildConfig.VERSION_NAME,
        val exportedAt: Long = System.currentTimeMillis(),
        val settings: Map<String, PreferenceValue>? = null,
        val hKeyframes: List<HKeyframeEntity>? = null,
        val checkInRecords: List<CheckInRecordEntity>? = null,
        val sideDishes: List<SideDishEntity>? = null,
        val watchHistories: List<WatchHistoryEntity>? = null,
        val downloadGroups: List<DownloadGroupEntity>? = null,
        val downloads: List<HanimeDownloadEntity>? = null,
        val downloadCategories: List<DownloadCategoryEntity>? = null,
        val downloadCategoryCrossRefs: List<HanimeCategoryCrossRef>? = null,
        val cookies: Map<String, Map<String, PreferenceValue>>? = null,
        val fapFolderCookies: String? = null,
        val missAvHistories: List<MissAvHistoryEntity>? = null,
        val hentaiMamaHistories: List<HentaiMamaHistoryEntity>? = null,
        val onAirHistories: List<OnAirHistoryEntity>? = null,
        val fapFolderHistories: List<FapFolderHistoryEntity>? = null,
    )

    @Serializable
    private sealed interface PreferenceValue {
        @Serializable
        data class BooleanValue(val value: Boolean) : PreferenceValue

        @Serializable
        data class FloatValue(val value: Float) : PreferenceValue

        @Serializable
        data class IntValue(val value: Int) : PreferenceValue

        @Serializable
        data class LongValue(val value: Long) : PreferenceValue

        @Serializable
        data class StringValue(val value: String) : PreferenceValue

        @Serializable
        data class StringSetValue(val value: Set<String>) : PreferenceValue
    }

    suspend fun exportTo(
        context: Context,
        uri: Uri,
        sections: Set<Section> = Section.entries.toSet(),
    ) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            exportTo(context, outputStream, sections)
        } ?: error("Unable to open backup file")
    }

    suspend fun importFrom(
        context: Context,
        uri: Uri,
        sections: Set<Section> = Section.entries.toSet(),
    ) {
        val backup = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            json.decodeFromString<BackupData>(inputStream.bufferedReader().readText())
        } ?: error("Unable to open backup file")

        backup.hKeyframes?.takeIf { Section.KEYFRAMES in sections }?.let { hKeyframes ->
            MiscellanyDatabase.instance.hKeyframeDao.apply {
                deleteAll()
                insertAll(hKeyframes)
            }
        }

        backup.checkInRecords?.takeIf { Section.CHECK_IN in sections }?.let { checkInRecords ->
            CheckInRecordDatabase.getDatabase(context).checkInDao().apply {
                deleteAll()
                insertAll(checkInRecords.map { it.normalizeSideDishes() })
            }
        }

        backup.sideDishes?.takeIf { Section.CHECK_IN in sections }?.let { sideDishes ->
            CheckInRecordDatabase.getDatabase(context).sideDishDao().apply {
                deleteAll()
                insertAll(sideDishes)
            }
        }

        backup.watchHistories?.takeIf { Section.HANIME_HISTORY in sections }?.let { watchHistories ->
            HistoryDatabase.instance.watchHistory.apply {
                deleteAll()
                insertAll(watchHistories)
            }
        }

        if (Section.DOWNLOADS in sections && (backup.downloadGroups != null ||
                    backup.downloads != null ||
                    backup.downloadCategories != null || backup.downloadCategoryCrossRefs != null)
        ) {
            val downloadGroups = backup.downloadGroups.orEmpty()
            val groupIds = downloadGroups.mapTo(mutableSetOf()) { it.id } +
                    DownloadGroupEntity.DEFAULT_GROUP_ID
            val downloads = backup.downloads.orEmpty().map { download ->
                if (download.groupId in groupIds) {
                    download
                } else {
                    download.copy(groupId = DownloadGroupEntity.DEFAULT_GROUP_ID)
                }
            }
            val downloadCategories = backup.downloadCategories.orEmpty()
            val downloadIds = downloads.mapTo(mutableSetOf()) { it.id }
            val categoryIds = downloadCategories.mapTo(mutableSetOf()) { it.id }
            val crossRefs = backup.downloadCategoryCrossRefs.orEmpty().filter { crossRef ->
                crossRef.videoId in downloadIds && crossRef.categoryId in categoryIds
            }

            DownloadDatabase.instance.apply {
                downloadCategoryDao.deleteAllCrossRefs()
                hanimeDownloadDao.deleteAll()
                downloadCategoryDao.deleteAllCategories()
                downloadGroupDao.deleteAll()
                downloadGroupDao.insertAll(downloadGroups)
                downloadGroupDao.insertDefaultGroup()
                downloadCategoryDao.insertAllCategories(downloadCategories)
                hanimeDownloadDao.insertAll(downloads)
                downloadCategoryDao.insertAllCrossRefs(crossRefs)
            }
        }

        backup.missAvHistories?.takeIf { Section.MISSAV_HISTORY in sections }?.let {
            MissAvDatabase.getInstance(context).missAvHistoryDao().apply {
                deleteAll(); insertAll(it)
            }
        }
        backup.hentaiMamaHistories?.takeIf { Section.HENTAIMAMA_HISTORY in sections }?.let {
            HentaiMamaDatabase.getInstance(context).historyDao().apply {
                deleteAll(); insertAll(it)
            }
        }
        backup.onAirHistories?.takeIf { Section.ONAIR_HISTORY in sections }?.let {
            OnAirDatabase.getInstance(context).onAirHistoryDao().apply {
                deleteAll(); insertAll(it)
            }
        }
        backup.fapFolderHistories?.takeIf { Section.FAPFOLDER_HISTORY in sections }?.let {
            FapFolderDatabase.getInstance(context).fapFolderHistoryDao().apply {
                deleteAll(); insertAll(it)
            }
        }

        backup.settings?.takeIf { Section.SETTINGS in sections }?.let { applyPrefs(it) }

        backup.cookies?.forEach { (sectionName, prefs) ->
            val section = Section.entries.firstOrNull { it.name == sectionName } ?: return@forEach
            if (section in sections) applyPrefs(prefs)
        }

        if (Section.COOKIES_FAPFOLDER in sections) {
            backup.fapFolderCookies?.takeIf { it.isNotBlank() }?.let { raw ->
                FapFolderSecurePrefs.instance.edit(commit = true) {
                    putString(FapFolderConstants.PREF_FAPFOLDER_COOKIES_RAW, raw)
                }
                runCatching { FapFolderCookieJar().restoreFromPreferences() }
            }
        }
    }

    private fun applyPrefs(settings: Map<String, PreferenceValue>) {
        Preferences.preferenceSp.edit {
            settings.forEach { (key, value) ->
                when (value) {
                    is PreferenceValue.BooleanValue -> putBoolean(key, value.value)
                    is PreferenceValue.FloatValue -> putFloat(key, value.value)
                    is PreferenceValue.IntValue -> putInt(key, value.value)
                    is PreferenceValue.LongValue -> putLong(key, value.value)
                    is PreferenceValue.StringSetValue -> putStringSet(key, value.value)
                    is PreferenceValue.StringValue -> putString(key, value.value)
                }
            }
        }
    }

    private suspend fun exportTo(
        context: Context,
        outputStream: OutputStream,
        sections: Set<Section>,
    ) {
        val allPrefs = Preferences.preferenceSp.all.mapValuesNotNull { (_, value) ->
            value.toPreferenceValue()
        }
        val settings = allPrefs.filterKeys { cookieSectionOfKey(it) == null }
        val cookies = Section.entries
            .filter { it.group == Section.Group.COOKIES && it in sections }
            .associate { section ->
                section.name to allPrefs.filterKeys { cookieSectionOfKey(it) == section }
            }
        val checkInDb = CheckInRecordDatabase.getDatabase(context)
        val backup = BackupData(
            settings = if (Section.SETTINGS in sections) settings else null,
            hKeyframes = if (Section.KEYFRAMES in sections)
                MiscellanyDatabase.instance.hKeyframeDao.getAll() else null,
            checkInRecords = if (Section.CHECK_IN in sections)
                checkInDb.checkInDao().getAllRecords() else null,
            sideDishes = if (Section.CHECK_IN in sections)
                checkInDb.sideDishDao().getAll() else null,
            watchHistories = if (Section.HANIME_HISTORY in sections)
                HistoryDatabase.instance.watchHistory.getAll() else null,
            downloadGroups = if (Section.DOWNLOADS in sections)
                DownloadDatabase.instance.downloadGroupDao.getAllGroupsOnce() else null,
            downloads = if (Section.DOWNLOADS in sections)
                DownloadDatabase.instance.hanimeDownloadDao.getAll() else null,
            downloadCategories = if (Section.DOWNLOADS in sections)
                DownloadDatabase.instance.downloadCategoryDao.getAllCategoriesOnce() else null,
            downloadCategoryCrossRefs = if (Section.DOWNLOADS in sections)
                DownloadDatabase.instance.downloadCategoryDao.getAllCrossRefs() else null,
            cookies = cookies.takeIf { it.isNotEmpty() },
            fapFolderCookies = if (Section.COOKIES_FAPFOLDER in sections) {
                runCatching {
                    FapFolderSecurePrefs.instance.getString(
                        FapFolderConstants.PREF_FAPFOLDER_COOKIES_RAW, null
                    ) ?: FapFolderSecurePrefs.readFallback(
                        FapFolderConstants.PREF_FAPFOLDER_COOKIES_RAW
                    )
                }.getOrNull()
            } else null,
            missAvHistories = if (Section.MISSAV_HISTORY in sections)
                MissAvDatabase.getInstance(context).missAvHistoryDao().getAllHistorySync() else null,
            hentaiMamaHistories = if (Section.HENTAIMAMA_HISTORY in sections)
                HentaiMamaDatabase.getInstance(context).historyDao().getAll() else null,
            onAirHistories = if (Section.ONAIR_HISTORY in sections)
                OnAirDatabase.getInstance(context).onAirHistoryDao().getAll() else null,
            fapFolderHistories = if (Section.FAPFOLDER_HISTORY in sections)
                FapFolderDatabase.getInstance(context).fapFolderHistoryDao().getAll() else null,
        )
        outputStream.bufferedWriter().use { writer ->
            writer.write(json.encodeToString(backup))
        }
    }

    private inline fun <K, V, R : Any> Map<K, V>.mapValuesNotNull(
        transform: (Map.Entry<K, V>) -> R?
    ): Map<K, R> {
        return mapNotNull { entry -> transform(entry)?.let { entry.key to it } }.toMap()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.toPreferenceValue(): PreferenceValue? {
        return when (this) {
            is Boolean -> PreferenceValue.BooleanValue(this)
            is Float -> PreferenceValue.FloatValue(this)
            is Int -> PreferenceValue.IntValue(this)
            is Long -> PreferenceValue.LongValue(this)
            is String -> PreferenceValue.StringValue(this)
            is Set<*> -> PreferenceValue.StringSetValue(this.filterIsInstance<String>().toSet())
            else -> null
        }
    }

    private fun CheckInRecordEntity.normalizeSideDishes(): CheckInRecordEntity {
        if (sideDishes.isBlank()) return this
        val normalized = sideDishes
            .replace("\\u001E", "\u001E")
            .replace("\\u001e", "\u001E")
            .split(",")
            .joinToString(",") { item ->
                if (item.contains("\u001E") || !item.contains("|")) {
                    item
                } else {
                    val title = item.substringBefore("|")
                    val videoCode = item.substringAfter("|", "")
                    if (videoCode.isBlank()) title else "$title\u001E$videoCode"
                }
            }
        return copy(sideDishes = normalized)
    }
}


