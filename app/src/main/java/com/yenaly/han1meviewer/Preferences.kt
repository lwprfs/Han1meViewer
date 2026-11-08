package com.yenaly.han1meviewer

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.yenaly.han1meviewer.HanimeConstants.HANIME_URL
import com.yenaly.han1meviewer.logic.network.SiteNetworkPrefs
import com.yenaly.han1meviewer.logic.network.interceptor.SpeedLimitInterceptor
import com.yenaly.han1meviewer.ui.navigation.settings.SettingsPreferenceKeys
import com.yenaly.han1meviewer.ui.view.video.HJzvdStd
import com.yenaly.han1meviewer.ui.view.video.HMediaKernel
import com.yenaly.han1meviewer.util.CookieString
import com.yenaly.han1meviewer.util.SafFileManager
import com.yenaly.han1meviewer.worker.HanimeDownloadManagerV2
import com.yenaly.yenaly_libs.utils.applicationContext
import com.yenaly.yenaly_libs.utils.getSpValue
import com.yenaly.yenaly_libs.utils.putSpValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

enum class SiteType {
    HANIME, MISSAV, HENTAIMAMA, JAVCHU, ONAIR, FAPFOLDER, SUPJAV
}

object MissAvConstants {
    val MISSAV_URL = arrayOf("https://missav.ws", "https://missav.live", "https://missav.ai")
}

object HentaiMamaConstants {
    const val BASE_URL = "https://hentaimama.io"
}

object Preferences {
    private const val USAGE_NOTICE_ACCEPTED = "usage_notice_accepted"

    val preferenceSp: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(
            applicationContext
        )

    var isAlreadyLogin: Boolean
        get() = getSpValue(ALREADY_LOGIN, false)
        set(value) {
            loginStateFlow.value = value
            putSpValue(ALREADY_LOGIN, value)
        }

    val loginStateFlow = MutableStateFlow(isAlreadyLogin)

    var usageNoticeAccepted: Boolean
        get() = preferenceSp.getBoolean(USAGE_NOTICE_ACCEPTED, false)
        set(value) = preferenceSp.edit { putBoolean(USAGE_NOTICE_ACCEPTED, value) }

    val savedUserId: String
        get() = preferenceSp.getString(SAVED_USER_ID,"") ?: ""

    var loginCookie
        get() = CookieString(getSpValue(LOGIN_COOKIE, EMPTY_STRING))
        set(value) {
            loginCookieStateFlow.value = value
            putSpValue(LOGIN_COOKIE, value.cookie)
        }

    val loginCookieStateFlow = MutableStateFlow(loginCookie)

    var cloudFlareCookie
        get() = CookieString(getSpValue(CLOUDFLARE_COOKIE   , EMPTY_STRING))
        set(value) {
            cloudFlareCookieStateFlow.value = value
            putSpValue(CLOUDFLARE_COOKIE, value.cookie)
        }
    val cloudFlareCookieStateFlow = MutableStateFlow(cloudFlareCookie)

    private const val UPDATE_NODE_ID = "update_node_id"

    var updateNodeId: String
        get() = getSpValue(UPDATE_NODE_ID, EMPTY_STRING)
        set(value) = putSpValue(UPDATE_NODE_ID, value)

    var lastUpdatePopupTime
        get() = getSpValue(SettingsPreferenceKeys.LAST_UPDATE_POPUP_TIME, 0L)
        set(value) = putSpValue(SettingsPreferenceKeys.LAST_UPDATE_POPUP_TIME, value)

    val updatePopupIntervalDays
        get() = preferenceSp.getInt(SettingsPreferenceKeys.UPDATE_POPUP_INTERVAL_DAYS, 0)

    val useCIUpdateChannel
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.USE_CI_UPDATE_CHANNEL, false)

    @OptIn(ExperimentalTime::class)
    val isUpdateDialogVisible: Boolean
        get() {
            val now = kotlin.time.Clock.System.now()
            val lastCheckTime = kotlin.time.Instant.fromEpochSeconds(lastUpdatePopupTime)
            val interval = updatePopupIntervalDays
            return now > lastCheckTime + interval.days
        }

    var siteType: SiteType
        get() = try {
            SiteType.valueOf(preferenceSp.getString("site_type", SiteType.HANIME.name) ?: SiteType.HANIME.name)
        } catch (_: IllegalArgumentException) {
            SiteType.HANIME
        }
        set(value) = preferenceSp.edit { putString("site_type", value.name) }

    val missAvBaseUrl: String
        get() = SiteNetworkPrefs.baseUrl(SiteType.MISSAV)

    var missAvLockQuality: Boolean
        get() = preferenceSp.getBoolean("missav_lock_quality", false)
        set(value) = preferenceSp.edit { putBoolean("missav_lock_quality", value) }

    var missAvSubtitleSource: String
        get() = preferenceSp.getString("missav_subtitle_source", "subtitlecat") ?: "subtitlecat"
        set(value) = preferenceSp.edit { putString("missav_subtitle_source", value) }

    var missAvSubtitleCatLanguage: String
        get() = preferenceSp.getString("missav_subtitlecat_language", "en") ?: "en"
        set(value) = preferenceSp.edit { putString("missav_subtitlecat_language", value) }

    var missAvSubtitleCatTranslate: Boolean
        get() = preferenceSp.getBoolean("missav_subtitlecat_translate", true)
        set(value) = preferenceSp.edit { putBoolean("missav_subtitlecat_translate", value) }

    var missAvPreferredQuality: String
        get() = preferenceSp.getString("missav_preferred_quality", "auto") ?: "auto"
        set(value) = preferenceSp.edit { putString("missav_preferred_quality", value) }

    val hentaiMamaBaseUrl: String
        get() = SiteNetworkPrefs.baseUrl(SiteType.HENTAIMAMA)

    val fapFolderBaseUrl: String
        get() = SiteNetworkPrefs.baseUrl(SiteType.FAPFOLDER)

    val supJavBaseUrl: String
        get() = SiteNetworkPrefs.baseUrl(SiteType.SUPJAV)

    val javchuBaseUrl: String
        get() = SiteNetworkPrefs.baseUrl(SiteType.JAVCHU)

    val onAirBaseUrl: String
        get() = SiteNetworkPrefs.baseUrl(SiteType.ONAIR)

    val switchPlayerKernel: String
        get() = preferenceSp.getString(
            SettingsPreferenceKeys.SWITCH_PLAYER_KERNEL,
            HMediaKernel.Type.ExoPlayer.name
        ) ?: HMediaKernel.Type.ExoPlayer.name

    val showBottomProgress: Boolean
        get() = preferenceSp.getBoolean(
            SettingsPreferenceKeys.SHOW_BOTTOM_PROGRESS,
            true
        )

    val playerSpeed: Float
        get() = preferenceSp.getString(
            SettingsPreferenceKeys.PLAYER_SPEED,
            HJzvdStd.DEF_SPEED.toString()
        )?.toFloat() ?: HJzvdStd.DEF_SPEED

    val slideSensitivity: Int
        get() = preferenceSp.getInt(
            SettingsPreferenceKeys.SLIDE_SENSITIVITY,
            HJzvdStd.DEF_PROGRESS_SLIDE_SENSITIVITY
        )

    val longPressSpeedTime: Float
        get() = preferenceSp.getString(
            SettingsPreferenceKeys.LONG_PRESS_SPEED_TIMES,
            HJzvdStd.DEF_LONG_PRESS_SPEED_TIMES.toString()
        )?.toFloat() ?: HJzvdStd.DEF_LONG_PRESS_SPEED_TIMES

    val videoLanguage: String
        get() = preferenceSp.getString(SettingsPreferenceKeys.VIDEO_LANGUAGE, "zhs") ?: "zht"

    val videoQuality: String
        get() = preferenceSp.getString(SettingsPreferenceKeys.DEFAULT_VIDEO_QUALITY, "1080P") ?: "1080P"

    val showPlayedIndicator: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.SHOW_PLAYED_INDICATOR,true)

    val watchedProgressThreshold: Int
        get() = preferenceSp.getInt(SettingsPreferenceKeys.WATCHED_PROGRESS_THRESHOLD, 90)

    val autoPlayNext: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.AUTO_PLAY_NEXT, false)

    val searchPagination: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.SEARCH_PAGINATION, false)

    val searchGridColumnsConfig: SearchGridColumnsConfig
        get() = SearchGridColumnsConfig(
            compactColumns = preferenceSp.getInt(
                SettingsPreferenceKeys.SEARCH_GRID_COLUMNS_COMPACT,
                SearchGridColumnsConfig.DEFAULT_COMPACT_COLUMNS,
            ),
            mediumColumns = preferenceSp.getInt(
                SettingsPreferenceKeys.SEARCH_GRID_COLUMNS_MEDIUM,
                SearchGridColumnsConfig.DEFAULT_MEDIUM_COLUMNS,
            ),
            expandedColumns = preferenceSp.getInt(
                SettingsPreferenceKeys.SEARCH_GRID_COLUMNS_EXPANDED,
                SearchGridColumnsConfig.DEFAULT_EXPANDED_COLUMNS,
            ),
            largeColumns = preferenceSp.getInt(
                SettingsPreferenceKeys.SEARCH_GRID_COLUMNS_LARGE,
                SearchGridColumnsConfig.DEFAULT_LARGE_COLUMNS,
            ),
        )

    val horizontalCardCountConfig: HorizontalCardCountConfig
        get() = HorizontalCardCountConfig(
            narrowCount = preferenceSp.getString(
                SettingsPreferenceKeys.HORIZONTAL_CARD_COUNT_NARROW,
                HorizontalCardCountConfig.DEFAULT_NARROW_COUNT.toString(),
            )?.toFloatOrNull() ?: HorizontalCardCountConfig.DEFAULT_NARROW_COUNT,
            compactCount = preferenceSp.getString(
                SettingsPreferenceKeys.HORIZONTAL_CARD_COUNT_COMPACT,
                HorizontalCardCountConfig.DEFAULT_COMPACT_COUNT.toString(),
            )?.toFloatOrNull() ?: HorizontalCardCountConfig.DEFAULT_COMPACT_COUNT,
            mediumCount = preferenceSp.getString(
                SettingsPreferenceKeys.HORIZONTAL_CARD_COUNT_MEDIUM,
                HorizontalCardCountConfig.DEFAULT_MEDIUM_COUNT.toString(),
            )?.toFloatOrNull() ?: HorizontalCardCountConfig.DEFAULT_MEDIUM_COUNT,
            expandedCount = preferenceSp.getString(
                SettingsPreferenceKeys.HORIZONTAL_CARD_COUNT_EXPANDED,
                HorizontalCardCountConfig.DEFAULT_EXPANDED_COUNT.toString(),
            )?.toFloatOrNull() ?: HorizontalCardCountConfig.DEFAULT_EXPANDED_COUNT,
        )

    val fakeLauncherIcon: String
        get() = preferenceSp.getString(
            SettingsPreferenceKeys.FAKE_LAUNCHER_ICON,
            "com.yenaly.han1meviewer.LauncherAliasDefault") ?: "com.yenaly.han1meviewer.LauncherAliasDefault"

    /** True while the Javchu ("av") flavour of the Hanime client is active. */
    val isAvSite: Boolean
        get() = siteType == SiteType.JAVCHU || baseUrl == HANIME_URL[3]

    val baseUrl: String
        get() = SiteNetworkPrefs.baseUrl(siteType)

    val homeUrl: String
        get() = SiteNetworkPrefs.homeUrl(siteType)

    val useCustomMirrorSite: Boolean
        get() = SiteNetworkPrefs.useCustomMirror(siteType)

    val customMirrorSite: String
        get() = SiteNetworkPrefs.customMirror(siteType)

    val appendCustomMirrorPath: Boolean
        get() = SiteNetworkPrefs.appendCustomPath(siteType)

    val displayUrl: String
        get() = SiteNetworkPrefs.displayUrl(siteType)

    val selectedBaseUrl: String
        get() = preferenceSp.getString(SettingsPreferenceKeys.SELECTED_BASE_URL, HANIME_URL[0])
            ?: HANIME_URL[0]

    val useBuiltInHosts: Boolean
        get() = SiteNetworkPrefs.useBuiltInHosts(siteType)

    val useBackupMediaCdn: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.USE_BACKUP_MEDIA_CDN, false)

    val customHostsData: String
        get() = SiteNetworkPrefs.customHostsData(siteType)

    val useDoH: Boolean
        get() = SiteNetworkPrefs.useDoH(siteType)

    val dohPreset: String
        get() = SiteNetworkPrefs.dohPreset(siteType)

    val dohCustomUrl: String
        get() = SiteNetworkPrefs.dohCustomUrl(siteType)

    val dohBootstrapIps: String
        get() = SiteNetworkPrefs.dohBootstrapIps(siteType)

    val dohTimeoutSeconds: Int
        get() = SiteNetworkPrefs.dohTimeoutSeconds(siteType)

    val whenCountdownRemind: Int
        get() = preferenceSp.getInt(
            SettingsPreferenceKeys.WHEN_COUNTDOWN_REMIND,
            HJzvdStd.DEF_COUNTDOWN_SEC
        ) * 1_000

    val showCommentWhenCountdown: Boolean
        get() = preferenceSp.getBoolean(
            SettingsPreferenceKeys.SHOW_COMMENT_WHEN_COUNTDOWN,
            false
        )

    val hKeyframesEnable: Boolean
        get() = preferenceSp.getBoolean(
            SettingsPreferenceKeys.H_KEYFRAMES_ENABLE,
            true
        )

    val sharedHKeyframesEnable: Boolean
        get() = preferenceSp.getBoolean(
            SettingsPreferenceKeys.SHARED_H_KEYFRAMES_ENABLE,
            true
        )

    val sharedHKeyframesUseFirst: Boolean
        get() = preferenceSp.getBoolean(
            SettingsPreferenceKeys.SHARED_H_KEYFRAMES_USE_FIRST,
            false
        )

    val proxyType: Int
        get() = SiteNetworkPrefs.proxyType(siteType)

    val proxyIp: String
        get() = SiteNetworkPrefs.proxyIp(siteType)

    val proxyPort: Int
        get() = SiteNetworkPrefs.proxyPort(siteType)

    val isAnalyticsEnabled: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.USE_ANALYTICS, true)

    val downloadCountLimit: Int
        get() = preferenceSp.getInt(
            SettingsPreferenceKeys.DOWNLOAD_COUNT_LIMIT,
            HanimeDownloadManagerV2.MAX_CONCURRENT_DOWNLOAD_DEF
        )

    val collapseDownloadedGroup: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.COLLAPSE_DOWNLOADED_GROUP,false)

    var downloadAutoGroup: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.DOWNLOAD_AUTO_GROUP, true)
        set(value) = preferenceSp.edit {
            putBoolean(SettingsPreferenceKeys.DOWNLOAD_AUTO_GROUP, value)
        }

    var downloadGroupNameFromSeriesName: Boolean
        get() = preferenceSp.getBoolean(
            SettingsPreferenceKeys.DOWNLOAD_GROUP_NAME_FROM_SERIES_NAME,
            false,
        )
        set(value) = preferenceSp.edit {
            putBoolean(SettingsPreferenceKeys.DOWNLOAD_GROUP_NAME_FROM_SERIES_NAME, value)
        }

    var downloadGroupNameTraditional: Boolean
        get() = preferenceSp.getBoolean(
            SettingsPreferenceKeys.DOWNLOAD_GROUP_TRADITIONAL,
            videoLanguage == "zht",
        )
        set(value) = preferenceSp.edit {
            putBoolean(SettingsPreferenceKeys.DOWNLOAD_GROUP_TRADITIONAL, value)
        }
    val isUsePrivateStorage: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.USE_PRIVATE_STORAGE,true)
    val safDownloadPath: String?
        get() = preferenceSp.getString(SafFileManager.KEY_TREE_URI,null)

    val useDarkMode: String
        get() = preferenceSp.getString(SettingsPreferenceKeys.USE_DARK_MODE,"always_off") ?: "always_off"
    val useDynamicColor: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.USE_DYNAMIC_COLOR,false)
    val themeColor: String?
        get() = preferenceSp.getString(SettingsPreferenceKeys.THEME_COLOR, null)
    val pureBlackDarkMode: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.PURE_BLACK_DARK_MODE, false)
    val allowResumePlayback: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.ALLOW_RESUME_PLAYBACK,true)

    val searchArtistIgnoreVideoType: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.SEARCH_ARTIST_IGNORE_VIDEO_TYPE,false)

    val disableMobileDataWarning: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.DISABLE_MOBILE_DATA_WARNING,false)

    val disablePredictiveBack: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.DISABLE_PREDICTIVE_BACK, false)

    val tabletMode: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.TABLET_MODE, false)

    val showExitConfirm: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.SHOW_EXIT_CONFIRM, true)

    val mpvProfile: String
        get() = preferenceSp.getString(SettingsPreferenceKeys.MPV_PROFILE, "fast") ?: "fast"

    val enableGPUNextRenderer: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.ENABLE_GPU_NEXT_RENDERER, false)

    val mpvInterpolation: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.MPV_INTERPOLATION, false)

    val mpvDeband: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.MPV_DEBAND, true)

    val mpvFramedrop: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.MPV_FRAMEDROP, true)

    val mpvHwdec: String
        get() = preferenceSp.getString(SettingsPreferenceKeys.MPV_HWDEC, "Auto")?: "Auto"

    val mpvCacheSecs: Int
        get() = preferenceSp.getInt(SettingsPreferenceKeys.MPV_CACHE_SECS, 60)

    val mpvTlsVerify: Boolean
        get() = preferenceSp.getBoolean(SettingsPreferenceKeys.MPV_TLS_VERIFY, true)

    val mpvNetworkTimeout: Int
        get() = preferenceSp.getInt(SettingsPreferenceKeys.MPV_NETWORK_TIMEOUT, 10)

    val customMpvParams: String
        get() = preferenceSp.getString(SettingsPreferenceKeys.CUSTOM_PARAMS,"")?: ""

    val downloadSpeedLimit: Long
        get() {
            val index = preferenceSp.getInt(
                SettingsPreferenceKeys.DOWNLOAD_SPEED_LIMIT,
                SpeedLimitInterceptor.NO_LIMIT_INDEX
            )
            return SpeedLimitInterceptor.SPEED_BYTES[index]
        }

    val missAvUuid: String
        get() {
            var uuid = preferenceSp.getString("missav_uuid", null)
            if (uuid == null) {
                uuid = java.util.UUID.randomUUID().toString()
                preferenceSp.edit { putString("missav_uuid", uuid) }
            }
            return uuid
        }
}
