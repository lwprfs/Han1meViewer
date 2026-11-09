package com.yenaly.han1meviewer.SupJav.data.remote

import android.util.Base64
import android.util.Log
import com.yenaly.han1meviewer.SupJav.common.SupJavConstants
import com.yenaly.han1meviewer.SupJav.common.SupJavSettings
import com.yenaly.han1meviewer.SupJav.data.model.SupJavServer
import com.yenaly.han1meviewer.SupJav.data.model.SupJavStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

object SupJavStreamResolver {

    private const val TAG = "SupJavResolver"

    private val STREAMTAPE_REGEX = Regex(
        """getElementById\('robotlink'\)\.innerHTML\s*=\s*'([^']*)'\s*\+\s*\('([^']*)'\)((?:\.substring\(\d+\))*)"""
    )
    private val SUBSTRING_REGEX = Regex("""substring\((\d+)\)""")
    private val PACKED_REGEX = Regex(
        """\}\('(.*)',\s*(\d+),\s*(\d+),\s*'(.*?)'\.split\('\|'\)""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val FILE_REGEX = Regex("""file\s*:\s*["'](https?://[^"']+)["']""")
    private val HLS_KEY_REGEX = Regex("""["']?hls\d*["']?\s*:\s*["'](https?://[^"']+)["']""")
    private val URL_PLAY_REGEX = Regex("""var\s+urlPlay\s*=\s*'([^']+)'""")
    private val JS_REDIRECT_REGEX = Regex("""window\.location\.href\s*=\s*['"](https?://[^'"]+)['"]""")
    private val WORD_REGEX = Regex("""\b\w+\b""")
    private val RESOLUTION_REGEX = Regex("""RESOLUTION=(\d+)x(\d+)""")
    private val BANDWIDTH_REGEX = Regex("""BANDWIDTH=(\d+)""")

    suspend fun resolve(server: SupJavServer): List<SupJavStream> = withContext(Dispatchers.IO) {
        val hosterUrl = resolveHosterUrl(server.token)
        if (hosterUrl == null) {
            Log.w(TAG, "No hoster URL for server ${server.name}")
            return@withContext emptyList()
        }
        val host = hosterUrl.toHttpUrlOrNull()?.host.orEmpty()
        Log.d(TAG, "server=${server.name} host=$host")
        val label = server.name
        val name = server.name.uppercase()
        val single: SupJavStream? = when {
            name == "ST" || host.contains("streamtape") || host.contains("tape") ->
                streamtape(hosterUrl, label)

            name == "LUC" || host.contains("lumin") || host.contains("lulu") ->
                packedPlayer(hosterUrl, label) ?: apiStream(hosterUrl, label)

            name == "FST" -> packedPlayer(hosterUrl, label) ?: apiStream(hosterUrl, label)
            name == "VAS" -> apiStream(hosterUrl, label) ?: packedPlayer(hosterUrl, label)
            name == "VOE" || host.contains("voe") -> voe(hosterUrl, label)
            name == "TV" -> tvPlayer(hosterUrl, label)
            else -> null
        }
        val stream = single ?: return@withContext emptyList()
        orderByPreference(expandHls(stream))
    }

    private fun resolveHosterUrl(token: String): String? {
        val request = Request.Builder()
            .url("${SupJavConstants.PROTECTOR_URL}/supjav.php?c=$token")
            .header("Referer", "${SupJavConstants.PROTECTOR_URL}/")
            .build()
        return runCatching {
            SupJavNetwork.noRedirectClient.newCall(request).execute().use { it.header("location") }
        }.onFailure { Log.e(TAG, "protector failed", it) }.getOrNull()
    }

    private fun fetch(url: String, referer: String, origin: String? = null): String {
        val builder = Request.Builder().url(url).header("Referer", referer)
        if (origin != null) builder.header("Origin", origin)
        return SupJavNetwork.sharedOkHttpClient.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} for $url")
            resp.body.string()
        }
    }

    private fun originOf(url: String): String? =
        url.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" }

    private fun apiStream(embedUrl: String, label: String): SupJavStream? = runCatching {
        val clean = embedUrl.substringBefore('#')
        val origin = originOf(clean) ?: return null
        val fileCode = clean.substringBefore('?').trimEnd('/').substringAfterLast('/')
        val body = JSONObject()
            .put("filecode", fileCode)
            .put("device", "android")
            .put("codecs", JSONArray())
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$origin/api/stream")
            .header("Origin", origin)
            .header("Referer", clean)
            .post(body)
            .build()
        val json = SupJavNetwork.sharedOkHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            JSONObject(resp.body.string())
        }
        val url = json.optString("streaming_url")
        if (url.isBlank()) null else SupJavStream(url, label, "$origin/")
    }.onFailure { Log.e(TAG, "apiStream failed", it) }.getOrNull()

    private fun packedPlayer(embedUrl: String, label: String): SupJavStream? = runCatching {
        val clean = embedUrl.substringBefore('#')
        val origin = originOf(clean) ?: return null
        val html = fetch(clean, "${SupJavConstants.PROTECTOR_URL}/")
        val script = unpack(html) ?: html
        val url = FILE_REGEX.find(script)?.groupValues?.get(1)
            ?: HLS_KEY_REGEX.find(script)?.groupValues?.get(1)
            ?: return null
        SupJavStream(url, label, "$origin/")
    }.onFailure { Log.e(TAG, "packedPlayer failed", it) }.getOrNull()

    private fun tvPlayer(embedUrl: String, label: String): SupJavStream? = runCatching {
        val clean = embedUrl.substringBefore('#')
        val origin = originOf(clean) ?: return null
        val html = fetch(clean, "${SupJavConstants.PROTECTOR_URL}/")
        val url = URL_PLAY_REGEX.find(html)?.groupValues?.get(1) ?: return null
        SupJavStream(url, label, "$origin/")
    }.onFailure { Log.e(TAG, "tvPlayer failed", it) }.getOrNull()

    private fun streamtape(embedUrl: String, label: String): SupJavStream? = runCatching {
        val clean = embedUrl.substringBefore('#')
        val html = fetch(clean, "${SupJavConstants.PROTECTOR_URL}/")
        val match = STREAMTAPE_REGEX.findAll(html).lastOrNull() ?: return null
        val (prefix, inner, subs) = match.destructured
        var tail = inner
        SUBSTRING_REGEX.findAll(subs).forEach { n ->
            tail = tail.drop(n.groupValues[1].toInt())
        }
        val path = prefix + tail
        val url = when {
            path.startsWith("//") -> "https:$path"
            path.startsWith("http") -> path
            else -> "https://streamtape.com/${path.trimStart('/')}"
        }
        SupJavStream(url, label, "https://streamtape.com/")
    }.onFailure { Log.e(TAG, "streamtape failed", it) }.getOrNull()

    private fun voe(embedUrl: String, label: String): SupJavStream? = runCatching {
        var pageUrl = embedUrl.substringBefore('#')
        var html = fetch(pageUrl, "${SupJavConstants.PROTECTOR_URL}/")
        repeat(2) {
            if (html.length < 2_000 && html.contains("window.location.href")) {
                val next = JS_REDIRECT_REGEX.find(html)?.groupValues?.get(1) ?: return@repeat
                pageUrl = next
                html = fetch(pageUrl, "${SupJavConstants.PROTECTOR_URL}/")
            }
        }
        val origin = originOf(pageUrl) ?: return null
        val url = voeStreamFrom(html) ?: return null
        SupJavStream(url, label, "$origin/")
    }.onFailure { Log.e(TAG, "voe failed", it) }.getOrNull()

    private fun voeStreamFrom(html: String): String? {

        Regex("""['"]hls['"]\s*:\s*['"]([^'"]+)['"]""").find(html)?.groupValues?.get(1)?.let { v ->
            val decoded = if (v.startsWith("http")) v else runCatching {
                String(Base64.decode(v, Base64.DEFAULT))
            }.getOrNull()
            if (decoded?.startsWith("http") == true) return decoded
        }
        Regex("""['"]mp4['"]\s*:\s*['"]([^'"]+)['"]""").find(html)?.groupValues?.get(1)?.let { v ->
            val decoded = if (v.startsWith("http")) v else runCatching {
                String(Base64.decode(v, Base64.DEFAULT))
            }.getOrNull()
            if (decoded?.startsWith("http") == true) return decoded
        }

        val blob = Regex("""<script[^>]*type=["']application/json["'][^>]*>\s*\[?\s*"([^"]+)"\s*\]?\s*</script>""")
            .find(html)?.groupValues?.get(1)
        if (blob != null) {
            val json = runCatching { decodeVoeBlob(blob) }.getOrNull()
            if (json != null) {
                for (key in listOf("source", "direct_access_url", "hls", "mp4")) {
                    val v = json.optString(key)
                    if (v.startsWith("http")) return v
                }
            }
        }
        return null
    }

    private fun decodeVoeBlob(encoded: String): JSONObject {
        val rot13 = encoded.map { c ->
            when (c) {
                in 'a'..'z' -> 'a' + (c - 'a' + 13) % 26
                in 'A'..'Z' -> 'A' + (c - 'A' + 13) % 26
                else -> c
            }
        }.joinToString("")
        var s = rot13
        for (pattern in listOf("@$", "^^", "~@", "%?", "*~", "!!", "#&")) s = s.replace(pattern, "")
        s = String(Base64.decode(s, Base64.DEFAULT))
        s = s.map { (it.code - 3).toChar() }.joinToString("").reversed()
        s = String(Base64.decode(s, Base64.DEFAULT))
        return JSONObject(s)
    }

    private fun expandHls(stream: SupJavStream): List<SupJavStream> {
        val single = listOf(stream)
        if (!stream.url.substringBefore('?').contains(".m3u8", ignoreCase = true)) return single
        return runCatching {
            val text = fetch(stream.url, stream.referer, stream.referer.trimEnd('/'))
            if (!text.contains("#EXT-X-STREAM-INF")) return single
            val base = URI(stream.url)
            val lines = text.lines()
            val variants = mutableListOf<SupJavStream>()
            lines.forEachIndexed { i, line ->
                if (!line.startsWith("#EXT-X-STREAM-INF")) return@forEachIndexed
                val uri = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                    ?.trim() ?: return@forEachIndexed
                val height = RESOLUTION_REGEX.find(line)?.groupValues?.get(2)?.toIntOrNull() ?: 0
                val kbps = (BANDWIDTH_REGEX.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L) / 1000
                val tag = if (height > 0) "${height}p" else if (kbps > 0) "${kbps}k" else "auto"
                variants += SupJavStream(
                    url = base.resolve(uri).toString(),
                    label = "${stream.label} - $tag",
                    referer = stream.referer,
                    height = height,
                )
            }
            if (variants.isEmpty()) return single
            val unique = variants.distinctBy { it.label }.sortedByDescending { it.height }
            unique + stream.copy(label = "${stream.label} - Auto")
        }.onFailure { Log.w(TAG, "expandHls failed, using master", it) }.getOrDefault(single)
    }

    private fun orderByPreference(streams: List<SupJavStream>): List<SupJavStream> {
        if (streams.size < 2) return streams
        val target = SupJavSettings.preferredQuality.removeSuffix("p").toIntOrNull() ?: return streams
        val withHeight = streams.filter { it.height > 0 }
        val chosen = withHeight.filter { it.height <= target }.maxByOrNull { it.height }
            ?: withHeight.minByOrNull { it.height }
            ?: return streams
        return listOf(chosen) + streams.filter { it !== chosen }
    }

    internal fun unpack(source: String): String? {
        val m = PACKED_REGEX.find(source) ?: return null
        val payload = m.groupValues[1].replace("\\'", "'")
        val radix = m.groupValues[2].toIntOrNull() ?: return null
        val count = m.groupValues[3].toIntOrNull() ?: return null
        val dict = m.groupValues[4].split('|')
        if (radix !in 2..62) return null

        val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        fun encode(n: Int): String {
            if (n == 0) return "0"
            var value = n
            val sb = StringBuilder()
            while (value > 0) {
                sb.append(alphabet[value % radix])
                value /= radix
            }
            return sb.reverse().toString()
        }

        val lookup = HashMap<String, String>(count)
        for (i in 0 until count) {
            val key = encode(i)
            lookup[key] = dict.getOrNull(i)?.takeIf { it.isNotEmpty() } ?: key
        }
        return WORD_REGEX.replace(payload) { lookup[it.value] ?: it.value }
    }
}
