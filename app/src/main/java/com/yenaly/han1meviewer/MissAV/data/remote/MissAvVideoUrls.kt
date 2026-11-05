package com.yenaly.han1meviewer.MissAV.data.remote
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.yenaly.han1meviewer.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object MissAvVideoUtils {

    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        if (text.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied: $text", Toast.LENGTH_SHORT).show()
    }

    fun parseCodeAndExtra(code: String): Pair<String, String?> {
        val suffixPatterns = listOf(
            "uncensored-leak",
            "english-subtitle",
            "chinese-subtitle",
            "uncensored"
        )

        for (suffix in suffixPatterns) {
            if (code.lowercase().endsWith("-$suffix")) {
                val mainCode = code.substring(0, code.length - suffix.length - 1)
                return Pair(mainCode, suffix.uppercase())
            }
        }

        val parts = code.split("-")
        if (parts.size >= 2) {
            val last = parts.last()
            val extras = listOf("UNCENSORED-LEAK", "ENGLISH-SUBTITLE", "CHINESE-SUBTITLE", "UNCENSORED")
            if (extras.any { it.equals(last, ignoreCase = true) }) {
                return Pair(parts.dropLast(1).joinToString("-"), last.uppercase())
            }
        }
        return Pair(code, null)
    }

    fun extractReleaseDate(description: String?): String? {
        if (description.isNullOrBlank()) return null
        val pattern = Pattern.compile("Release date:\\s*(\\d{4}-\\d{2}-\\d{2})", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(description)
        return if (matcher.find()) matcher.group(1) else null
    }
}

object MissAvVideoUrls {

    fun setupPlayerWithSubtitle(
        context: Context,
        player: ExoPlayer,
        videoUrl: String,
        speed: Float = 1.0f,
        keepPosition: Boolean = true
    ) {
        val currentPosition = if (keepPosition) player.currentPosition else 0L
        val wasPlaying = player.playWhenReady

        val baseUrl = Preferences.missAvBaseUrl
        val dataSourceFactory = DefaultDataSource.Factory(
            context,
            DefaultHttpDataSource.Factory().setDefaultRequestProperties(
                hashMapOf("Referer" to "$baseUrl/", "Origin" to baseUrl.trimEnd('/'))
            )
        )
        val mediaItem = MediaItem.Builder().setUri(videoUrl).build()
        val mediaSource = if (videoUrl.contains(".m3u8")) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        } else {
            androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
        }

        player.setMediaSource(mediaSource)
        player.prepare()
        player.setPlaybackSpeed(speed)

        if (keepPosition && currentPosition > 0) {
            player.seekTo(currentPosition)
        }

        player.playWhenReady = wasPlaying
    }
}

const val MISSAV_QUALITY_AUTO = "Auto"

data class MissAvVariant(
    val height: Int,
    val bandwidth: Long,
    val url: String,
) {
    val label: String get() = "${height}P"
}

data class MissAvStreams(
    val masterUrl: String,
    val variants: List<MissAvVariant>,
) {
    val qualityMap: Map<String, String> by lazy {
        linkedMapOf<String, String>().apply {
            put(MISSAV_QUALITY_AUTO, masterUrl)
            variants.forEach { put(it.label, it.url) }
        }
    }

    fun downloadUrlFor(label: String): String? {
        qualityMap[label]?.takeIf { label != MISSAV_QUALITY_AUTO }?.let { return it }
        return variants.firstOrNull()?.url
    }

    fun downloadLabelFor(label: String): String =
        if (label == MISSAV_QUALITY_AUTO) variants.firstOrNull()?.label ?: label else label
}

object MissAvStreamResolver {

    private const val TAG = "MissAvStreamResolver"
    private val VARIANT_PATH = Regex("""^(.*)/(\d+)p/[^/?#]*\.m3u8(.*)$""")
    private val STREAM_INF_RESOLUTION = Regex("""RESOLUTION=(\d+)x(\d+)""", RegexOption.IGNORE_CASE)
    private val STREAM_INF_BANDWIDTH = Regex("""(?<![A-Z-])BANDWIDTH=(\d+)""", RegexOption.IGNORE_CASE)
    private val LEGACY_HEIGHTS = listOf(1080, 720, 480, 360)

    fun toMasterUrl(url: String): String {
        val m = VARIANT_PATH.find(url) ?: return url
        return m.groupValues[1] + "/playlist.m3u8" + m.groupValues[3]
    }

    fun parseMaster(text: String, masterUrl: String): List<MissAvVariant> {
        val lines = text.split("\n").map { it.trim() }
        val out = mutableListOf<MissAvVariant>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) {
                val height = STREAM_INF_RESOLUTION.find(line)?.groupValues?.get(2)?.toIntOrNull()
                val bandwidth = STREAM_INF_BANDWIDTH.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                var j = i + 1
                while (j < lines.size && (lines[j].isEmpty() || lines[j].startsWith("#"))) j++
                if (j < lines.size) {
                    val uri = lines[j]
                    val abs = runCatching { java.net.URI(masterUrl).resolve(uri).toString() }
                        .getOrDefault(uri)
                    val h = height ?: Regex("""(\d+)p""").find(uri)?.groupValues?.get(1)?.toIntOrNull()
                    if (h != null) out += MissAvVariant(h, bandwidth, abs)
                    i = j
                }
            }
            i++
        }
        return out.distinctBy { it.height }.sortedByDescending { it.height }
    }

    fun legacyStreams(capturedUrl: String): MissAvStreams {
        val master = toMasterUrl(capturedUrl)
        if (!VARIANT_PATH.containsMatchIn(capturedUrl) && !capturedUrl.endsWith("playlist.m3u8")) {
            return MissAvStreams(capturedUrl, emptyList())
        }
        val variants = LEGACY_HEIGHTS.map { h ->
            val base = master.substringBeforeLast("/playlist.m3u8")
            MissAvVariant(h, 0L, "$base/${h}p/video.m3u8")
        }
        return MissAvStreams(master, variants)
    }

    suspend fun resolve(capturedUrl: String): MissAvStreams = withContext(Dispatchers.IO) {
        val master = toMasterUrl(capturedUrl)
        runCatching {
            val base = Preferences.missAvBaseUrl.trimEnd('/')
            val client = MissAvNetwork.downloadOkHttpClient.newBuilder()
                .callTimeout(8, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder()
                .url(master)
                .header("Referer", "$base/")
                .header("Origin", base)
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val variants = parseMaster(resp.body.string(), master)
                if (variants.isEmpty()) error("no variants in master playlist")
                MissAvStreams(master, variants)
            }
        }.getOrElse { e ->
            android.util.Log.w(TAG, "Master playlist fetch failed, using fallback: ${e.message}")
            legacyStreams(capturedUrl)
        }
    }

    fun pickLabel(preference: String, streams: MissAvStreams): String {
        val labels = streams.qualityMap.keys.toList()
        if (preference.equals("auto", ignoreCase = true) || streams.variants.isEmpty()) {
            return labels.firstOrNull() ?: ""
        }
        val wanted = preference.toIntOrNull() ?: return labels.first()
        val heights = streams.variants.map { it.height }
        val best = heights.filter { it <= wanted }.maxOrNull()
            ?: heights.filter { it > wanted }.minOrNull()
            ?: return labels.first()
        return "${best}P"
    }
}
