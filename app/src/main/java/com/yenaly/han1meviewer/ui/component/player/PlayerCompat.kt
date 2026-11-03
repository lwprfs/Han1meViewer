package com.yenaly.han1meviewer.ui.component.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.navigation.main.VideoRoute
import com.yenaly.han1meviewer.ui.navigation.navigateSafely
import java.util.concurrent.ConcurrentHashMap

/*
 * Why this exists
 * ---------------
 * Some sites (HentaiMama's gdvid.info) serve 10-bit H.264 (High 4:4:4 Predictive). Android's
 * hardware AVC decoders and the AOSP software decoder only handle 8-bit, so ExoPlayer/MediaCodec
 * fails with NO_EXCEEDS_CAPABILITIES. mpv (bundled with the app, FFmpeg-based) decodes anything,
 * so whenever Media3 reports "this device cannot decode the stream" we hand playback to mpv
 * automatically instead of showing a dead player. All ExoPlayer instances in the app should be
 * created with [createSitePlayer] and report errors through [PlaybackException.isDecoderProblem].
 */

/** Use instead of `ExoPlayer.Builder(context).build()`. */
@OptIn(UnstableApi::class)
fun createSitePlayer(context: Context): ExoPlayer {
    val renderers = DefaultRenderersFactory(context)
        // Uses an FFmpeg renderer if one is ever added to the classpath; harmless otherwise.
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        .setEnableDecoderFallback(true)
    return ExoPlayer.Builder(context, renderers).build()
}

/** True when the device cannot decode the stream (as opposed to a network problem). */
fun PlaybackException.isDecoderProblem(): Boolean = when (errorCode) {
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> true
    else -> false
}

object StreamFallback {
    private val mpvUrls = ConcurrentHashMap.newKeySet<String>()
    private val referers = ConcurrentHashMap<String, String>()

    fun shouldUseMpv(url: String?): Boolean = url != null && url in mpvUrls

    fun refererFor(url: String?): String? = url?.let { referers[it] }

    /**
     * Opens [url] in the app's mpv-based player. Returns false when it can't navigate
     * (caller should then show its own error UI).
     */
    fun playInMpv(context: Context, url: String, referer: String? = null): Boolean {
        val activity = context.findHostActivity() as? MainActivity ?: return false
        mpvUrls += url
        if (!referer.isNullOrBlank()) referers[url] = referer
        activity.navController.navigateSafely(
            VideoRoute(videoCode = "-1", localUri = url, autoPlay = true)
        )
        return true
    }
}

/** Last resort: hand the stream to VLC / mpv / MX Player etc. */
fun openInExternalPlayer(context: Context, url: String) {
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(url.toUri(), "video/*")
    val chooser = Intent.createChooser(view, null)
    if (context.findHostActivity() == null) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(chooser) }
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}
