package com.yenaly.han1meviewer.FapFolder.data.local

import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderSessionContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FapFolderSessionCache {

    private val _sessionFlow = MutableStateFlow<FapFolderSessionContext?>(null)
    val sessionFlow: StateFlow<FapFolderSessionContext?> = _sessionFlow.asStateFlow()

    @Volatile
    private var cachedSession: FapFolderSessionContext? = null

    @Volatile
    private var lastUpdatedAt: Long = 0L

    fun current(): FapFolderSessionContext? = cachedSession

    fun update(session: FapFolderSessionContext?) {
        if (session == null) {
            clear()
            return
        }
        cachedSession = session
        lastUpdatedAt = System.currentTimeMillis()
        _sessionFlow.value = session
    }

    fun updatePartial(
        loggedIn: Boolean? = null,
        viewerUserId: Long? = null,
        viewerUsername: String? = null,
        viewerAvatarUrl: String? = null,
        points: String? = null,
        wallet: String? = null,
    ) {
        val base = cachedSession ?: FapFolderSessionContext.empty()
        val viewer = base.viewer.copy(
            userId = viewerUserId ?: base.viewer.userId,
            username = viewerUsername ?: base.viewer.username,
            avatarUrl = viewerAvatarUrl ?: base.viewer.avatarUrl,
            points = points ?: base.viewer.points,
            wallet = wallet ?: base.viewer.wallet,
            isLoggedIn = loggedIn ?: base.viewer.isLoggedIn,
        )
        update(base.copy(loggedIn = loggedIn ?: base.loggedIn, viewer = viewer))
    }

    fun clear() {
        cachedSession = null
        lastUpdatedAt = 0L
        _sessionFlow.value = null
    }

    fun uploadsBase(): String =
        cachedSession?.uploadsPath?.trimEnd('/')?.takeIf { it.isNotBlank() }
            ?: "https://fap.onl/uploads"

    fun ajaxBase(): String =
        cachedSession?.ajaxPath?.trimEnd('/')?.takeIf { it.isNotBlank() }
            ?: "https://fapfolder.club/includes/ajax"

    fun siteBase(): String =
        cachedSession?.sitePath?.trimEnd('/')?.takeIf { it.isNotBlank() }
            ?: "https://fapfolder.club"

    fun isLoggedIn(): Boolean = cachedSession?.loggedIn == true

    fun viewerUserId(): Long? = cachedSession?.viewer?.userId

    fun viewerUsername(): String? = cachedSession?.viewer?.username

    fun viewerAvatarUrl(): String? = cachedSession?.viewer?.avatarUrl

    fun secret(): String? = cachedSession?.secret

    fun lastUpdated(): Long = lastUpdatedAt
}
