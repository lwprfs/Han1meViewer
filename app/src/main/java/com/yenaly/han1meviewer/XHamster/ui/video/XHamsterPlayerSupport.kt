package com.yenaly.han1meviewer.XHamster.ui.video

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import com.yenaly.han1meviewer.ui.component.player.createSitePlayer
import com.yenaly.han1meviewer.ui.component.player.isDecoderProblem
import com.yenaly.han1meviewer.ui.component.player.openInExternalPlayer

internal fun createXHamsterPlayer(context: Context): ExoPlayer = createSitePlayer(context)

internal fun PlaybackException.isXHamsterDecoderProblem(): Boolean = isDecoderProblem()

internal fun openXHamsterExternal(context: Context, url: String) =
    openInExternalPlayer(context, url)
