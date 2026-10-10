package com.yenaly.han1meviewer.HentaiMama.common

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetwork
import okio.Path.Companion.toOkioPath

object HentaiMamaImageLoader {

    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }
    }

    private fun build(context: Context): ImageLoader {
        val diskCache = DiskCache.Builder()
            .directory(
                context.cacheDir.resolve("hentaimama_image_cache").toOkioPath()
            )
            .maxSizeBytes(512L * 1024 * 1024)
            .build()

        return ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { HentaiMamaNetwork.sharedOkHttpClient }
                    )
                )
            }
            .diskCache(diskCache)
            .crossfade(true)
            .build()
    }

    fun reset() {
        synchronized(this) {
            instance = null
        }
    }
}
