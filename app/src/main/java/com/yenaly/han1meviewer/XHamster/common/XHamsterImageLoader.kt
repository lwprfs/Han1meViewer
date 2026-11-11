package com.yenaly.han1meviewer.XHamster.common

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.yenaly.han1meviewer.XHamster.cloudflare.XHamsterCloudflareInterceptor
import com.yenaly.han1meviewer.XHamster.data.remote.XHamsterNetwork
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

object XHamsterImageLoader {

    private val refererInterceptor = Interceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header("Referer", XHamsterConstants.REFERER)
                .header("Accept", "image/webp,image/apng,image/jpeg,image/png,*/*;q=0.8")
                .build()
        )
    }

    @Volatile
    private var cachedBase: OkHttpClient? = null

    @Volatile
    private var cachedImageClient: OkHttpClient? = null

    @Synchronized
    private fun imageClient(): OkHttpClient {
        val base = XHamsterNetwork.sharedOkHttpClient
        val existing = cachedImageClient
        if (existing != null && base === cachedBase) return existing
        val builder = base.newBuilder().cookieJar(CookieJar.NO_COOKIES)
        builder.interceptors().removeAll { it is XHamsterCloudflareInterceptor }
        val client = builder.addInterceptor(refererInterceptor).build()
        cachedBase = base
        cachedImageClient = client
        return client
    }

    private val callFactory = Call.Factory { request -> imageClient().newCall(request) }

    fun isCoverUrl(url: HttpUrl): Boolean = isCdnHost(url.host)

    fun isCdnHost(host: String): Boolean {
        val h = host.lowercase()
        return h == "xhcdn.com" || h.endsWith(".xhcdn.com")
    }

    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    private fun build(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { callFactory }))
            }
            .diskCache(
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("xhamster_image_cache").toOkioPath())
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            )
            .crossfade(true)
            .build()
}
