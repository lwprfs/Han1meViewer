package com.yenaly.han1meviewer.MissAV

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