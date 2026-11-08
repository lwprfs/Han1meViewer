package com.yenaly.han1meviewer.MissAV.common
object MissAvConstants {

    /** Default mirrors plus whatever mirror / custom mirror is selected in the network settings. */
    val MISSAV_HOSTNAME: Array<String>
        get() = com.yenaly.han1meviewer.logic.network.SiteNetworkPrefs.knownHosts(
            com.yenaly.han1meviewer.SiteType.MISSAV,
            listOf("missav.ws", "missav.live", "missav.ai"),
        )
    val MISSAV_URL = arrayOf("https://missav.ws/", "https://missav.live/", "https://missav.ai/")
    const val MISSAV_API_URL = "https://client-rapi-missav.recombee.com"
    const val MISSAV_PUBLIC_TOKEN = "Ikkg568nlM51RHvldlPvc2GzZPE9R4XGzaH9Qj4zK9npbbbTly1gj9K4mgRn0QlV"

    fun getPrimaryDomain(): String =
        com.yenaly.han1meviewer.Preferences.missAvBaseUrl.removePrefix("https://").trimEnd('/')
    fun getPrimaryUrl(): String = com.yenaly.han1meviewer.Preferences.missAvBaseUrl
}
