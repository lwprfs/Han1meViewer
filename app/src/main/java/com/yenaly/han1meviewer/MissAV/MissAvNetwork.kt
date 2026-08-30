package com.yenaly.han1meviewer.MissAV

import android.content.Context
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.network.HDns
import com.yenaly.han1meviewer.logic.network.HProxySelector
import com.yenaly.han1meviewer.logic.network.interceptor.UrlLoggingInterceptor
import com.yenaly.han1meviewer.logic.network.interceptor.UserAgentInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object MissAvNetwork {

    private var _missAvService: MissAvService? = null
    private var _baseUrl: String? = null
    private var _okHttpClient: OkHttpClient? = null

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val context: Context
        get() = appContext ?: throw IllegalStateException("MissAvNetwork not initialized")

    val missAvService: MissAvService
        get() {
            val currentBaseUrl = Preferences.missAvBaseUrl
            if (_missAvService == null || _baseUrl != currentBaseUrl) {
                _baseUrl = currentBaseUrl
                _missAvService = createService(currentBaseUrl)
            }
            return _missAvService!!
        }

    private fun createService(baseUrl: String): MissAvService {
        val client = createOkHttpClient(baseUrl)
        _okHttpClient = client

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .build()
            .create(MissAvService::class.java)
    }

    private fun createOkHttpClient(baseUrl: String): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(UserAgentInterceptor)
            .addInterceptor(UrlLoggingInterceptor())
            .addInterceptor(MissAvCloudflareInterceptor(context))
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Origin", baseUrl)
                    .addHeader("Referer", baseUrl)
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .addHeader("Accept-Language", "en-US,en;q=0.9")
                    .addHeader("Cache-Control", "no-cache")
                    .build()
                chain.proceed(request)
            }
            .cookieJar(MissAvCookieJar())
            .dns(HDns())
            .proxySelector(HProxySelector())
            .build()
    }

    fun rebuildNetwork() {
        _missAvService = null
        _baseUrl = null
        _okHttpClient = null
    }
}