package com.yenaly.han1meviewer.FapFolder.data.local

import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.common.FapFolderUrlUtils
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderSearchHistoryEntry
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderSearchTab

object FapFolderSearchHelper {

    fun load(): List<FapFolderSearchHistoryEntry> = FapFolderSearchHistoryRepo.load()

    fun save(
        rawQuery: String,
        isHashtag: Boolean,
        tab: FapFolderSearchTab,
    ) {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty() && !isHashtag) return
        FapFolderSearchHistoryRepo.push(
            FapFolderSearchHistoryEntry(
                query = if (isHashtag) trimmed.removePrefix("#") else trimmed,
                isHashtag = isHashtag,
                tab = tab.param,
            )
        )
    }

    fun remove(entry: FapFolderSearchHistoryEntry) {
        FapFolderSearchHistoryRepo.remove(entry)
    }

    fun clear() {
        FapFolderSearchHistoryRepo.clear()
    }

    fun detectHashtag(rawQuery: String): Boolean {
        if (rawQuery.isBlank()) return false
        val trimmed = rawQuery.trim()
        return trimmed.startsWith("#") ||
                FapFolderUrlUtils.extractHashtag(trimmed) != null
    }

    fun normaliseHashtag(tag: String): String =
        tag.trim().removePrefix("#").trim()

    fun historyLabel(entry: FapFolderSearchHistoryEntry): String {
        return if (entry.isHashtag) "#${entry.query}" else entry.query
    }

    fun buildUrl(
        rawQuery: String,
        isHashtag: Boolean,
        tab: FapFolderSearchTab,
    ): String {
        val base = FapFolderConstants.BASE_URL.trimEnd('/')
        val path = if (isHashtag) {
            FapFolderUrlUtils.toHashtagUrlPath(rawQuery)
        } else {
            FapFolderUrlUtils.toSearchUrlPath(rawQuery)
        }
        val url = base + path
        return if (tab == FapFolderSearchTab.POSTS) url else "$url?tab=${tab.param}"
    }

    fun buildHashtagUrl(tag: String): String {
        val base = FapFolderConstants.BASE_URL.trimEnd('/')
        return base + FapFolderUrlUtils.toHashtagUrlPath(tag)
    }
}
