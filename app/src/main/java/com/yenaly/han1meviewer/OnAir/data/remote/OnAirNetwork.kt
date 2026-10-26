package com.yenaly.han1meviewer.OnAir.data.remote

import android.content.Context
import com.yenaly.han1meviewer.OnAir.common.OnAirSitePreferences
import com.yenaly.han1meviewer.logic.network.HDns
import com.yenaly.han1meviewer.logic.network.HProxySelector
import com.yenaly.han1meviewer.logic.network.interceptor.UrlLoggingInterceptor
import com.yenaly.han1meviewer.logic.network.interceptor.UserAgentInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object OnAirNetwork {

    @Volatile
    private var _service: OnAirService? = null
    @Volatile
    private var _baseUrl: String? = null
    @Volatile
    private var _client: OkHttpClient? = null
    @Volatile
    private var _cookieJar: OnAirCookieJar? = null
    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var isInitialized = false

    @Volatile
    private var lastSavedCookieSnapshot: String? = null

    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            appContext = context.applicationContext
            isInitialized = true
            android.util.Log.d("OnAirNetwork", "Initialized")
        }
    }

    private val context: Context
        get() {
            if (!isInitialized) {
                throw IllegalStateException("OnAirNetwork not initialized.")
            }
            return appContext ?: throw IllegalStateException("OnAirNetwork context is null")
        }

    val baseUrl: String
        get() = OnAirSitePreferences.selectedBaseUrl

    val service: OnAirService
        get() {
            if (!isInitialized) throw IllegalStateException("OnAirNetwork not initialized.")
            val current = baseUrl
            if (_service == null || _baseUrl != current) {
                _baseUrl = current
                _service = createService(current)
            }
            return _service!!
        }

    val sharedOkHttpClient: OkHttpClient
        get() {
            if (_client == null) service
            return _client!!
        }

    private fun createService(base: String): OnAirService {
        val client = createOkHttpClient(base)
        _client = client
        return Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .build()
            .create(OnAirService::class.java)
    }

    private fun createOkHttpClient(base: String): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(UserAgentInterceptor)
            .addInterceptor(UrlLoggingInterceptor())
            .addInterceptor { chain ->
                val request = chain.request()
                val isNavigation = request.method == "GET" &&
                        (request.header("Accept")?.contains("text/html") == true)
                val builder = request.newBuilder()
                    .addHeader("Origin", base.trimEnd('/'))
                    .addHeader("Referer", base)
                    .addHeader(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9," +
                                "image/avif,image/webp,image/apng,*/*;q=0.8",
                    )
                    .addHeader("Accept-Language", "en-US,en;q=0.9")
                    .addHeader("Upgrade-Insecure-Requests", "1")
                    .addHeader("Sec-Fetch-Dest", if (isNavigation) "document" else "empty")
                    .addHeader("Sec-Fetch-Mode", if (isNavigation) "navigate" else "cors")
                    .addHeader("Sec-Fetch-Site", "same-origin")
                    .addHeader("Cache-Control", "no-cache")
                    .build()
                chain.proceed(builder)
            }
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                val url = response.request.url.toString()
                val code = response.code
                val body = runCatching {
                    if (code in 200..299) response.peekBody(128 * 1024).string() else null
                }.getOrNull()
                runCatching { persistJarIfChanged(url, code, body) }
                response
            }
            .cookieJar(getOrCreateCookieJar())
            .dns(HDns())
            .proxySelector(HProxySelector())
            .build()
    }

    private fun getOrCreateCookieJar(): OnAirCookieJar {
        return _cookieJar ?: synchronized(this) {
            _cookieJar ?: OnAirCookieJar().also {
                val raw = OnAirCookieManager.cookiesRaw
                it.seed(OnAirCookieManager.parseToCookies(raw, baseUrl))
                lastSavedCookieSnapshot = raw
                _cookieJar = it
            }
        }
    }

    private fun persistJarIfChanged(url: String, code: Int, body: String?) {
        if (url.contains("/login", ignoreCase = true)) return
        if (code in 300..399) return
        if (body != null) {
            if (body.contains("<title>Log in", ignoreCase = true) ||
                body.contains("<title>Login", ignoreCase = true)
            ) return
            if (body.contains("action=\"/login\"", ignoreCase = true) &&
                !body.contains("nav-username", ignoreCase = true)
            ) return
        }
        val jar = _cookieJar ?: return
        val snapshot = jar.snapshotJson()
        if (snapshot.isBlank()) return
        if (snapshot == lastSavedCookieSnapshot) return
        lastSavedCookieSnapshot = snapshot
        OnAirCookieManager.persistOnly(snapshot)
        android.util.Log.d("OnAirNetwork", "Persisted cookie jar (${snapshot.length} chars)")
    }

    fun refreshCookieJar() {
        val raw = OnAirCookieManager.cookiesRaw
        val parsed = OnAirCookieManager.parseToCookies(raw, baseUrl)
        if (parsed.isEmpty()) {
            android.util.Log.w(
                "OnAirNetwork",
                "refreshCookieJar: parse yielded 0 cookies — keeping existing jar",
            )
            return
        }
        val jar = getOrCreateCookieJar()
        jar.clearCookies()
        jar.seed(parsed)
        lastSavedCookieSnapshot = raw
        android.util.Log.d(
            "OnAirNetwork",
            "refreshCookieJar: seeded ${parsed.size} cookies names=" +
                    parsed.map { "${it.name}@${it.path}" },
        )
    }

    fun snapshotCookies(): String {
        val jar = getOrCreateCookieJar()
        return jar.snapshotJson()
    }

    fun normalizeUrl(path: String): String {
        return when {
            path.startsWith("http://") || path.startsWith("https://") -> path
            path.startsWith("//") -> "http:$path"
            path.startsWith("/") -> baseUrl.trimEnd('/') + path
            else -> baseUrl.trimEnd('/') + "/" + path
        }
    }

    fun clearCookies() {
        _cookieJar?.clearCookies()
        _cookieJar = null
        lastSavedCookieSnapshot = ""
    }

    fun rebuildNetwork() {
        _service = null
        _baseUrl = null
        _client = null
        _cookieJar?.let {
            it.clearCookies()
            val raw = OnAirCookieManager.cookiesRaw
            val parsed = OnAirCookieManager.parseToCookies(raw, baseUrl)
            if (parsed.isNotEmpty()) {
                it.seed(parsed)
                lastSavedCookieSnapshot = raw
            }
        }
    }
}
