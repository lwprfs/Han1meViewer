package com.yenaly.han1meviewer.FapFolder.ui.video

object FapFolderVideoPlaylist {

    @Volatile
    private var ids: List<String> = emptyList()

    fun set(items: List<String>) {
        ids = items.distinct()
    }

    fun previous(current: String): String? = ids.indexOf(current).takeIf { it > 0 }?.let { ids[it - 1] }

    fun next(current: String): String? =
        ids.indexOf(current).takeIf { it >= 0 && it < ids.lastIndex }?.let { ids[it + 1] }
}
