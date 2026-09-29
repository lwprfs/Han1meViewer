package com.yenaly.han1meviewer.ui.view.video

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import cn.jzvd.JZMediaInterface
import cn.jzvd.JZMediaSystem
import cn.jzvd.Jzvd
import com.yenaly.han1meviewer.BuildConfig
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.USER_AGENT
import com.yenaly.han1meviewer.logic.network.HProxySelector
import com.yenaly.han1meviewer.util.AnimeShaders
import com.yenaly.han1meviewer.util.AnimeShaders.getCert
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import `is`.xyz.mpv.MPVLib
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.absoluteValue

sealed interface HMediaKernel {
    enum class Type(val clazz: Class<out JZMediaInterface>) {
        MediaPlayer(SystemMediaKernel::class.java),
        ExoPlayer(ExoMediaKernel::class.java),
        MpvPlayer(MpvMediaKernel::class.java);

        companion object {
            fun fromString(name: String): Type {
                return when (name) {
                    MediaPlayer.name -> MediaPlayer
                    ExoPlayer.name -> ExoPlayer
                    MpvPlayer.name -> MpvPlayer
                    else -> ExoPlayer
                }
            }
        }
    }
}

class ExoMediaKernel(jzvd: Jzvd) : JZMediaInterface(jzvd), Player.Listener, HMediaKernel {
    private var isActuallyPlaying = false
    private var lastBufferedPercent = -1
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            isActuallyPlaying = isPlaying
        }
        override fun onIsLoadingChanged(isLoading: Boolean) {
            val per = _exoPlayer?.bufferedPercentage ?: return
            if (per != lastBufferedPercent) {
                lastBufferedPercent = per
                jzvd.setBufferProgress(per)
            }
        }

        override fun onPlaybackStateChanged(state: Int) {

            val per = _exoPlayer?.bufferedPercentage ?: return
            jzvd.setBufferProgress(per)
        }
    }

    companion object {
        const val TAG = "ExoMediaKernel"
    }

    private var _exoPlayer: ExoPlayer? = null

    private val exoPlayer get() = _exoPlayer!!

    private var callback: Runnable? = null
    private var prevSeek = 0L

    var videoRealWidth: Int = 0
        private set
    var videoRealHeight: Int = 0
        private set
    @OptIn(UnstableApi::class)
    override fun prepare() {
        if (_exoPlayer != null) {
            Log.w(TAG, "prepare called, but player already exists.")
            return
        }
        Log.i(TAG, "prepare")
        val context = jzvd.context

        release()
        mMediaHandlerThread = HandlerThread("JZVD")
        mMediaHandlerThread.start()
        mMediaHandler = Handler(mMediaHandlerThread.looper)
        handler = Handler(Looper.getMainLooper())
        mMediaHandler?.post {
            val videoTrackSelectionFactory = AdaptiveTrackSelection.Factory()
            val trackSelector = DefaultTrackSelector(context, videoTrackSelectionFactory)

            val loadControl: LoadControl = DefaultLoadControl.Builder()

                .build()

            val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()

            val renderersFactory = DefaultRenderersFactory(context)
            _exoPlayer = ExoPlayer.Builder(context, renderersFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .setBandwidthMeter(bandwidthMeter)
                .build().apply {
                    addListener(playerListener)
                }

            val dataSourceFactory = DefaultDataSource.Factory(
                context,
                DefaultHttpDataSource.Factory()
                    .setDefaultRequestProperties(jzvd.jzDataSource.headerMap)
            )

            val currUrl = jzvd.jzDataSource.currentUrl.toString()
            val videoSource = if (currUrl.contains(".m3u8")) {
                HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(currUrl))
            } else {
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(currUrl))
            }

            Log.i(TAG, "URL Link = $currUrl")

            exoPlayer.addListener(this)

            val isLoop = jzvd.jzDataSource.looping
            if (isLoop) {
                exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
            } else {
                exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
            }
            exoPlayer.setMediaSource(videoSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true

            val surfaceTexture = jzvd.textureView?.surfaceTexture
            if (surfaceTexture == null) {
                Log.e(TAG, "❌ surfaceTexture is null, video surface not set")
            }
            surfaceTexture?.let { exoPlayer.setVideoSurface(Surface(it)) }
        }
    }

    override fun start() {
        mMediaHandler?.post {
            _exoPlayer?.playWhenReady = true
        }
    }

    fun setLooping(looping: Boolean) {
        mMediaHandler?.post {
            _exoPlayer?.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        val realWidth = videoSize.width * videoSize.pixelWidthHeightRatio
        val realHeight = videoSize.height
        videoRealWidth = realWidth.toInt()
        videoRealHeight = realHeight
        Log.i("JZVD-onVideoSizeChanged","realWidth:$realWidth,realHeight:$realHeight")
        handler.post {
            jzvd.onVideoSizeChanged(realWidth.toInt(), realHeight)
        }
        val ratio = realWidth / realHeight
        if (ratio > 1) {
            Jzvd.FULLSCREEN_ORIENTATION = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            Jzvd.FULLSCREEN_ORIENTATION = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    override fun onRenderedFirstFrame() {
        Log.i(TAG, "onRenderedFirstFrame")
    }

    override fun pause() {
        mMediaHandler?.post {
            _exoPlayer?.playWhenReady = false
        }
    }

    override fun isPlaying(): Boolean {
        return runOnPlayerThread{

            _exoPlayer?.playWhenReady
        } == true

    }

    override fun seekTo(time: Long) {
        mMediaHandler?.post {
            if (time != prevSeek) {
                _exoPlayer?.let { exoPlayer ->
                    if (time >= exoPlayer.bufferedPosition) {
                        jzvd.onStatePreparingPlaying()
                    }
                    exoPlayer.seekTo(time)
                    exoPlayer.playWhenReady = true
                    prevSeek = time
                    jzvd.seekToInAdvance = time
                }
            }
        }
    }

    override fun release() {
        if (mMediaHandler != null && mMediaHandlerThread != null && _exoPlayer != null) {
            val tmpHandlerThread = mMediaHandlerThread
            val tmpMediaPlayer = exoPlayer
            SAVED_SURFACE = null
            mMediaHandler?.post {
                tmpMediaPlayer.release()
                tmpHandlerThread.quit()
                _exoPlayer = null
            }
        }
    }

    inline fun <T> runOnPlayerThread(crossinline block: () -> T?): T? {
        if (Looper.myLooper() == mMediaHandler?.looper) {
            return block()
        }

        val result = AtomicReference<T?>()
        val latch = CountDownLatch(1)

        mMediaHandler?.post {
            try {
                result.set(block())
            } finally {
                latch.countDown()
            }
        }

        val finished = latch.await(300, TimeUnit.MILLISECONDS)
        return if (finished) result.get() else null
    }

    override fun getCurrentPosition(): Long {
        return runOnPlayerThread {
            _exoPlayer?.currentPosition
        } ?: 0L
    }

    override fun getDuration(): Long {
        return runOnPlayerThread {
            _exoPlayer?.duration
        } ?: 0L
    }

    override fun setVolume(leftVolume: Float, rightVolume: Float) {
        mMediaHandler?.post {
            _exoPlayer?.volume = (leftVolume + rightVolume) / 2
        }
    }

    override fun setSpeed(speed: Float) {
        mMediaHandler?.post {
            val playbackParams = PlaybackParameters(speed, 1.0F)
            _exoPlayer?.playbackParameters = playbackParams
        }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        Log.i(TAG, "onTimelineChanged")
    }

    override fun onIsLoadingChanged(isLoading: Boolean) {
        Log.i(TAG, "onIsLoadingChanged")
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        Log.i(TAG, "onPlayWhenReadyChanged: $playWhenReady, reason: $reason, " +
                "playbackState=${_exoPlayer?.playbackState}")
        if (playWhenReady && _exoPlayer?.playbackState == Player.STATE_READY) {
            handler?.post {
                Log.i(TAG,"onPlayWhenReadyChanged_ready")
                jzvd.onStatePlaying()
            }
        }
        Log.i(TAG,"onPlayWhenReadyChanged")
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        Log.i(TAG, "onPlaybackStateChanged: $playbackState")
        handler?.post {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    jzvd.onStatePreparingPlaying()
                    callback?.let(handler::post)
                }

                Player.STATE_READY -> {

                    runOnPlayerThread {
                        Log.i(TAG, "STATE_READY, playWhenReady=${_exoPlayer?.playWhenReady}")
                        if (_exoPlayer?.playWhenReady == true){
                            jzvd.onStatePlaying()
                        }
                    }
                }

                Player.STATE_ENDED -> {
                    jzvd.onCompletion()
                }

                else -> {
                    Log.i(TAG, "onPlaybackStateChanged: $playbackState")
                }
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        Log.e(TAG, "onPlayerError: $error")
        handler?.post { jzvd.onError(1000, 1000) }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            handler?.post {
                jzvd.onSeekComplete()
                runOnPlayerThread {
                    if (_exoPlayer?.playWhenReady==true) {
                        jzvd.onStatePlaying()
                    }
                }

            }
        }
    }

    override fun setSurface(surface: Surface?) {
        Log.e(TAG, "setSurface: $surface")
        _exoPlayer?.setVideoSurface(surface)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (SAVED_SURFACE == null) {
            SAVED_SURFACE = surface
            Log.d(TAG, "onSurfaceTextureAvailable: $SAVED_SURFACE $width $height")
            if (_exoPlayer == null) {
                prepare()
            } else {
                _exoPlayer?.setVideoSurface(Surface(surface))
            }
        } else {
            jzvd.textureView.setSurfaceTexture(SAVED_SURFACE)
        }
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

}

class SystemMediaKernel(jzvd: Jzvd) : JZMediaSystem(jzvd), HMediaKernel {

    val videoRealWidth: Int get() = mediaPlayer?.videoWidth ?: 0
    val videoRealHeight: Int get() = mediaPlayer?.videoHeight ?: 0
    override fun setSpeed(speed: Float) {
        mMediaHandler?.post {
            try {
                val pp = mediaPlayer.playbackParams
                pp.speed = speed.absoluteValue
                mediaPlayer.playbackParams = pp
            } catch (_: IllegalArgumentException) {
                try {
                    val opp = PlaybackParams().setSpeed(speed.absoluteValue)
                    mediaPlayer.playbackParams = opp
                } catch (e: IllegalArgumentException) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onVideoSizeChanged(mediaPlayer: MediaPlayer?, width: Int, height: Int) {
        super.onVideoSizeChanged(mediaPlayer, width, height)
        val ratio = width.toFloat() / height
        if (ratio > 1) {
            Jzvd.FULLSCREEN_ORIENTATION = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            Jzvd.FULLSCREEN_ORIENTATION = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    override fun pause() {
        mMediaHandler?.post {
            mediaPlayer?.pause()
        }
    }

    override fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }

    fun setLooping(looping: Boolean) {
        mMediaHandler?.post {
            mediaPlayer?.isLooping = looping
        }
    }
}

class MpvMediaKernel(jzvd: Jzvd) : JZMediaInterface(jzvd) {
    companion object {
        const val TAG = "MpvMediaKernel"
    }
    val videoRealWidth: Int
        get() = MPVLib.getPropertyInt("video-params/w") ?: 0

    val videoRealHeight: Int
        get() = MPVLib.getPropertyInt("video-params/h") ?: 0

    private var mpvTimePos: Double = 0.0
    private var mpvCacheDuration: Double = 0.0
    private var mpvDuration: Double = 0.0
    private var currentPfd: ParcelFileDescriptor? = null
    private var detachFd: Int? = null
    private var pfdFilePath = false
    private val certFile = getCert(jzvd.context)
    val defaultSpeed = Preferences.playerSpeed
    private val mpvOptions: Map<String, String>
        get() = buildMap {

            if (Preferences.enableGPUNextRenderer){
                put("vo", "gpu-next")
            } else {
                put("vo", "gpu")
            }

            put("profile", when (Preferences.mpvProfile) {
                "gpu-hq" -> "gpu-hq"
                "fast" -> "fast"
                else -> "default"
            })

            put("hwdec", when (Preferences.mpvHwdec) {
                "Auto" -> "auto"
                "HW" -> "mediacodec-copy"
                "HW+" -> "mediacodec"
                "Vulkan" -> "vulkan-copy"
                "vulkan+" -> "vulkan"
                "SW" -> "no"
                else -> "auto"
            })

            put("msg-level", "all=" + if (BuildConfig.DEBUG) "debug" else "warn")

            if (Preferences.mpvInterpolation) {
                put("interpolation", "yes")
                put("tscale", "oversample")
                put("video-sync", "display-resample")
            }

            put("cache", "yes")
            put("cache-secs", Preferences.mpvCacheSecs.toString())
            put("vd-lavc-threads", Runtime.getRuntime().availableProcessors().toString())

            if (Preferences.mpvFramedrop) {
                put("framedrop", "vo")
            } else {
                put("framedrop", "no")
            }

            put("deband", if (Preferences.mpvDeband) "yes" else "no")

            put("cache-pause", "no")

            put("network-timeout", Preferences.mpvNetworkTimeout.toString())

            put("tls-ca-file", certFile)

            put("tls-verify", if (Preferences.mpvTlsVerify) "no" else "yes")

            val proxyIp = Preferences.proxyIp
            val proxyPort = Preferences.proxyPort
            if (proxyIp.isNotBlank() && proxyPort != -1) {
                val proxyUrl = "${proxyIp}:${proxyPort}"
                if (Preferences.proxyType == HProxySelector.TYPE_HTTP) {
                    put("http-proxy", "http://$proxyUrl")
                }
            }
            put("user-agent", USER_AGENT)
        }

    private val observedProperties = listOf(
        "time-pos" to MPVLib.mpvFormat.MPV_FORMAT_DOUBLE,
        "duration" to MPVLib.mpvFormat.MPV_FORMAT_DOUBLE,
        "pause" to MPVLib.mpvFormat.MPV_FORMAT_FLAG,
        "playback-active" to MPVLib.mpvFormat.MPV_FORMAT_FLAG,
        "video-params/w" to MPVLib.mpvFormat.MPV_FORMAT_INT64,
        "video-params/h" to MPVLib.mpvFormat.MPV_FORMAT_INT64,
        "demuxer-cache-duration" to MPVLib.mpvFormat.MPV_FORMAT_DOUBLE
    )
    fun parseCustomMpvParams(): LinkedHashMap<String, String> {
        val rawInput = Preferences.customMpvParams
        val map = linkedMapOf<String, String>()

        rawInput.split(";").forEach { entry ->
            val trimmedEntry = entry.trim()
            if (trimmedEntry.isEmpty() || trimmedEntry.startsWith("#")) return@forEach

            val parts = trimmedEntry.split(",", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    map[key] = value
                }
            }
        }
        return map
    }

    fun init() {
        mpvOptions.forEach { (key, value) ->
            Log.i("MPV_config","$key,$value")
            MPVLib.setOptionString(key, value)
        }
        try {
            val customParams = parseCustomMpvParams()
            customParams.forEach { (key, value) ->
                Log.i("MPV_config","custom: $key,$value")
                MPVLib.setOptionString(key, value)
            }
        } catch (e: Exception){
            GlobalToasts.show(e.message.orEmpty(), level = GlobalToasts.ToastLevel.ERROR)
        }

        observedProperties.forEach { (name, type) ->
            MPVLib.observeProperty(name, type)
        }
        MPVLib.addObserver(mpvEventObserver)
    }

    fun prepareUri(context: Context, uri: Uri): String? {
        return when (uri.scheme) {
            "http", "https" -> {
                uri.toString()
            }
            "file", "content" -> {
                try {
                    currentPfd = context.contentResolver.openFileDescriptor(uri, "r")
                    detachFd = currentPfd?.detachFd()
                    Log.d(TAG, "Detached FD = $detachFd")
                    pfdFilePath = true
                    if (detachFd != null) {
                        "fd://$detachFd"
                    } else null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            else -> {
                null
            }
        }
    }

    fun releaseCurrentPfd(from: String? = null) {
        if (!pfdFilePath) return
        try {
            currentPfd?.let {
                Log.i(TAG, "Closing currentPfd: $it")
                it.close()
            }

            detachFd?.let { fd ->
                Log.i(TAG, "Closing detachFd: $fd")
                try {
                    ParcelFileDescriptor.adoptFd(fd).close()
                } catch (e: Exception) {
                    Log.w(TAG, "detachFd $fd already closed or invalid", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing PFD", e)
        } finally {
            currentPfd = null
            detachFd = null
            handler?.postDelayed({
                Log.i(TAG, "${from ?: "releaseCurrentPfd"} completed. PFD cleared.")
            }, 200)
        }
    }

    override fun prepare() {
        init()
        handler = Handler(Looper.getMainLooper())

        val url = jzvd.jzDataSource.currentUrl.toString()
        if (url.isEmpty()) {
            Log.e(TAG, "视频链接为空")
            return
        }

        Log.e(TAG, "URL Link = $url")
        MPVLib.setOptionString("force-window", "yes")

        val uri = url.toUri()
        val path = prepareUri(jzvd.context, uri)
        if (path != null) {
            MPVLib.command(arrayOf("loadfile", path))
            val surfaceTexture = jzvd.textureView?.surfaceTexture
            surfaceTexture?.let { MPVLib.attachSurface(Surface(it)) }
        }
    }

    override fun start() {
        MPVLib.setPropertyBoolean("pause", false)
    }

    fun setLooping(looping: Boolean) {
        MPVLib.setPropertyString("loop-file", if (looping) "inf" else "no")
    }

    override fun pause() {
        MPVLib.setPropertyBoolean("pause", true)
    }

    override fun isPlaying(): Boolean {
        val pause = MPVLib.getPropertyBoolean("pause")
        return !pause
    }

    override fun seekTo(time: Long) {
        Log.i("seekTo","${ (time / 1000.0)}")
        MPVLib.command(arrayOf("seek", (time / 1000.0).toString(), "absolute", "exact"))
    }

    override fun release() {
        clearSuperResolution()
        MPVLib.setPropertyBoolean("pause", true)
        MPVLib.command(arrayOf("loadfile", "", "replace"))
        MPVLib.setPropertyString("vo", "null")
        MPVLib.setOptionString("force-window", "no")
        MPVLib.detachSurface()
        MPVLib.removeObserver(mpvEventObserver)
        handler?.postDelayed({
            releaseCurrentPfd("jzvd-release")
        }, 200)
        SAVED_SURFACE = null
    }

    override fun getCurrentPosition(): Long {
        val timePosSeconds = MPVLib.getPropertyDouble("time-pos")
        return (timePosSeconds ?: 0.0).toLong() * 1000
    }

    override fun getDuration(): Long {
        val durationSeconds = MPVLib.getPropertyDouble("duration")
        return (durationSeconds ?: 0.0).toLong() * 1000
    }

    override fun setVolume(leftVolume: Float, rightVolume: Float) {
        val volume = (leftVolume + rightVolume) / 2 * 100
        MPVLib.setPropertyDouble("volume", volume.toDouble())
    }

    override fun setSpeed(speed: Float) {
        MPVLib.setPropertyDouble("speed", speed.toDouble())
    }

    override fun setSurface(surface: Surface?) {
        MPVLib.attachSurface(surface)
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        if (SAVED_SURFACE == null) {
            SAVED_SURFACE = surfaceTexture
            prepare()
        } else {
            jzvd.textureView.setSurfaceTexture(SAVED_SURFACE)
        }
    }

    fun updateSurFaceSize(width: Int, height: Int) {
        Log.d(TAG, "updateSurFaceSize ${width}x${height}")
        MPVLib.setPropertyString("android-surface-size", "${width}x${height}")
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceTextureSizeChanged ${width}x${height}")
        updateSurFaceSize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean = false

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}

    private fun clearSuperResolution() {
        MPVLib.command(arrayOf("change-list", "glsl-shaders", "clr", ""))
    }
    fun setSuperResolution(index: Int) {
        if (index != 0) {
            val cmd = arrayOf("change-list", "glsl-shaders", "set", AnimeShaders.getShader(jzvd.context, index))
            MPVLib.command(cmd)
        } else {
            clearSuperResolution()
        }
    }

    private val mpvEventObserver = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) {

        }

        override fun eventProperty(property: String, value: Long) {

        }

        override fun eventProperty(property: String, value: Boolean) {

        }

        override fun eventProperty(property: String, value: String) {

        }

        override fun eventProperty(property: String, value: Double) {

            when (property) {
                "time-pos" -> mpvTimePos = value
                "demuxer-cache-duration" -> mpvCacheDuration = value
                "duration" -> mpvDuration = value
            }
            if (mpvDuration > 0) {
                val bufferedTime = mpvTimePos + mpvCacheDuration
                val safeBufferedTime = bufferedTime.coerceAtMost(mpvDuration)
                val bufferPercent = ((safeBufferedTime / mpvDuration) * 100).toInt()
                handler.post {
                    jzvd.setBufferProgress(bufferPercent)
                }
            }

        }

        override fun event(eventId: Int) {
            handler.post {
                when (eventId) {
                    MPVLib.mpvEventId.MPV_EVENT_START_FILE -> {

                        mpvTimePos = 0.0
                        mpvCacheDuration = 0.0
                        mpvDuration = 0.0
                        jzvd.onStatePreparing()
                    }
                    MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> {

                        jzvd.onPrepared()
                        MPVLib.setPropertyDouble("speed", defaultSpeed.toDouble())
                    }
                    MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART -> {

                        jzvd.onStatePlaying()
                    }
                    MPVLib.mpvEventId.MPV_EVENT_END_FILE -> {

                        releaseCurrentPfd("MPV_EVENT_END_FILE")
                        jzvd.onCompletion()
                    }
                    MPVLib.mpvEventId.MPV_EVENT_SHUTDOWN -> {
                        Log.e(TAG, "event: $eventId")
                    }
                }
            }
        }
    }
}
