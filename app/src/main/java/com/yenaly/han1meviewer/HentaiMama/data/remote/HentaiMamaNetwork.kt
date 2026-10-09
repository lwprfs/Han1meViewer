package com.yenaly.han1meviewer.HentaiMama.data.remote

import com.yenaly.han1meviewer.HentaiMama.cloudflare.HentaiMamaCloudflareInterceptor
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaConstants
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.network.interceptor.UrlLoggingInterceptor
import com.yenaly.han1meviewer.logic.network.interceptor.UserAgentInterceptor
import com.yenaly.yenaly_libs.utils.applicationContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object HentaiMamaNetwork {

    @Volatile
    private var _service: HentaiMamaService? = null

    @Volatile
    private var _baseUrl: String? = null

    @Volatile
    private var _cookieJar: HentaiMamaCookieJar? = null

    @Volatile
    private var _client: OkHttpClient? = null

    val baseUrl: String
        get() = Preferences.hentaiMamaBaseUrl.ifBlank { HentaiMamaConstants.BASE_URL }

    val apiUrl: String
        get() = "$baseUrl/wp-admin/admin-ajax.php"

    val service: HentaiMamaService
        get() {
            ensureService()
            return _service!!
        }

    val sharedOkHttpClient: OkHttpClient
        get() {
            ensureService()
            return _client!!
        }

    @Synchronized
    private fun ensureService() {
        val currentBaseUrl = baseUrl
        if (_service == null || _baseUrl != currentBaseUrl) {
            _baseUrl = currentBaseUrl
            _service = createService(currentBaseUrl)
        }
    }

    fun normalizeUrl(path: String): String {
        return when {
            path.startsWith("http") -> path
            path.startsWith("/") -> baseUrl + path
            else -> "$baseUrl/$path"
        }
    }

    private fun createService(baseUrl: String): HentaiMamaService {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(UserAgentInterceptor)
            .addInterceptor(UrlLoggingInterceptor())
            .cookieJar(getOrCreateCookieJar())
            .addInterceptor(HentaiMamaCloudflareInterceptor(applicationContext))
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Referer", baseUrl)
                    .addHeader(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                    )
                    .addHeader("Accept-Language", "en-US,en;q=0.9")
                    .addHeader("Cache-Control", "no-cache")
                    .build()
                chain.proceed(request)
            }
            .build()

        _client = client

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .build()
            .create(HentaiMamaService::class.java)
    }

    private fun getOrCreateCookieJar(): HentaiMamaCookieJar {
        return _cookieJar ?: synchronized(this) {
            _cookieJar ?: HentaiMamaCookieJar().also { _cookieJar = it }
        }
    }

    fun clearCookies() {
        _cookieJar?.clearCookies()
        _cookieJar = null
    }

    fun rebuildNetwork() {
        _service = null
        _baseUrl = null
        _client = null
    }
}
