package com.yenaly.han1meviewer.XHamster.data.remote

import com.yenaly.han1meviewer.XHamster.common.XHamsterConstants
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterDetail
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URI

object XHamsterStreamResolver {

    const val AUTO = "Auto"

    private val HEIGHT = Regex("""(\d{3,4})""")
    private val RESOLUTION = Regex("""RESOLUTION=(\d+)x(\d+)""")
    private val NAME = Regex("""NAME="([^"]+)"""")

    suspend fun resolve(detail: XHamsterDetail): List<XHamsterStream> =
        withContext(Dispatchers.IO) {
            val result = ArrayList<XHamsterStream>()

            detail.hlsTemplate?.let { template ->
                val ladder = ladderFor(detail.maxResolution)
                val found = coroutineScope {
                    ladder.map { quality ->
                        async {
                            val url = template.replace("_TPL_", quality)
                            if (isPlaylist(url)) {
                                XHamsterStream(quality, url, heightOf(quality))
                            } else null
                        }
                    }.awaitAll()
                }.filterNotNull()
                result += found
            }

            if (result.isEmpty()) {
                detail.hlsDirect?.let { result += expandMaster(it) }
            }

            if (result.isEmpty()) {
                detail.mp4Fallback?.let { url ->
                    val q = HEIGHT.find(url.substringAfterLast('/'))?.value
                    result += XHamsterStream(
                        label = if (q != null) "${q}p" else "MP4",
                        url = url,
                        height = q?.toIntOrNull() ?: 1,
                    )
                }
            }

            // Highest quality first, adaptive entry (height 0) kept in front.
            result.sortedWith(compareBy({ it.height != 0 }, { -it.height }))
        }

    private fun heightOf(label: String): Int = HEIGHT.find(label)?.value?.toIntOrNull() ?: 0

    private fun ladderFor(maxResolution: String?): List<String> {
        val max = maxResolution?.let {
            if (it.contains("4k", ignoreCase = true)) 2160 else heightOf(it).takeIf { h -> h > 0 }
        } ?: 1080
        val filtered = XHamsterConstants.QUALITY_LADDER.filter { heightOf(it) <= max }
        return filtered.ifEmpty { listOf("480p", "240p") }
    }

    private fun get(url: String): String? = runCatching {
        val request = Request.Builder().url(url).build()
        XHamsterNetwork.streamClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body.string() else null
        }
    }.getOrNull()

    private fun isPlaylist(url: String): Boolean =
        get(url)?.trimStart()?.startsWith("#EXTM3U") == true

    private fun expandMaster(masterUrl: String): List<XHamsterStream> {
        val text = get(masterUrl)
        val auto = XHamsterStream(AUTO, masterUrl, 0)
        if (text == null || !text.contains("#EXT-X-STREAM-INF")) return listOf(auto)

        val variants = ArrayList<XHamsterStream>()
        val lines = text.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val height = RESOLUTION.find(line)?.groupValues?.get(2)?.toIntOrNull() ?: 0
                val name = NAME.find(line)?.groupValues?.get(1)
                var j = i + 1
                while (j < lines.size && (lines[j].isBlank() || lines[j].startsWith("#"))) j++
                if (j < lines.size) {
                    val ref = lines[j].trim()
                    val abs = runCatching { URI(masterUrl).resolve(ref).toString() }.getOrDefault(ref)
                    variants += XHamsterStream(
                        label = name ?: if (height > 0) "${height}p" else "Stream",
                        url = abs,
                        height = height,
                    )
                    i = j
                }
            }
            i++
        }
        return listOf(auto) + variants.distinctBy { it.url }
    }
}
