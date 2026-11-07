package com.yenaly.han1meviewer.SupJav.common

import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.yenaly.han1meviewer.SupJav.cloudflare.SupJavCloudflareInterceptor
import com.yenaly.han1meviewer.SupJav.data.remote.SupJavNetwork
import com.yenaly.han1meviewer.USER_AGENT
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.util.concurrent.TimeUnit

/**
 * SupJav's image host (img.supjav.com) rejects hotlinked requests, so every image request
 * to a SupJav host must carry `Referer: https://supjav.com/`. On top of that the images have to
 * be fetched through the same network setup as the SupJav pages (custom DNS / proxy settings),
 * otherwise they are unreachable on networks where the plain system resolver is blocked.
 */
object SupJavImageLoader {

    private const val TAG = "SupJavImage"
    private const val JAVCHU_REFERER = "https://javchu.com/"

    private fun isSupJavHost(host: String): Boolean =
        SupJavConstants.HOSTNAME.any { host == it || host.endsWith(".$it") }

    private fun isJavchuHost(host: String): Boolean =
        host == "javchu.com" || host.endsWith(".javchu.com")

    /** Adds referer + user agent to SupJav image requests and logs the ones that fail. */
    private val supJavInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("Referer", "${SupJavConstants.BASE_URL}/")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "image/webp,image/apng,image/jpeg,image/png,*/*;q=0.8")
            .build()
        val response = chain.proceed(request)
        if (!response.isSuccessful) {
            Log.w(TAG, "HTTP ${response.code} for ${request.url}")
        }
        response
    }

    /** Client for SupJav hosts: the SupJav network config minus the Cloudflare page handling. */
    private val supJavClient: OkHttpClient by lazy {
        val builder = SupJavNetwork.sharedOkHttpClient.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
        // Images must never pop up the Cloudflare verification screen.
        builder.interceptors().removeAll { it is SupJavCloudflareInterceptor }
        builder.addInterceptor(supJavInterceptor).build()
    }

    /** Client for every other host: untouched except for javchu.com, which also checks referers. */
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

    /** Dedicated loader with its own disk cache, used explicitly by the SupJav screens. */
    fun get(context: Context): ImageLoader =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext, "supjav_image_cache").also { instance = it }
        }

    /**
     * Loader for the app-wide singleton: behaves like the default loader for every other site but
     * routes SupJav hosts through the SupJav client, so SupJav covers also work in shared
     * screens (history, check-in...).
     */
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
