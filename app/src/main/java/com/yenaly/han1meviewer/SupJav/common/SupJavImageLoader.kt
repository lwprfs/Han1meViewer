package com.yenaly.han1meviewer.SupJav.common

import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.yenaly.han1meviewer.USER_AGENT
import com.yenaly.han1meviewer.logic.network.HDns
import com.yenaly.han1meviewer.logic.network.HProxySelector
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import okio.Path.Companion.toOkioPath
import java.util.concurrent.TimeUnit

object SupJavImageLoader {

    private const val TAG = "SupJavImage"
    private const val JAVCHU_REFERER = "https://javchu.com/"

    private fun isSupJavHost(host: String): Boolean =
        SupJavConstants.HOSTNAME.any { host == it || host.endsWith(".$it") }

    private fun isJavchuHost(host: String): Boolean =
        host == "javchu.com" || host.endsWith(".javchu.com")

    private val supJavInterceptor = Interceptor { chain ->
        val original = chain.request()
        val request = original.newBuilder()
            .header("Referer", "${SupJavConstants.BASE_URL}/")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "image/avif,image/webp,image/apng,image/jpeg,image/png,*/*;q=0.8")
            .removeHeader("Cookie")
            .build()
        var response = try {
            chain.proceed(request)
        } catch (e: IOException) {

            val plain = plainUrl(request.url.toString()) ?: throw e
            Log.w(TAG, "IO error for ${request.url}, retrying $plain: ${e.message}")
            chain.proceed(request.newBuilder().url(plain).build())
        }
        if (!response.isSuccessful) {
            Log.w(TAG, "HTTP ${response.code} for ${request.url}")
            val plain = plainUrl(request.url.toString())
            if (plain != null) {
                response.close()
                response = chain.proceed(request.newBuilder().url(plain).build())
                if (!response.isSuccessful) Log.w(TAG, "HTTP ${response.code} for $plain")
            }
        }
        normalizeContentType(response)
    }

    private fun plainUrl(url: String): String? {
        val idx = url.indexOf('!')
        return if (idx > 0) url.substring(0, idx) else null
    }

    private fun normalizeContentType(response: Response): Response {
        val type = response.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase()
        val fixed = when {
            type == "image/jpg" -> "image/jpeg"
            type == null || type == "application/octet-stream" -> "image/jpeg"
            else -> return response
        }
        if (!response.isSuccessful) return response
        return response.newBuilder().header("Content-Type", fixed).build()
    }

    private val supJavClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .cookieJar(CookieJar.NO_COOKIES)
            .dns(HDns())
            .proxySelector(HProxySelector())
            .addInterceptor(supJavInterceptor)
            .build()
    }

    private val otherClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                if (isJavchuHost(request.url.host) && request.header("Referer") == null) {
                    chain.proceed(request.newBuilder().header("Referer", JAVCHU_REFERER).build())
                } else {
                    chain.proceed(request)
                }
            }
            .build()
    }

    private val callFactory = Call.Factory { request ->
        val client = if (isSupJavHost(request.url.host)) supJavClient else otherClient
        client.newCall(request)
    }

    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext, "supjav_image_cache").also { instance = it }
        }

    fun buildGlobal(context: Context): ImageLoader = build(context.applicationContext, null)

    private fun build(context: Context, cacheDirName: String?): ImageLoader {
        val builder = ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { callFactory }))
            }
            .crossfade(true)
        if (cacheDirName != null) {
            builder.diskCache(
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve(cacheDirName).toOkioPath())
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            )
        }
        return builder.build()
    }
}
