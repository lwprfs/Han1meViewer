package com.yenaly.han1meviewer.worker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.OutputStream
import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Thrown for HTTP level problems so the caller can tell "cookies expired" from "network down". */
class HlsHttpException(val code: Int, message: String) : IOException(message)

/**
 * Minimal HLS VOD downloader: resolves master -> best variant -> media playlist, downloads the
 * segments (optionally AES-128 encrypted) and writes them back to back into one stream, which
 * gives a playable `.ts` (or fragmented mp4 when the playlist uses `EXT-X-MAP`).
 */
class HlsDownloader(
    private val client: OkHttpClient,
    private val headers: Map<String, String>,
) {

    data class Key(val uri: String, val iv: ByteArray?)

    data class Segment(
        val url: String,
        val sequence: Long,
        val key: Key?,
    )

    data class MediaPlaylist(
        val segments: List<Segment>,
        val initSegmentUrl: String?,
    )

    private fun request(url: String): Request =
        Request.Builder().url(url).get().apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()

    private fun fetchBytes(url: String): ByteArray {
        client.newCall(request(url)).execute().use { response ->
            if (!response.isSuccessful) {
                throw HlsHttpException(response.code, "HTTP ${response.code} for $url")
            }
            val finalPath = response.request.url.encodedPath.lowercase()
            if (finalPath.contains("/login")) {
                throw HlsHttpException(401, "Redirected to login for $url")
            }
            return response.body.bytes()
        }
    }

    private fun fetchText(url: String): String = String(fetchBytes(url), Charsets.UTF_8)

    private fun resolve(base: String, ref: String): String =
        runCatching { URI(base).resolve(ref).toString() }.getOrDefault(ref)

    /** Resolves [url] (master or media playlist) into a flat list of segments. */
    suspend fun resolvePlaylist(url: String): MediaPlaylist = withContext(Dispatchers.IO) {
        var currentUrl = url
        var text = fetchText(currentUrl)
        if (!text.trimStart().startsWith("#EXTM3U")) {
            throw IOException("Not an HLS playlist")
        }
        var guard = 0
        while (text.contains("#EXT-X-STREAM-INF") && guard < 4) {
            currentUrl = pickBestVariant(currentUrl, text)
                ?: throw IOException("Playlist has no playable variant")
            text = fetchText(currentUrl)
            guard++
        }
        parseMedia(currentUrl, text)
    }

    private fun pickBestVariant(baseUrl: String, text: String): String? {
        val lines = text.lines().map { it.trim() }
        var bestBandwidth = -1L
        var best: String? = null
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val bandwidth = Regex("""BANDWIDTH=(\d+)""").find(line)
                    ?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                var j = i + 1
                while (j < lines.size && (lines[j].isEmpty() || lines[j].startsWith("#"))) j++
                if (j < lines.size && bandwidth >= bestBandwidth) {
                    bestBandwidth = bandwidth
                    best = resolve(baseUrl, lines[j])
                }
                i = j
            }
            i++
        }
        return best
    }

    private fun parseMedia(baseUrl: String, text: String): MediaPlaylist {
        var mediaSequence = 0L
        var currentKey: Key? = null
        var initUrl: String? = null
        val segments = ArrayList<Segment>()
        var expectUri = false // kept for readability: EXTINF is always followed by its URI
        var sequence = 0L
        for (raw in text.lines()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                    mediaSequence = line.substringAfter(':').trim().toLongOrNull() ?: 0L
                    sequence = mediaSequence
                }
                line.startsWith("#EXT-X-KEY:") -> {
                    val method = Regex("""METHOD=([A-Z0-9-]+)""").find(line)?.groupValues?.get(1)
                    currentKey = when (method) {
                        null, "NONE" -> null
                        "AES-128" -> {
                            val uri = Regex("""URI="([^"]+)"""").find(line)?.groupValues?.get(1)
                                ?: throw IOException("Encrypted playlist without key URI")
                            val iv = Regex("""IV=0[xX]([0-9a-fA-F]+)""").find(line)
                                ?.groupValues?.get(1)?.let(::hexToBytes)
                            Key(resolve(baseUrl, uri), iv)
                        }
                        else -> throw IOException("Unsupported HLS encryption: $method")
                    }
                }
                line.startsWith("#EXT-X-MAP:") -> {
                    Regex("""URI="([^"]+)"""").find(line)?.groupValues?.get(1)
                        ?.let { initUrl = resolve(baseUrl, it) }
                }
                line.startsWith("#EXTINF") -> expectUri = true
                line.startsWith("#") -> Unit
                else -> {
                    segments += Segment(resolve(baseUrl, line), sequence, currentKey)
                    sequence++
                    expectUri = false
                }
            }
        }
        if (segments.isEmpty()) throw IOException("Playlist has no segments")
        return MediaPlaylist(segments, initUrl)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val padded = if (hex.length % 2 == 0) hex else "0$hex"
        val raw = ByteArray(padded.length / 2) {
            padded.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
        if (raw.size >= 16) return raw.copyOfRange(raw.size - 16, raw.size)
        return ByteArray(16 - raw.size) + raw
    }

    private fun sequenceIv(sequence: Long): ByteArray {
        val iv = ByteArray(16)
        for (i in 0 until 8) iv[15 - i] = (sequence shr (8 * i)).toByte()
        return iv
    }

    private val keyCache = HashMap<String, ByteArray>()

    private fun keyBytes(uri: String): ByteArray =
        synchronized(keyCache) { keyCache[uri] } ?: fetchBytes(uri).also {
            if (it.size != 16) throw IOException("Unexpected HLS key length ${it.size}")
            synchronized(keyCache) { keyCache[uri] = it }
        }

    private fun downloadSegment(segment: Segment): ByteArray {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                val data = fetchBytes(segment.url)
                val key = segment.key ?: return data
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(keyBytes(key.uri), "AES"),
                    IvParameterSpec(key.iv ?: sequenceIv(segment.sequence)),
                )
                return cipher.doFinal(data)
            } catch (e: HlsHttpException) {
                if (e.code == 401 || e.code == 403 || e.code == 404 || e.code == 410) throw e
                lastError = e
            } catch (e: IOException) {
                lastError = e
            }
            if (attempt < 2) Thread.sleep(600L * (attempt + 1))
        }
        throw lastError ?: IOException("Segment download failed")
    }

    /**
     * Writes the playlist into [out]. [onProgress] gets (finished segments, total segments,
     * bytes written so far) after every batch. Returns the number of bytes written.
     */
    suspend fun download(
        playlist: MediaPlaylist,
        out: OutputStream,
        parallelism: Int = 4,
        onProgress: suspend (done: Int, total: Int, bytes: Long) -> Unit,
    ): Long = withContext(Dispatchers.IO) {
        var bytes = 0L
        playlist.initSegmentUrl?.let { initUrl ->
            val data = fetchBytes(initUrl)
            out.write(data)
            bytes += data.size
        }
        val total = playlist.segments.size
        var done = 0
        playlist.segments.chunked(parallelism.coerceAtLeast(1)).forEach { batch ->
            currentCoroutineContext().ensureActive()
            val parts = coroutineScope {
                batch.map { seg -> async(Dispatchers.IO) { downloadSegment(seg) } }
                    .map { it.await() }
            }
            parts.forEach { part ->
                out.write(part)
                bytes += part.size
            }
            done += batch.size
            onProgress(done, total, bytes)
        }
        out.flush()
        bytes
    }

    companion object {
        fun isHls(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            return url.substringBefore('#').lowercase().contains(".m3u8")
        }
    }
}
