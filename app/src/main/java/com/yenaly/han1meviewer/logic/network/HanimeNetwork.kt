// app/src/main/java/com/yenaly/han1meviewer/logic/network/HanimeNetwork.kt
package com.yenaly.han1meviewer.logic.network

import com.yenaly.han1meviewer.GETCHU_BASE_URL
import com.yenaly.han1meviewer.HANIME_BASE_URL
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.network.service.HGitHubService
import com.yenaly.han1meviewer.logic.network.service.GetchuService
import com.yenaly.han1meviewer.logic.network.service.HanimeBaseService
import com.yenaly.han1meviewer.logic.network.service.HanimeCommentService
import com.yenaly.han1meviewer.logic.network.service.HanimeMyListService
import com.yenaly.han1meviewer.logic.network.service.HanimeSubscriptionService
import android.util.Log

/**
 * @project Hanime1
 * @author Yenaly Liew
 * @time 2022/06/08 008 22:35
 */
object HanimeNetwork {
    var hanimeService = _hanimeService
        private set
    var githubService = _githubService
        private set
    var getchuService = _getchuService
        private set
    var commentService = _commentService
        private set
    var myListService = _myListService
        private set
    var subscriptionService = _subscriptionService
        private set

    private val _hanimeService
        get() = ServiceCreator.create<HanimeBaseService>(Preferences.baseUrl)  // ← Uses current baseUrl

    private val _githubService
        get() = ServiceCreator.createGitHubApi<HGitHubService>()

    private val _getchuService
        get() = ServiceCreator.createGetchu<GetchuService>(GETCHU_BASE_URL)

    private val _commentService
        get() = ServiceCreator.create<HanimeCommentService>(Preferences.baseUrl)  // ← Uses current baseUrl

    private val _myListService
        get() = ServiceCreator.create<HanimeMyListService>(Preferences.baseUrl)  // ← Uses current baseUrl

    private val _subscriptionService
        get() = ServiceCreator.create<HanimeSubscriptionService>(Preferences.baseUrl)  // ← Uses current baseUrl

    fun rebuildNetwork() {
        Log.d("HanimeNetwork", "Rebuilding network with baseUrl: ${Preferences.baseUrl}")
        ServiceCreator.rebuildOkHttpClient()
        // Recreate all services with the current base URL
        hanimeService = _hanimeService
        getchuService = _getchuService
        commentService = _commentService
        myListService = _myListService
        subscriptionService = _subscriptionService
        Log.d("HanimeNetwork", "Network rebuilt successfully")
    }
}