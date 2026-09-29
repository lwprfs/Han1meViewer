package com.yenaly.han1meviewer.logic.network

import android.util.Log
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.util.CookieString
import com.yenaly.han1meviewer.util.toLoginCookieList
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class HCookieJar : CookieJar {

    companion object {
        @JvmStatic
        val cookieMap: MutableMap<String, MutableList<Cookie>> = mutableMapOf()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = mutableListOf<Cookie>()
        cookieMap[host]?.let { cookies.addAll(it) }

        cookies.addAll(Preferences.loginCookieStateFlow.value.toLoginCookieList(host))
        cookies.addAll(Preferences.cloudFlareCookieStateFlow.value.toLoginCookieList(host))

        Log.d("HCookieJar", "loadForRequest for $host: $cookies")

        return cookies
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookieMap[url.host] = cookies.toMutableList().also {
            it += Preferences.loginCookieStateFlow.value.toLoginCookieList(url.host)
        }
    }
}
