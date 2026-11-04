package com.yenaly.han1meviewer.FapFolder.data.remote

import android.content.Context
import android.util.Log
import com.yenaly.han1meviewer.BuildConfig
import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.common.FapFolderHeaders
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.network.HDns
import com.yenaly.han1meviewer.logic.network.HProxySelector
import com.yenaly.han1meviewer.logic.network.interceptor.UrlLoggingInterceptor
import com.yenaly.yenaly_libs.utils.applicationContext
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.TimeUnit

object FapFolderNetwork {

    private const val TAG = "FapFolderNetwork"

    private val RETRYABLE_POST_PATHS = listOf(
        "/includes/ajax/data/load.php",
        "/includes/ajax/posts/lightbox.php",
        "/includes/ajax/data/search.php",
        "/includes/ajax/posts/filter.php",
        "/includes/ajax/data/live.php",
    )

    @Volatile
    private var _service: FapFolderService? = null

    @Volatile
    private var _client: OkHttpClient? = null

    @Volatile
    private var _playbackClient: OkHttpClient? = null

    @Volatile
    private var _cookieJar: FapFolderCookieJar? = null

    @Volatile
    private var isInitialized = false

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            appContext = context.applicationContext
            val jar = getOrCreateCookieJar()
            runCatching { jar.restoreFromPreferences() }
                .onFailure { Log.e(TAG, "init: cookie restore failed", it) }
            isInitialized = true
            Log.d(
                TAG,
                "init: cookieJar restored, hasAuth=${FapFolderCookieJar.hasRequiredAuthCookie()}",
            )
        }
    }

    private fun ensureInitialized() {
        if (isInitialized) return
        init(applicationContext)
    }

    private val context: Context
        get() {
            ensureInitialized()
            return appContext ?: throw IllegalStateException("FapFolderNetwork context is null")
        }

    val baseUrl: String
        get() = FapFolderConstants.BASE_URL

    val ajaxBase: String
        get() = FapFolderConstants.AJAX_BASE

    val uploadsBase: String
        get() = FapFolderConstants.UPLOADS_BASE

    val cookieJar: FapFolderCookieJar
        get() {
            ensureInitialized()
            return getOrCreateCookieJar()
        }

    val service: FapFolderService
        get() {
            ensureInitialized()
            val current = _service
            if (current != null) return current
            synchronized(this) {
                val again = _service
                if (again != null) return again
                val client = _client ?: buildClient().also { _client = it }
                val created = buildService(client)
                _service = created
                return created
            }
        }

    val sharedOkHttpClient: OkHttpClient
        get() {
            ensureInitialized()
            val current = _client
            if (current != null) return current
            synchronized(this) {
                val again = _client
                if (again != null) return again
                val built = buildClient()
                _client = built
                return built
            }
        }

    val playbackOkHttpClient: OkHttpClient
        get() {
            ensureInitialized()
            val current = _playbackClient
            if (current != null) return current
            synchronized(this) {
                val again = _playbackClient
                if (again != null) return again
                val base = _client ?: buildClient().also { _client = it }
                val built = base.newBuilder()
                    .callTimeout(0, TimeUnit.SECONDS)
                    .readTimeout(FapFolderConstants.PLAYBACK_READ_TIMEOUT_S, TimeUnit.SECONDS)
                    .build()
                _playbackClient = built
                return built
            }
        }

    private fun getOrCreateCookieJar(): FapFolderCookieJar {
        val current = _cookieJar
        if (current != null) return current
        synchronized(this) {
            val again = _cookieJar
            if (again != null) return again
            val jar = FapFolderCookieJar()
            _cookieJar = jar
            return jar
        }
    }

    private fun buildService(client: OkHttpClient): FapFolderService {
        return Retrofit.Builder()
            .baseUrl(FapFolderConstants.BASE_URL)
            .client(client)
            .build()
            .create(FapFolderService::class.java)
    }

    private fun isFirstPartyHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val h = host.lowercase()
        return h == FapFolderConstants.SITE_HOST || h.endsWith(".${FapFolderConstants.SITE_HOST}")
    }

    private fun isSafeToRetry(request: Request): Boolean {
        return when (request.method) {
            "GET", "HEAD" -> true
            "POST" -> {
                val path = request.url.encodedPath
                RETRYABLE_POST_PATHS.any { path.endsWith(it) }
            }
            else -> false
        }
    }

    private fun isTransientStatus(code: Int): Boolean {
        return code == 429 || code == 502 || code == 503 || code == 504 || code in 520..524
    }

    private fun retryDelayMs(attempt: Int, response: Response?): Long {
        val retryAfterSec = response?.header("Retry-After")?.trim()?.toLongOrNull()
        if (retryAfterSec != null && retryAfterSec >= 0) {
            return (retryAfterSec * 1000L).coerceAtMost(FapFolderConstants.RETRY_AFTER_MAX_MS)
        }
        val exp = FapFolderConstants.RETRY_BASE_DELAY_MS * (1L shl (attempt - 1))
        return exp + (0..250).random()
    }

    private fun pauseBeforeRetry(ms: Long, chain: Interceptor.Chain) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted while waiting to retry")
        }
        if (chain.call().isCanceled()) throw IOException("Canceled")
    }

    private fun proceedWithRetry(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isFirstPartyHost(request.url.host) || !isSafeToRetry(request)) {
            return chain.proceed(request)
        }
        val maxAttempts = FapFolderConstants.MAX_RETRY_ATTEMPTS
        var attempt = 1
        while (true) {
            var response: Response? = null
            try {
                response = chain.proceed(request)
            } catch (e: IOException) {
                if (chain.call().isCanceled() || attempt >= maxAttempts) throw e
                Log.w(TAG, "retry $attempt/$maxAttempts after ${e::class.java.simpleName}: ${request.url.encodedPath}")
            }
            if (response != null) {
                if (attempt >= maxAttempts || !isTransientStatus(response.code)) return response
                val wait = retryDelayMs(attempt, response)
                Log.w(TAG, "retry $attempt/$maxAttempts after HTTP ${response.code}: ${request.url.encodedPath}")
                response.close()
                pauseBeforeRetry(wait, chain)
            } else {
                pauseBeforeRetry(retryDelayMs(attempt, null), chain)
            }
            attempt++
        }
    }

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(FapFolderConstants.CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(FapFolderConstants.READ_TIMEOUT_S, TimeUnit.SECONDS)
            .writeTimeout(FapFolderConstants.WRITE_TIMEOUT_S, TimeUnit.SECONDS)
            .callTimeout(FapFolderConstants.CALL_TIMEOUT_S, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .cookieJar(getOrCreateCookieJar())

        val useCustomDoh = runCatching { Preferences.useDoH }.getOrDefault(false)
        if (useCustomDoh) {
            builder.dns(HDns())
        } else {
            builder.dns(Dns.SYSTEM)
        }

        val proxyType = runCatching { Preferences.proxyType }
            .getOrDefault(HProxySelector.TYPE_SYSTEM)
        if (proxyType != HProxySelector.TYPE_DIRECT &&
            proxyType != HProxySelector.TYPE_SYSTEM
        ) {
            builder.proxySelector(HProxySelector())
        }

        builder.addInterceptor(BrotliInterceptor)

        builder.addInterceptor { chain -> proceedWithRetry(chain) }

        builder.addInterceptor { chain ->
            val original = chain.request()
            val host = original.url.host

            if (!isFirstPartyHost(host)) {
                return@addInterceptor chain.proceed(original)
            }

            val isAjax = original.header("x-requested-with") != null ||
                    original.url.encodedPath.contains("/includes/ajax/")

            val headerSet = if (isAjax) FapFolderHeaders.ajax()
            else FapFolderHeaders.pageSameOrigin()
            val requestBuilder = original.newBuilder()
            headerSet.names().forEach { name ->
                if (original.header(name) == null) {
                    requestBuilder.addHeader(name, headerSet.get(name) ?: "")
                }
            }
            chain.proceed(requestBuilder.build())
        }

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(UrlLoggingInterceptor())
        }

        return builder.build()
    }

    fun normalizeUrl(path: String): String {
        return com.yenaly.han1meviewer.FapFolder.common.FapFolderUrlUtils.normalizePath(path)
    }

    fun rebuildNetwork() {
        Log.d(TAG, "Refreshing cookie jar and client")
        ensureInitialized()
        val jar = getOrCreateCookieJar()

        jar.restoreFromPreferences()

        synchronized(this) {
            _client = null
            _playbackClient = null
            _service = null
        }
    }

    fun clearCookies() {
        cookieJar.clearCookies()
        cookieJar.clearPersistedPreferences()
        Log.d(TAG, "clearCookies: jar and preferences cleared")
    }

    fun clearAuthCookies() {
        cookieJar.clearAuthCookies()
        Log.d(TAG, "clearAuthCookies: session cookies removed, Cloudflare cookies kept")
    }

    fun snapshotCookies(): String {
        ensureInitialized()
        val snap = FapFolderCookieJar.snapshot()
        if (snap.isEmpty()) return ""
        return snap.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun persistCookies() {
        cookieJar.persistToPreferences()
    }

    fun hasAuthCookies(): Boolean {
        ensureInitialized()
        return FapFolderCookieJar.hasRequiredAuthCookie()
    }

    fun isChallengeResponse(body: String?): Boolean =
        cookieJar.isCloudflareChallengeBody(body)

    fun context(): Context = context
}
