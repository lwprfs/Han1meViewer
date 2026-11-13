package com.yenaly.han1meviewer.XHamster.data.remote

import com.yenaly.han1meviewer.XHamster.cloudflare.XHamsterCloudflareInterceptor
import com.yenaly.han1meviewer.XHamster.common.XHamsterConstants
import com.yenaly.han1meviewer.logic.network.HDns
import com.yenaly.han1meviewer.logic.network.HProxySelector
import com.yenaly.han1meviewer.logic.network.interceptor.UrlLoggingInterceptor
import com.yenaly.han1meviewer.logic.network.interceptor.UserAgentInterceptor
import com.yenaly.han1meviewer.util.applicationContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object XHamsterNetwork {

    @Volatile
    private var _service: XHamsterService? = null

    @Volatile
    private var _client: OkHttpClient? = null

    @Volatile
    private var _streamClient: OkHttpClient? = null

    @Volatile
    private var _cookieJar: XHamsterCookieJar? = null

    val baseUrl: String
        get() = XHamsterConstants.BASE_URL

    val service: XHamsterService
        get() {
            ensure()
            return _service!!
        }

    val sharedOkHttpClient: OkHttpClient
        get() {
            ensure()
            return _client!!
        }

    val streamClient: OkHttpClient
        get() {
            ensure()
            return _streamClient!!
        }

    private val cdnHeaders = Interceptor { chain ->
        val request = chain.request()
        val builder = request.newBuilder()
        if (request.header("Referer") == null) builder.header("Referer", XHamsterConstants.REFERER)
        if (request.header("Origin") == null) builder.header("Origin", XHamsterConstants.ORIGIN)
        chain.proceed(builder.build())
    }

    private val siteReferer = Interceptor { chain ->
        val request = chain.request()
        if (request.header("Referer") == null) {
            chain.proceed(request.newBuilder().header("Referer", XHamsterConstants.REFERER).build())
        } else {
            chain.proceed(request)
        }
    }

    @Synchronized
    private fun ensure() {
        if (_service != null && _client != null && _streamClient != null) return
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(UserAgentInterceptor)
            .addInterceptor(siteReferer)
            .addInterceptor(UrlLoggingInterceptor())
            .cookieJar(getOrCreateCookieJar())
            .addInterceptor(XHamsterCloudflareInterceptor(applicationContext))
            .dns(HDns())
            .proxySelector(HProxySelector())
            .build()
        _client = client
        _streamClient = client.newBuilder()
            .readTimeout(45, TimeUnit.SECONDS)
            .also { builder ->
                builder.interceptors().removeAll { it is XHamsterCloudflareInterceptor }
            }
            .addInterceptor(cdnHeaders)
            .build()
        _service = Retrofit.Builder()
            .baseUrl(baseUrl + "/")
            .client(client)
            .build()
            .create(XHamsterService::class.java)
    }

    private fun getOrCreateCookieJar(): XHamsterCookieJar =
        _cookieJar ?: XHamsterCookieJar().also { _cookieJar = it }

    fun hasAuthCookies(): Boolean = XHamsterCookieJar.hasAuthCookies()

    fun clearLoginCookies() {
        XHamsterCookieJar.clearLoginCookies()
    }

    fun clearCookies() {
        _cookieJar?.clearCookies()
        _cookieJar = null
        rebuildNetwork()
    }

    fun rebuildNetwork() {
        _service = null
        _client = null
        _streamClient = null
    }
}
