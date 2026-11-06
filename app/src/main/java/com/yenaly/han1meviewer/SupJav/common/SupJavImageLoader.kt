package com.yenaly.han1meviewer.SupJav.common

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.yenaly.han1meviewer.USER_AGENT
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.util.concurrent.TimeUnit

object SupJavImageLoader {

    private fun isSupJavHost(host: String): Boolean =
        SupJavConstants.HOSTNAME.any { host == it || host.endsWith(".$it") }

    private val refererInterceptor = Interceptor { chain ->
        val request = chain.request()
        if (!isSupJavHost(request.url.host)) return@Interceptor chain.proceed(request)
        chain.proceed(
            request.newBuilder()
                .header("Referer", "${SupJavConstants.BASE_URL}/")
                .header("User-Agent", USER_AGENT)
                .build()
        )
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor(refererInterceptor)
            .build()
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
                add(OkHttpNetworkFetcherFactory(callFactory = { client }))
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
