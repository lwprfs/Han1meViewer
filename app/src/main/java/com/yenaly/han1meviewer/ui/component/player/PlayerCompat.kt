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

@OptIn(UnstableApi::class)
fun createSitePlayer(context: Context): ExoPlayer {
    val renderers = DefaultRenderersFactory(context)

        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        .setEnableDecoderFallback(true)
    return ExoPlayer.Builder(context, renderers).build()
}

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
