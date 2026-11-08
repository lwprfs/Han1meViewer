package com.yenaly.han1meviewer.RouVideo.ui.video

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import com.yenaly.han1meviewer.ui.component.player.createSitePlayer
import com.yenaly.han1meviewer.ui.component.player.isDecoderProblem
import com.yenaly.han1meviewer.ui.component.player.openInExternalPlayer

internal fun createRouVideoPlayer(context: Context): ExoPlayer = createSitePlayer(context)

internal fun PlaybackException.isRouVideoDecoderProblem(): Boolean = isDecoderProblem()

internal fun openRouVideoExternal(context: Context, url: String) =
    openInExternalPlayer(context, url)
