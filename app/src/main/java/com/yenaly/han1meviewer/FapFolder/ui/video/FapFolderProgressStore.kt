package com.yenaly.han1meviewer.FapFolder.ui.video

object FapFolderProgressStore {

    private data class Entry(
        val positionMs: Long,
        val durationMs: Long,
        val savedAt: Long,
    )

    private val store = mutableMapOf<String, Entry>()

    @Synchronized
    fun put(videoId: String, positionMs: Long, durationMs: Long) {
        if (videoId.isBlank()) return
        if (positionMs < 0L || durationMs < 0L) return
        if (positionMs == 0L && durationMs == 0L) return
        store[videoId] = Entry(
            positionMs = positionMs,
            durationMs = durationMs,
            savedAt = System.currentTimeMillis(),
        )
    }

    @Synchronized
    fun get(videoId: String): Pair<Long, Long>? {
        if (videoId.isBlank()) return null
        val entry = store[videoId] ?: return null
        if (System.currentTimeMillis() - entry.savedAt > 6 * 60 * 60 * 1000L) {
            store.remove(videoId)
            return null
        }
        return entry.positionMs to entry.durationMs
    }

    @Synchronized
    fun clear(videoId: String) {
        if (videoId.isBlank()) return
        store.remove(videoId)
    }

    @Synchronized
    fun clearAll() {
        store.clear()
    }
}
