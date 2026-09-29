package com.yenaly.han1meviewer

object FirebaseConstants {

    const val ADV_SEARCH_OPT = "advanced_search_options"

    const val H_KEYFRAMES = "h_keyframes"

    const val LOGIN_STATE = "login_state"

    const val APP_LANGUAGE = "app_language"

    const val VERSION_SOURCE = "version_source"

    const val RUNNING_DOWNLOAD_WORK_COUNT = "running_download_work_count"

    const val ENABLE_CI_UPDATE = "enable_ci_update"

    val remoteConfigDefaults: Map<String, Any> = mapOf(
        ENABLE_CI_UPDATE to true
    )

}
