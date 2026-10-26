package com.yenaly.han1meviewer.OnAir

import com.yenaly.han1meviewer.OnAir.common.OnAirConstants

object OnAirSiteType {

    const val HOSTNAME = "onair"

    fun isOnAirSite(siteTypeName: String?): Boolean =
        siteTypeName.equals("ONAIR", ignoreCase = true)

    fun displayUrl(): String = OnAirConstants.ONAIR_URL[0]

    fun defaultHostname(): String = OnAirConstants.ONAIR_HOSTNAME[0]
}
