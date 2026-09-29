package com.yenaly.han1meviewer.logic.model

data class Playlists(
    val playlists: List<Playlist>,
    val csrfToken: String? = null,
    val maxPage: Int = 1,
) {
    data class Playlist(
        val listCode: String,
        var title: String,
        var total: Int,
        val coverUrl: String? = null
    )
}

data class ModifiedPlaylistArgs(
    var title: String,
    var desc: String,
    var isDeleted: Boolean,
)
