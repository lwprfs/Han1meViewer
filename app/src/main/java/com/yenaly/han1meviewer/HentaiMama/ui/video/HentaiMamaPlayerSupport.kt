package com.yenaly.han1meviewer.HentaiMama.ui.video

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import com.yenaly.han1meviewer.ui.component.player.createSitePlayer
import com.yenaly.han1meviewer.ui.component.player.isDecoderProblem
import com.yenaly.han1meviewer.ui.component.player.openInExternalPlayer

/** Thin wrappers over the shared helpers in `ui.component.player.PlayerCompat`. */
internal fun createHentaiMamaPlayer(context: Context): ExoPlayer = createSitePlayer(context)

internal fun PlaybackException.isHentaiMamaDecoderProblem(): Boolean = isDecoderProblem()

internal fun openHentaiMamaExternal(context: Context, url: String) =
    openInExternalPlayer(context, url)
