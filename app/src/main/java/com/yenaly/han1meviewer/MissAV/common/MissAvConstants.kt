package com.yenaly.han1meviewer.MissAV.common
object MissAvConstants {

    val MISSAV_HOSTNAME = arrayOf("missav.ws", "missav.live", "missav.ai")
    val MISSAV_URL = arrayOf("https://missav.ws/", "https://missav.live/", "https://missav.ai/")
    const val MISSAV_API_URL = "https://client-rapi-missav.recombee.com"
    const val MISSAV_PUBLIC_TOKEN = "Ikkg568nlM51RHvldlPvc2GzZPE9R4XGzaH9Qj4zK9npbbbTly1gj9K4mgRn0QlV"

    fun getPrimaryDomain(): String = MISSAV_HOSTNAME[0]
    fun getPrimaryUrl(): String = MISSAV_URL[0]
}