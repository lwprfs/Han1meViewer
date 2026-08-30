package com.yenaly.han1meviewer.HentaiMama

import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.network.interceptor.UrlLoggingInterceptor
import com.yenaly.han1meviewer.logic.network.interceptor.UserAgentInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object HentaiMamaNetwork {

    private var _service: HentaiMamaService? = null
    private var _baseUrl: String? = null

    val service: HentaiMamaService
        get() {
            val currentBaseUrl = Preferences.hentaiMamaBaseUrl ?: HentaiMamaConstants.BASE_URL
            if (_service == null || _baseUrl != currentBaseUrl) {
                _baseUrl = currentBaseUrl
                _service = createService(currentBaseUrl)
            }
            return _service!!
        }

    private fun createService(baseUrl: String): HentaiMamaService {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(UserAgentInterceptor)
            .addInterceptor(UrlLoggingInterceptor())
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Referer", baseUrl)
                    .build()
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .build()
            .create(HentaiMamaService::class.java)
    }

    fun rebuildNetwork() {
        _service = null
        _baseUrl = null
    }
}