package com.yenaly.han1meviewer.OnAir.ui.video

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

object OnAirSubtitleHelper {

    private const val TAG = "OnAirSubtitleHelper"

    fun parseSrt(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val lines = content.replace("\r\n", "\n").replace('\r', '\n').lines()
        var i = 0
        while (i < lines.size) {
            while (i < lines.size && lines[i].isBlank()) i++
            if (i >= lines.size) break
            if (lines[i].trim().matches(Regex("^\\d+$"))) i++
            if (i >= lines.size) break
            val timeLine = lines[i]
            val match = Regex("""(\d{2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,.](\d{3})""")
                .find(timeLine)
            if (match == null) {
                i++
                continue
            }
            val start = toMillis(match.groupValues[1], match.groupValues[2], match.groupValues[3], match.groupValues[4])
            val end = toMillis(match.groupValues[5], match.groupValues[6], match.groupValues[7], match.groupValues[8])
            i++
            val textLines = mutableListOf<String>()
            while (i < lines.size && lines[i].isNotBlank()) {
                textLines.add(lines[i].trim())
                i++
            }
            if (textLines.isNotEmpty()) {
                cues.add(SubtitleCue(start, end, textLines.joinToString("\n")))
            }
        }
        return cues.sortedBy { it.startMs }
    }

    private fun toMillis(h: String, m: String, s: String, ms: String): Long {
        val hh = h.toLongOrNull() ?: 0L
        val mm = m.toLongOrNull() ?: 0L
        val ss = s.toLongOrNull() ?: 0L
        val mss = ms.toLongOrNull() ?: 0L
        return hh * 3600_000L + mm * 60_000L + ss * 1_000L + mss
    }

    suspend fun downloadSubtitle(
        context: Context,
        url: String,
        fileName: String,
    ): Uri? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        runCatching {
            val safe = fileName
                .replace(Regex("""[\\/:*?"<>|]"""), "_")
                .take(100)
                .ifBlank { "onair_subtitle.srt" }
            val dir = File(context.getExternalFilesDir("subtitles"), "onair")
            if (!dir.exists() && !dir.mkdirs()) return@withContext null
            val file = File(dir, safe)

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android) OnAirViewer")
                .build()
            val bytes = com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetwork.sharedOkHttpClient
                .newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body.bytes()
                } ?: return@withContext null
            if (bytes.isEmpty()) return@withContext null

            FileOutputStream(file).use { it.write(bytes) }

            val authority = "${context.packageName}.fileProvider"
            FileProvider.getUriForFile(context, authority, file)
        }.onFailure { Log.e(TAG, "downloadSubtitle failed: ${it.message}") }
            .getOrNull()
    }

    fun loadLocalSubtitle(context: Context, uri: Uri): List<SubtitleCue>? {
        return runCatching {
            val text = when (uri.scheme) {
                "file" -> File(URLDecoder.decode(uri.path.orEmpty(), StandardCharsets.UTF_8.name()))
                    .takeIf { it.exists() }?.readText(Charsets.UTF_8)
                else -> context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            } ?: return null
            parseSrt(text)
        }.onFailure { Log.e(TAG, "loadLocalSubtitle failed: ${it.message}") }
            .getOrNull()
    }

    fun cueAt(cues: List<SubtitleCue>, positionMs: Long): String? {
        if (cues.isEmpty()) return null
        return cues.firstOrNull { positionMs in it.startMs..it.endMs }?.text
    }
}
