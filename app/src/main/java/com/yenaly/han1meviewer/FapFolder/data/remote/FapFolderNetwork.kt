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
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object FapFolderNetwork {

    private const val TAG = "FapFolderNetwork"

    @Volatile
    private var _service: FapFolderService? = null

    @Volatile
    private var _client: OkHttpClient? = null

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
            isInitialized = true
            val jar = getOrCreateCookieJar()
            jar.restoreFromPreferences()
            Log.d(
                TAG,
                "init: cookieJar restored, hasAuth=${FapFolderCookieJar.hasRequiredAuthCookie()}",
            )
        }
    }

    private val context: Context
        get() {
            if (!isInitialized) {
                throw IllegalStateException("FapFolderNetwork not initialized.")
            }
            return appContext ?: throw IllegalStateException("FapFolderNetwork context is null")
        }

    val baseUrl: String
        get() = FapFolderConstants.BASE_URL

    val ajaxBase: String
        get() = FapFolderConstants.AJAX_BASE

    val uploadsBase: String
        get() = FapFolderConstants.UPLOADS_BASE

    val cookieJar: FapFolderCookieJar
        get() = getOrCreateCookieJar()

    val service: FapFolderService
        get() {
            if (!isInitialized) {
                throw IllegalStateException("FapFolderNetwork not initialized.")
            }
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
            if (!isInitialized) {
                throw IllegalStateException("FapFolderNetwork not initialized.")
            }
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

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(FapFolderConstants.CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(FapFolderConstants.READ_TIMEOUT_S, TimeUnit.SECONDS)
            .writeTimeout(FapFolderConstants.CALL_TIMEOUT_S, TimeUnit.SECONDS)
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

        builder.addInterceptor { chain ->
            val original = chain.request()
            val host = original.url.host

            if (!isFirstPartyHost(host)) {
                return@addInterceptor chain.proceed(original)
            }

            val builder = original.newBuilder()
            val isAjax = original.url.encodedPath.contains("/includes/ajax/")
            val hasExplicitHeaders = original.header("x-requested-with") != null ||
                    original.header("X-Requested-With") != null

            if (!hasExplicitHeaders) {
                val headerSet = if (isAjax) FapFolderHeaders.ajax()
                else FapFolderHeaders.pageSameOrigin()
                headerSet.names().forEach { name ->
                    if (original.header(name) == null) {
                        builder.addHeader(name, headerSet.get(name) ?: "")
                    }
                }
            }
            chain.proceed(builder.build())
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
        val jar = getOrCreateCookieJar()
        jar.restoreFromPreferences()

        synchronized(this) {
            _client = null
            _service = null
        }
    }

    fun clearCookies() {
        cookieJar.clearCookies()
        cookieJar.clearPersistedPreferences()
        Log.d(TAG, "clearCookies: jar and preferences cleared")
    }

    fun snapshotCookies(): String {
        val snap = FapFolderCookieJar.snapshot()
        if (snap.isEmpty()) return ""
        return snap.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun persistCookies() {
        cookieJar.persistToPreferences()
    }

    fun hasAuthCookies(): Boolean = FapFolderCookieJar.hasRequiredAuthCookie()

    fun isChallengeResponse(body: String?): Boolean =
        cookieJar.isCloudflareChallengeBody(body)

    fun context(): Context = context
}
