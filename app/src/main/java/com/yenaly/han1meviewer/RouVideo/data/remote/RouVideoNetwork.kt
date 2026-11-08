package com.yenaly.han1meviewer.RouVideo.data.remote

import com.yenaly.han1meviewer.RouVideo.cloudflare.RouVideoCloudflareInterceptor
import com.yenaly.han1meviewer.RouVideo.common.RouVideoConstants
import com.yenaly.han1meviewer.logic.network.HDns
import com.yenaly.han1meviewer.logic.network.HProxySelector
import com.yenaly.han1meviewer.logic.network.interceptor.UrlLoggingInterceptor
import com.yenaly.han1meviewer.logic.network.interceptor.UserAgentInterceptor
import com.yenaly.yenaly_libs.utils.applicationContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object RouVideoNetwork {

    @Volatile
    private var _service: RouVideoService? = null

    @Volatile
    private var _client: OkHttpClient? = null

    @Volatile
    private var _streamClient: OkHttpClient? = null

    @Volatile
    private var _cookieJar: RouVideoCookieJar? = null

    val baseUrl: String
        get() = RouVideoConstants.BASE_URL

    val service: RouVideoService
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
        if (request.header("Referer") == null) builder.header("Referer", RouVideoConstants.REFERER)
        if (request.header("Origin") == null) builder.header("Origin", RouVideoConstants.ORIGIN)
        chain.proceed(builder.build())
    }

    private val siteReferer = Interceptor { chain ->
        val request = chain.request()
        if (request.header("Referer") == null) {
            chain.proceed(request.newBuilder().header("Referer", RouVideoConstants.REFERER).build())
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
            .addInterceptor(RouVideoCloudflareInterceptor(applicationContext))
            .dns(HDns())
            .proxySelector(HProxySelector())
            .build()
        _client = client
        _streamClient = client.newBuilder()
            .readTimeout(45, TimeUnit.SECONDS)
            .also { builder ->
                builder.interceptors().removeAll { it is RouVideoCloudflareInterceptor }
            }
            .addInterceptor(cdnHeaders)
            .addInterceptor(RouVideoPngUnwrapInterceptor())
            .build()
        _service = Retrofit.Builder()
            .baseUrl(baseUrl + "/")
            .client(client)
            .build()
            .create(RouVideoService::class.java)
    }

    private fun getOrCreateCookieJar(): RouVideoCookieJar =
        _cookieJar ?: RouVideoCookieJar().also { _cookieJar = it }

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
