package com.yenaly.han1meviewer.ui.component.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.util.applicationContext

object PlayerMediaControl {

    object Settings {
        private const val K_ENABLED = "player_media_enabled"
        private const val K_PLAY_PAUSE = "player_media_play_pause"
        private const val K_VOLUME = "player_media_volume_keys"
        private const val K_NEXT_PREV = "player_media_next_prev"
        private const val K_FF_REW = "player_media_ff_rew"
        private const val K_NOISY = "player_media_pause_noisy"
        private const val K_INFO = "player_media_share_info"
        private const val K_STEP = "player_media_seek_step_sec"

        private val sp get() = Preferences.preferenceSp

        private fun get(key: String) = sp.getBoolean(key, true)
        private fun set(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()

        var enabled: Boolean
            get() = get(K_ENABLED)
            set(v) { set(K_ENABLED, v); update() }
        var playPause: Boolean
            get() = get(K_PLAY_PAUSE)
            set(v) = set(K_PLAY_PAUSE, v)
        var volumeKeys: Boolean
            get() = get(K_VOLUME)
            set(v) = set(K_VOLUME, v)
        var nextPrevious: Boolean
            get() = get(K_NEXT_PREV)
            set(v) = set(K_NEXT_PREV, v)
        var fastForwardRewind: Boolean
            get() = get(K_FF_REW)
            set(v) = set(K_FF_REW, v)
        var pauseWhenNoisy: Boolean
            get() = get(K_NOISY)
            set(v) = set(K_NOISY, v)
        var shareInfo: Boolean
            get() = get(K_INFO)
            set(v) { set(K_INFO, v); update() }
        var seekStepSec: Int
            get() = sp.getInt(K_STEP, 10).takeIf { it in SEEK_STEPS } ?: 10
            set(v) = sp.edit().putInt(K_STEP, v).apply()

        val SEEK_STEPS = listOf(5, 10, 15, 30)
    }

    class Controller(
        val title: () -> String,
        val isPlaying: () -> Boolean,
        val positionMs: () -> Long,
        val durationMs: () -> Long,
        val play: () -> Unit,
        val pause: () -> Unit,
        val seekBy: (Long) -> Unit,

        val previous: (() -> Unit)? = null,
        val next: (() -> Unit)? = null,
    )

    private val stack = ArrayList<Controller>()
    private val main = Handler(Looper.getMainLooper())
    private var session: MediaSession? = null
    private var noisyRegistered = false
    private var lastKey = 0
    private var lastKeyAt = 0L

    private val active: Controller? get() = stack.lastOrNull()

    fun register(controller: Controller) {
        stack.remove(controller)
        stack.add(controller)
        ensureSession()
        registerNoisy()
        update()
    }

    fun unregister(controller: Controller) {
        stack.remove(controller)
        if (stack.isEmpty()) unregisterNoisy()
        update()
    }

    fun update() {
        val s = session ?: return
        val c = active
        if (c == null || !Settings.enabled) {
            if (s.isActive) s.isActive = false
            return
        }
        val playing = c.isPlaying()
        var actions = 0L
        if (Settings.playPause) {
            actions = actions or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP
        }
        if (Settings.nextPrevious) {
            actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        }
        if (Settings.fastForwardRewind) {
            actions = actions or PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND
        }
        actions = actions or PlaybackState.ACTION_SEEK_TO
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    c.positionMs().coerceAtLeast(0L),
                    if (playing) 1f else 0f,
                    SystemClock.elapsedRealtime(),
                )
                .build()
        )
        val meta = MediaMetadata.Builder()
        if (Settings.shareInfo) {
            meta.putString(MediaMetadata.METADATA_KEY_TITLE, c.title())
            meta.putLong(MediaMetadata.METADATA_KEY_DURATION, c.durationMs().coerceAtLeast(0L))
        }
        s.setMetadata(meta.build())
        if (!s.isActive) s.isActive = true
    }

    fun handleKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (!isHandledKey(code)) return false
        if (active == null || !Settings.enabled) return false

        if (event.action != KeyEvent.ACTION_DOWN) return wouldHandle(code)
        if (event.repeatCount > 0 && code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return wouldHandle(code)
        }
        return handleCommand(code)
    }

    private fun isHandledKey(code: Int) = when (code) {
        KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> true
        else -> false
    }

    private fun wouldHandle(code: Int) = when (code) {
        KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_STOP -> Settings.playPause
        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> Settings.nextPrevious
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND -> Settings.fastForwardRewind
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> Settings.volumeKeys
        else -> false
    }

    private fun handleCommand(code: Int): Boolean {
        val c = active ?: return false
        if (!Settings.enabled || !wouldHandle(code)) return false

        val now = SystemClock.uptimeMillis()
        val isVolume = code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
        if (!isVolume && code == lastKey && now - lastKeyAt < 250) return true
        lastKey = code
        lastKeyAt = now

        val step = Settings.seekStepSec * 1000L
        when (code) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> if (!c.isPlaying()) c.play()
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> if (c.isPlaying()) c.pause()
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK ->
                if (c.isPlaying()) c.pause() else c.play()
            KeyEvent.KEYCODE_MEDIA_NEXT -> c.next?.invoke() ?: c.seekBy(step)
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> c.previous?.invoke() ?: c.seekBy(-step)
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> c.seekBy(step)
            KeyEvent.KEYCODE_MEDIA_REWIND -> c.seekBy(-step)
            KeyEvent.KEYCODE_VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
            KeyEvent.KEYCODE_VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
        }
        main.post { update() }
        return true
    }

    private fun adjustVolume(direction: Int) {
        val am = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    private fun ensureSession() {
        if (session != null) return
        val s = MediaSession(applicationContext, "HanimeVideoPlayer")
        @Suppress("DEPRECATION")
        s.setFlags(
            MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        s.setPlaybackToLocal(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()
        )
        s.setCallback(object : MediaSession.Callback() {
            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                val event = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                }) ?: return false
                if (!isHandledKey(event.keyCode)) return false
                if (event.action != KeyEvent.ACTION_DOWN) return wouldHandle(event.keyCode)
                return handleCommand(event.keyCode)
            }

            override fun onPlay() { handleCommand(KeyEvent.KEYCODE_MEDIA_PLAY) }
            override fun onPause() { handleCommand(KeyEvent.KEYCODE_MEDIA_PAUSE) }
            override fun onStop() { handleCommand(KeyEvent.KEYCODE_MEDIA_STOP) }
            override fun onSkipToNext() { handleCommand(KeyEvent.KEYCODE_MEDIA_NEXT) }
            override fun onSkipToPrevious() { handleCommand(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
            override fun onFastForward() { handleCommand(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) }
            override fun onRewind() { handleCommand(KeyEvent.KEYCODE_MEDIA_REWIND) }
            override fun onSeekTo(pos: Long) {
                val c = active ?: return
                c.seekBy(pos - c.positionMs())
                main.post { update() }
            }
        }, main)
        session = s
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            val c = active ?: return
            if (Settings.enabled && Settings.pauseWhenNoisy && c.isPlaying()) {
                c.pause()
                main.post { update() }
            }
        }
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        ContextCompat.registerReceiver(
            applicationContext, noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyRegistered = true
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        runCatching { applicationContext.unregisterReceiver(noisyReceiver) }
        noisyRegistered = false
    }
}
