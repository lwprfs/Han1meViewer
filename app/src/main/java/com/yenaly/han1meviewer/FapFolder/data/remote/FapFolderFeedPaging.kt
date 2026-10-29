package com.yenaly.han1meviewer.FapFolder.data.remote

import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderLoadMoreRequest
import com.yenaly.han1meviewer.FapFolder.data.model.FapFolderSearchTab

object FapFolderFeedPaging {

    fun home(
        offset: Int,
        premfeed: Int = FapFolderConstants.PREMFEED_OFF,
        time: Int = FapFolderConstants.TIME_NO_FILTER,
    ): FapFolderLoadMoreRequest = FapFolderLoadMoreRequest(
        get = FapFolderConstants.FEED_GET_NEWSFEED,
        offset = offset,
        filter = FapFolderConstants.FILTER_ALL,
        id = null,
        type = null,
        uid = null,
        premfeed = premfeed,
        time = time,
        search = null,
        hashtag = null,
    )

    fun profile(
        userId: Long,
        offset: Int,
        filter: String = FapFolderConstants.FILTER_ALL,
        premfeed: Int = FapFolderConstants.PREMFEED_OFF,
        time: Int = FapFolderConstants.TIME_NO_FILTER,
    ): FapFolderLoadMoreRequest = FapFolderLoadMoreRequest(
        get = FapFolderConstants.FEED_GET_POSTS_PROFILE,
        offset = offset,
        filter = filter,
        id = userId.toString(),
        type = null,
        uid = userId.toString(),
        premfeed = premfeed,
        time = time,
        search = null,
        hashtag = null,
    )

    fun group(
        groupId: Long,
        offset: Int,
        filter: String = FapFolderConstants.FILTER_ALL,
        premfeed: Int = FapFolderConstants.PREMFEED_OFF,
        time: Int = FapFolderConstants.TIME_NO_FILTER,
    ): FapFolderLoadMoreRequest = FapFolderLoadMoreRequest(
        get = FapFolderConstants.FEED_GET_POSTS_GROUP,
        offset = offset,
        filter = filter,
        id = groupId.toString(),
        type = null,
        uid = null,
        premfeed = premfeed,
        time = time,
        search = null,
        hashtag = null,
    )

    fun search(
        query: String,
        offset: Int,
        filter: String = FapFolderConstants.FILTER_ALL,
        premfeed: Int = FapFolderConstants.PREMFEED_OFF,
        time: Int = FapFolderConstants.TIME_NO_FILTER,
        hashtag: Boolean = false,
    ): FapFolderLoadMoreRequest {
        val clean = query.trim().removePrefix("#")
        return FapFolderLoadMoreRequest(
            get = FapFolderConstants.FEED_GET_SEARCH,
            offset = offset,
            filter = filter,
            id = null,
            type = null,
            uid = null,
            premfeed = premfeed,
            time = time,
            search = if (hashtag) null else clean,
            hashtag = if (hashtag) clean else null,
        )
    }

    fun fromStream(
        get: String?,
        filter: String?,
        id: String?,
        type: String?,
        uid: String?,
        search: String?,
        hashtag: String?,
        offset: Int,
        premfeed: Int = FapFolderConstants.PREMFEED_OFF,
        time: Int = FapFolderConstants.TIME_NO_FILTER,
    ): FapFolderLoadMoreRequest = FapFolderLoadMoreRequest(
        get = get ?: FapFolderConstants.FEED_GET_NEWSFEED,
        offset = offset,
        filter = filter,
        id = id,
        type = type,
        uid = uid,
        premfeed = premfeed,
        time = time,
        search = search,
        hashtag = hashtag,
    )

    fun tabGet(tab: FapFolderSearchTab): String = when (tab) {
        FapFolderSearchTab.POSTS -> FapFolderConstants.FEED_GET_SEARCH
        FapFolderSearchTab.USERS -> FapFolderConstants.FEED_GET_SEARCH
        FapFolderSearchTab.COMMUNITIES -> FapFolderConstants.FEED_GET_SEARCH
    }
}