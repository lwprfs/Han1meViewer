package com.yenaly.han1meviewer.HentaiMama.ui.video

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

/**
 * HentaiMama's gdvid.info files are 10-bit H.264 (High 4:4:4 Predictive, e.g. avc1.F40028).
 * Android's hardware AVC decoders and the AOSP software decoder only handle 8-bit, so
 * MediaCodec fails with NO_EXCEEDS_CAPABILITIES. Downloading works (plain byte copy).
 */

/** Use instead of `ExoPlayer.Builder(context).build()`. */
@OptIn(UnstableApi::class)
internal fun createHentaiMamaPlayer(context: Context): ExoPlayer {
    val renderers = DefaultRenderersFactory(context)
        // Uses an FFmpeg renderer if one is on the classpath, otherwise a harmless no-op.
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        .setEnableDecoderFallback(true)
    return ExoPlayer.Builder(context, renderers).build()
}

/** True when the device cannot decode the stream (as opposed to a network problem). */
internal fun PlaybackException.isDecoderProblem(): Boolean = when (errorCode) {
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> true
    else -> false
}

/** Hands the stream to VLC / mpv / MX Player etc. */
internal fun openInExternalPlayer(context: Context, url: String) {
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
