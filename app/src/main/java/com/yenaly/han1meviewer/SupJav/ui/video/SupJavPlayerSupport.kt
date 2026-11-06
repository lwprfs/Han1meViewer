package com.yenaly.han1meviewer.SupJav.ui.video

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import com.yenaly.han1meviewer.ui.component.player.createSitePlayer
import com.yenaly.han1meviewer.ui.component.player.isDecoderProblem
import com.yenaly.han1meviewer.ui.component.player.openInExternalPlayer

internal fun createSupJavPlayer(context: Context): ExoPlayer = createSitePlayer(context)

internal fun PlaybackException.isSupJavDecoderProblem(): Boolean = isDecoderProblem()

internal fun openSupJavExternal(context: Context, url: String) =
    openInExternalPlayer(context, url)
