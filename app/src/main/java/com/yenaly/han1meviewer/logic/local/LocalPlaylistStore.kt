package com.yenaly.han1meviewer.logic.local

import android.content.Context
import androidx.core.content.edit
import com.yenaly.han1meviewer.SiteType
import com.yenaly.han1meviewer.util.applicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class LocalPlaylistItem(
    val site: String,
    val videoCode: String,
    val title: String,
    val coverUrl: String? = null,
    val webUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
) {
    val siteType: SiteType? get() = runCatching { SiteType.valueOf(site) }.getOrNull()
    val key: String get() = "$site|$videoCode"
}

@Serializable
data class LocalPlaylist(
    val id: String,
    val name: String,
    val items: List<LocalPlaylistItem> = emptyList(),
)

/**
 * On-device playlists shared by every site. Sites that have an online playlist (Hanime,
 * Javchu, xHamster) keep using it; all other sites use this store.
 */
object LocalPlaylistStore {

    /** Sites whose playlists live on the server. */
    val ONLINE_SITES = setOf(SiteType.HANIME, SiteType.JAVCHU, SiteType.XHAMSTER)

    fun usesLocalPlaylists(site: SiteType): Boolean = site !in ONLINE_SITES

    private const val PREFS = "local_playlists"
    private const val KEY = "playlists_v1"
    private val json = Json { ignoreUnknownKeys = true }
    private val sp by lazy { applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    private val _playlists = MutableStateFlow(load())
    val playlists: StateFlow<List<LocalPlaylist>> = _playlists.asStateFlow()

    private fun load(): List<LocalPlaylist> = runCatching {
        sp.getString(KEY, null)?.let { json.decodeFromString<List<LocalPlaylist>>(it) }
    }.getOrNull() ?: emptyList()

    @Synchronized
    private fun update(transform: (List<LocalPlaylist>) -> List<LocalPlaylist>) {
        val next = transform(_playlists.value)
        sp.edit { putString(KEY, json.encodeToString(next)) }
        _playlists.value = next
    }

    fun create(name: String): String {
        val id = UUID.randomUUID().toString()
        update { it + LocalPlaylist(id, name.trim()) }
        return id
    }

    fun rename(id: String, name: String) =
        update { list -> list.map { if (it.id == id) it.copy(name = name.trim()) else it } }

    fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    fun add(playlistId: String, item: LocalPlaylistItem) = update { list ->
        list.map { p ->
            if (p.id != playlistId || p.items.any { it.key == item.key }) p
            else p.copy(items = listOf(item) + p.items)
        }
    }

    fun remove(playlistId: String, item: LocalPlaylistItem) = update { list ->
        list.map { p ->
            if (p.id == playlistId) p.copy(items = p.items.filterNot { it.key == item.key }) else p
        }
    }

    fun containing(item: LocalPlaylistItem): Set<String> =
        _playlists.value.filter { p -> p.items.any { it.key == item.key } }.map { it.id }.toSet()

    /** Replace the video's membership with exactly [ids]. */
    fun setMembership(item: LocalPlaylistItem, ids: Set<String>) = update { list ->
        list.map { p ->
            val has = p.items.any { it.key == item.key }
            when {
                p.id in ids && !has -> p.copy(items = listOf(item) + p.items)
                p.id !in ids && has -> p.copy(items = p.items.filterNot { it.key == item.key })
                else -> p
            }
        }
    }
}
