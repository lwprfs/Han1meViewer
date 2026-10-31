package com.yenaly.han1meviewer.FapFolder.common

import com.yenaly.han1meviewer.USER_AGENT

object FapFolderConstants {

    const val BASE_URL = "https://fapfolder.club/"
    const val SITE_HOST = "fapfolder.club"
    const val AJAX_BASE = "https://fapfolder.club/includes/ajax/"
    const val UPLOADS_BASE = "https://fap.onl/uploads/"
    const val UPLOADS_HOST = "fap.onl"
    const val SIGNIN_PATH = "signin"
    const val SIGNOUT_PATH = "signout"

    const val ENDPOINT_SIGNIN = "includes/ajax/core/signin.php"
    const val ENDPOINT_SEARCH = "includes/ajax/data/search.php"
    const val ENDPOINT_LOAD_MORE = "includes/ajax/data/load.php"
    const val ENDPOINT_LIGHTBOX = "includes/ajax/posts/lightbox.php"
    const val ENDPOINT_REACTION = "includes/ajax/posts/reaction.php"
    const val ENDPOINT_FILTER = "includes/ajax/posts/filter.php"
    const val ENDPOINT_COMMENT = "includes/ajax/posts/comment.php"
    const val ENDPOINT_CONNECT = "includes/ajax/users/connect.php"
    const val ENDPOINT_LIVE = "includes/ajax/data/live.php"

    const val ENDPOINT_FEED = "includes/ajax/data/load.php"
    const val ENDPOINT_GROUP_ACTIONS = "includes/ajax/users/connect.php"
    const val ENDPOINT_FOLLOW = "includes/ajax/users/connect.php"

    const val COOKIE_PHPSESSID = "PHPSESSID"
    const val COOKIE_C_USER = "c_user"
    const val COOKIE_XS = "xs"

    const val PREF_FAPFOLDER_COOKIES_RAW = "fapfolder_cookies_raw"
    const val PREF_FAPFOLDER_SEARCH_HISTORY = "fapfolder_search_history"

    const val FEED_GET_NEWSFEED = "newsfeed"
    const val FEED_GET_SEARCH = "search"
    const val FEED_GET_POSTS_PROFILE = "posts_profile"
    const val FEED_GET_POSTS_GROUP = "posts_group"
    const val FEED_GET_POPULAR = "popular"
    const val FEED_GET_DISCOVER = "discover"
    const val FEED_GET_PHOTOS_TAB = "photos"
    const val FEED_GET_VIDEOS_TAB = "videos"

    const val PATH_HOME = "/"
    const val PATH_HOT = "/popular"
    const val PATH_DISCOVER = "/discover"

    const val FEED_GET_SAVED = "saved"
    const val PATH_SAVED = "/saved"

    const val HOME_TAB_MY_FEED = "my_feed"
    const val HOME_TAB_HOT = "hot"
    const val HOME_TAB_DISCOVER = "discover"

    const val RANGE_ALL_TIME = 0
    const val RANGE_MONTH = 30
    const val RANGE_WEEK = 7

    const val FILTER_ALL = "all"
    const val FILTER_PHOTOS = "photos"
    const val FILTER_VIDEOS = "videos"

    const val TAB_POSTS = "posts"
    const val TAB_USERS = "users"
    const val TAB_COMMUNITIES = "communities"

    const val CONTEXT_ALBUM = "album"
    const val CONTEXT_PHOTOS = "photos"
    const val CONTEXT_POST = "post"

    const val ACTION_REACT_POST = "react_post"
    const val ACTION_UNREACT_POST = "unreact_post"
    const val ACTION_SAVE_POST = "save_post"
    const val ACTION_UNSAVE_POST = "unsave_post"
    const val ACTION_HIDE_POST = "hide_post"
    const val ACTION_UNHIDE_POST = "unhide_post"
    const val ACTION_DELETE_POST = "delete_post"
    const val ACTION_REACT_PHOTO = "react_photo"
    const val ACTION_UNREACT_PHOTO = "unreact_photo"
    const val ACTION_ADD_VOTE = "add_vote"
    const val ACTION_CHANGE_VOTE = "change_vote"
    const val ACTION_DELETE_VOTE = "delete_vote"

    const val REACTION_LIKE = "like"
    const val REACTION_ANGRY = "angry"

    const val GROUP_ACTION_JOIN = "group-join"
    const val GROUP_ACTION_LEAVE = "group-leave"
    const val GROUP_ACTION_ACCEPT = "group-accept"
    const val GROUP_ACTION_DECLINE = "group-decline"
    const val GROUP_ACTION_MEMBER_REMOVE = "group-member-remove"
    const val GROUP_ACTION_ADMIN_ADD = "group-admin-addation"
    const val GROUP_ACTION_ADMIN_REMOVE = "group-admin-remove"
    const val GROUP_ACTION_INVITE = "group-invite"

    const val FOLLOW_ACTION_FOLLOW = "follow"
    const val FOLLOW_ACTION_UNFOLLOW = "unfollow"
    const val FOLLOW_ACTION_BLOCK = "block"
    const val FOLLOW_ACTION_UNBLOCK = "unblock"

    const val HEADER_ORIGIN = "https://fapfolder.club"
    const val HEADER_REFERER = "https://fapfolder.club/"
    const val HEADER_REFERER_SIGNIN = "https://fapfolder.club/signin"
    const val AJAX_REQUESTED_WITH = "XMLHttpRequest"

    const val ACCEPT_PAGE =
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/jxl," +
                "image/avif,image/webp,image/apng,*/*;q=0.8," +
                "application/signed-exchange;v=b3;q=0.7"
    const val ACCEPT_AJAX = "application/json, text/javascript, */*; q=0.01"
    const val ACCEPT_ENCODING = "gzip, deflate, br, zstd"
    const val ACCEPT_LANGUAGE = "en-GB,en-US;q=0.9,en;q=0.8"

    const val SEC_CH_UA =
        "\"Chromium\";v=\"141\", \"Not(A:Brand\";v=\"24\", \"Android WebView\";v=\"141\""
    const val SEC_CH_UA_MOBILE = "?1"
    const val SEC_CH_UA_PLATFORM = "\"Android\""

    const val CONTENT_TYPE_FORM = "application/x-www-form-urlencoded; charset=UTF-8"

    val USER_AGENT_FAPFOLDER: String = USER_AGENT

    val HASHTAG_REGEX = Regex("#(\\w+)")

    const val MIN_GAP_BETWEEN_PAGES_MS = 500L
    const val MIN_GAP_BETWEEN_LIGHTBOX_MS = 300L
    const val MIN_GAP_BETWEEN_PAGE_GETS_MS = 500L
    const val MAX_PAGES_PER_QUERY = 50
    const val BACKOFF_403_MS = 60_000L
    const val CONNECT_TIMEOUT_S = 8L
    const val READ_TIMEOUT_S = 12L
    const val CALL_TIMEOUT_S = 15L

    const val TIME_NO_FILTER = -1
    const val PREMFEED_OFF = 0
    const val PREMFEED_ON = 1

    val AD_HOSTS_TO_SKIP = setOf(
        "vbuidencestures.com",
        "golovehub.com",
        "golove.ai",
        "undress.cc",
        "theporndude.com",
        "a.adtng.com",
    )

    val VIDEO_EXTENSIONS = setOf("mp4", "webm", "m4a", "m4v", "mpeg")
    val PHOTO_EXTENSIONS = setOf("webp", "jpg", "jpeg", "png")
}
