package com.yenaly.han1meviewer.ui.view.video

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.IntRange
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.getSystemService
import androidx.core.graphics.toColorInt
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.size
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.jzvd.JZDataSource
import cn.jzvd.JZMediaInterface
import cn.jzvd.JZUtils
import cn.jzvd.JzvdStd
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.itxca.spannablex.spannable
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.entity.HKeyframeEntity
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.adapter.HKeyframesRvAdapter
import com.yenaly.han1meviewer.ui.adapter.SuperResolutionAdapter
import com.yenaly.han1meviewer.ui.adapter.VideoSpeedAdapter
import com.yenaly.han1meviewer.ui.component.GlobalDialogs
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import com.yenaly.han1meviewer.ui.navigation.main.HomeRoute
import com.yenaly.han1meviewer.util.setStateViewLayout
import com.yenaly.yenaly_libs.utils.OrientationManager
import com.yenaly.yenaly_libs.utils.appScreenWidth
import com.yenaly.yenaly_libs.utils.findActivityOrNull
import com.yenaly.yenaly_libs.utils.navBarHeight
import com.yenaly.yenaly_libs.utils.statusBarHeight
import com.yenaly.yenaly_libs.utils.unsafeLazy
import com.yenaly.yenaly_libs.utils.view.removeItself
import java.util.Timer
import kotlin.math.absoluteValue

class HJzvdStd @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : JzvdStd(context, attrs), OnLongClickListener {
    interface FullscreenListener {
        fun onFullscreenChanged(isFullscreen: Boolean)
    }
    var fullscreenListener: FullscreenListener? = null

    private val dialogAccentColor: Int
        get() = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)

    companion object {

        const val THRESHOLD = 10

        const val DEF_PROGRESS_SLIDE_SENSITIVITY = 5

        const val DEF_COUNTDOWN_SEC = 10

        const val DEF_SPEED = 1.0F

        const val DEF_SPEED_INDEX = 2

        const val DEF_LONG_PRESS_SPEED_TIMES = 2.5F

        val speedArray = floatArrayOf(
            0.5F, 0.75F,
            1.0F, 1.25F, 1.5F, 1.75F,
            2.0F, 2.25F, 2.5F, 2.75F,
            3.0F,
        )

        val speedStringArray = Array(speedArray.size) { "${speedArray[it]}x" }
        const val DEF_SUPER_RESOLUTION_INDEX = 0
    }

    init {
        gestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (state == STATE_PLAYING || state == STATE_PAUSE) {
                        Log.d(TAG, "doubleClick [" + this.hashCode() + "] ")
                        startButton.performClick()
                    }
                    return super.onDoubleTap(e)
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (!mChangeBrightness && !mChangeVolume) {
                        onClickUiToggle()
                    }
                    return super.onSingleTapConfirmed(e)
                }
            })
    }

    private val showBottomProgress = Preferences.showBottomProgress

    private val userDefSpeed = Preferences.playerSpeed

    private val userDefSpeedIndex = speedArray.indexOfFirst { it == userDefSpeed }

    private val userDefSlideSensitivity = Preferences.slideSensitivity.toRealSensitivity()

    private val userDefLongPressSpeedTimes = Preferences.longPressSpeedTime

    private val userDefWhenCountdownRemind = Preferences.whenCountdownRemind

    private val userDefShowCommentWhenCountdown = Preferences.showCommentWhenCountdown

    private val isHKeyframeEnabled = Preferences.hKeyframesEnable

    private var currentSpeedIndex = userDefSpeedIndex
        @SuppressLint("SetTextI18n")
        set(value) {
            field = value

            val updateText = {
                tvSpeed.text = if (value == DEF_SPEED_INDEX) {
                    context.getString(R.string.speed)
                } else {
                    speedStringArray[value]
                }
            }

            if (Looper.myLooper() == Looper.getMainLooper()) {
                updateText()
            } else {
                tvSpeed.post(updateText)
            }

            videoSpeed = speedArray[value]

            if (jzDataSource.objects == null) {
                jzDataSource.objects = arrayOf(userDefSpeedIndex)
            }
            jzDataSource.objects[0] = value
        }

    fun getSuperResolutionArray(): Array<String> = arrayOf(
        context.getString(R.string.super_resolution_off),
        context.getString(R.string.super_resolution_performance),
        context.getString(R.string.super_resolution_quality)
    )
    private var superResolutionIndex = 0
        set(value) {
            field = value
            if (value != DEF_SUPER_RESOLUTION_INDEX) {
                superResolution.text = getSuperResolutionArray()[value]
            } else {
                superResolution.text = context.getString(R.string.anime_4k)
            }
            if (mediaInterface is MpvMediaKernel) {
                val kernel = mediaInterface as MpvMediaKernel
                kernel.setSuperResolution(value)
            }
        }

    private lateinit var tvSpeed: TextView
    private lateinit var tvKeyframe: TextView
    private lateinit var tvTimer: TextView
    private lateinit var btnGoHome: ImageView
    private lateinit var topBarContainer: LinearLayout
    private lateinit var layoutTop: View
    private lateinit var layoutBottom: View
    private lateinit var gestureLock: ImageView
    private lateinit var btnLoop: ImageView
    var gestureLocked = false
    var savedProgress: Long = 0L
    private lateinit var btnResumeProgress: MaterialButton
    private val handler = Handler(Looper.getMainLooper())
    private val hideResumeBtnRunnable = Runnable {
        btnResumeProgress.visibility = GONE
    }
    private var hasRestoredProgress = false
    lateinit var orientationManager: OrientationManager
    private lateinit var superResolution: TextView

    var isLooping = false
        set(value) {
            field = value
            if (::btnLoop.isInitialized) {
                btnLoop.imageTintList = ColorStateList.valueOf(
                    if (value) dialogAccentColor else Color.WHITE
                )
            }
        }

    var hKeyframe: HKeyframeEntity? = null
        set(value) {
            field = value
            hKeyframeAdapter?.submitList(value?.keyframes)
            hKeyframeAdapter?.isLocal = value?.let { it.author == null } ?: true
        }

    var videoCode: String? = null
        set(value) {
            field = value

            if (!value.isNullOrEmpty()) {
                initHKeyframeAdapter()
            }
        }

    private var hKeyframeAdapter: HKeyframesRvAdapter? = null
    private val switchPlayerKernel = Preferences.switchPlayerKernel
    var onVideoStateChanged: ((state: Int) -> Unit)? = null

    private fun initHKeyframeAdapter() {
        val videoCode = this.videoCode
        if (videoCode.isNullOrEmpty()) {

            hKeyframeAdapter = HKeyframesRvAdapter(
                videoCode = "",
                onModifyKeyframe = { _, _, _ -> },
                onRemoveKeyframe = { _, _ -> }
            )
            return
        }
        hKeyframeAdapter = HKeyframesRvAdapter(
            videoCode = videoCode,
            onModifyKeyframe = { code, oldKeyframe, newKeyframe ->
                context.findActivityOrNull<MainActivity>()?.viewModel?.modifyHKeyframe(
                    code, oldKeyframe, newKeyframe
                )
            },
            onRemoveKeyframe = { code, keyframe ->
                context.findActivityOrNull<MainActivity>()?.viewModel?.removeHKeyframe(
                    code, keyframe
                )
            }
        ).apply {
            setOnItemClickListener { _, _, position ->
                val keyframe = getItem(position)
                mediaInterface?.seekTo(keyframe.position)
                startProgressTimer()
            }
        }
    }

    private fun isNeedResumeProgress(): Boolean {
        return savedProgress > 5000 && Preferences.allowResumePlayback && !hasRestoredProgress
    }

    var onKeyframeClickListener: ((View) -> Unit)? = null

    var onGoHomeClickListener: ((View) -> Unit)? = null

    var onKeyframeLongClickListener: ((View) -> Unit)? = null

    private var videoSpeed: Float = userDefSpeed
        set(value) {
            field = value
            mediaInterface?.let { mi ->
                val isPlaying = mi.isPlaying
                mi.setSpeed(value)
                if (!isPlaying) {
                    mi.pause()
                }
            }
        }

    @Volatile
    private var isSpeedGestureDetected = false
    private var screenBrightnessBK = -1f
    private var isAdjustBrightness = false

    private val speedGestureDetector =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        val mi: JZMediaInterface? = mediaInterface
                        if (mi != null && mi.isPlaying) {
                            setSpeedInternal(videoSpeed * userDefLongPressSpeedTimes)
                            textureViewContainer.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            isSpeedGestureDetected = true
                        }
                    }
                }
            }
        })

    override fun getLayoutId() = R.layout.layout_jzvd_with_speed

    override fun showProgressDialog(
        deltaX: Float,
        seekTime: String,
        seekTimePosition: Long,
        totalTime: String,
        totalTimeDuration: Long,
    ) {
        super.showProgressDialog(deltaX, seekTime, seekTimePosition, totalTime, totalTimeDuration)
        mDialogSeekTime.setTextColor(dialogAccentColor)
        mDialogTotalTime.setTextColor(Color.WHITE)
        applyDialogAccent(
            progressBar = mDialogProgressBar,
        )
    }

    override fun showVolumeDialog(deltaY: Float, volumePercent: Int) {
        super.showVolumeDialog(deltaY, volumePercent)
        applyDialogAccent(
            progressBar = mDialogVolumeProgressBar,
        )
    }

    override fun showBrightnessDialog(brightnessPercent: Int) {
        super.showBrightnessDialog(brightnessPercent)
        applyDialogAccent(
            progressBar = mDialogBrightnessProgressBar,
        )
    }

    private fun applyDialogAccent(
        progressBar: ProgressBar? = null,
    ) {
        val color = dialogAccentColor
        progressBar?.progressTintList = ColorStateList.valueOf(color)
        progressBar?.progressBackgroundTintList = ColorStateList.valueOf(
            MaterialColors.compositeARGBWithAlpha(color, 96)
        )
    }

    override fun init(context: Context?) {
        super.init(context)
        SAVE_PROGRESS = false
        tvSpeed = findViewById(R.id.tv_speed)
        superResolution = findViewById(R.id.super_resolution)
        tvKeyframe = findViewById(R.id.tv_keyframe)
        tvTimer = findViewById(R.id.tv_timer)
        btnGoHome = findViewById(R.id.go_home)
        layoutTop = findViewById(R.id.layout_top)
        layoutBottom = findViewById(R.id.layout_bottom)
        btnResumeProgress = findViewById(R.id.btn_resume_progress)
        topBarContainer = findViewById(R.id.top_bar_container)
        gestureLock = findViewById(R.id.lock)
        gestureLock.isSelected = false
        btnLoop = findViewById(R.id.btn_loop)
        textureViewContainer.isHapticFeedbackEnabled = true
        tvSpeed.setOnClickListener(this)
        tvKeyframe.setOnClickListener(this)
        tvKeyframe.setOnLongClickListener(this)
        btnGoHome.setOnClickListener(this)
        superResolution.setOnClickListener(this)
        btnLoop.setOnClickListener(this)
        btnResumeProgress.setOnClickListener {
            hasRestoredProgress = true
            mediaInterface.seekTo(0L)
            btnResumeProgress.visibility = GONE
        }

        fullscreenButton.setOnClickListener {
            if (screen == SCREEN_FULLSCREEN) {
                gotoNormalScreen()
            } else {
                gotoFullscreen()
            }
        }
        gestureLock.setOnClickListener {
            gestureLocked = !gestureLocked
            gestureLock.isSelected = gestureLocked
        }

        initHKeyframeAdapter()
    }

    override fun setUp(jzDataSource: JZDataSource?, screen: Int) {
        super.setUp(jzDataSource, screen, ExoMediaKernel::class.java)
    }

    fun setUp(jzDataSource: JZDataSource?, screen: Int, kernel: HMediaKernel.Type) {
        setUp(jzDataSource, screen, kernel.clazz)
    }

    fun setControlsVisible(visible: Boolean) {
        findViewById<View>(R.id.tv_speed)?.isVisible = visible
        findViewById<View>(R.id.tv_keyframe)?.isVisible = visible
        findViewById<View>(R.id.tv_timer)?.isVisible = visible
        findViewById<View>(R.id.go_home)?.isVisible = visible
        findViewById<View>(R.id.layout_top)?.isVisible = visible
        findViewById<View>(R.id.layout_bottom)?.isVisible = visible
        findViewById<View>(R.id.btn_loop)?.isVisible = visible
    }

    override fun setUp(jzDataSource: JZDataSource?, screen: Int, clazz: Class<*>) {
        super.setUp(jzDataSource, screen, clazz)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val preferredQuality = prefs.getString("default_video_quality", null)
        if (Preferences.disableMobileDataWarning){
            WIFI_TIP_DIALOG_SHOWED = true
        }
        if (!preferredQuality.isNullOrBlank()) {
            val index = jzDataSource?.urlsMap?.keys?.indexOf(preferredQuality)
            if (index != -1) {
                if (jzDataSource != null) {
                    if (index != null) {
                        jzDataSource.currentUrlIndex = index
                    }
                }
            } else {
                Log.w("CustomJzvdStd-Settings", "清晰度 $preferredQuality 不可用，使用默认清晰度")
            }
        }
        Log.d("CustomJzvdStd-Settings", buildString {
            append("default_video_quality: ")
            appendLine(preferredQuality)
            append("showBottomProgress: ")
            appendLine(showBottomProgress)
            append("userDefSpeed: ")
            appendLine(userDefSpeed)
            append("userDefSpeedIndex: ")
            appendLine(userDefSpeedIndex)
            append("userDefSlideSensitivity: ")
            appendLine(userDefSlideSensitivity)
        })
        titleTextView.isInvisible = true
        if (bottomProgressBar != null && !showBottomProgress) {
            bottomProgressBar.removeItself()
            bottomProgressBar = ProgressBar(context)
        }
        screenBrightnessBK = try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            ).toFloat() / 255f
        } catch (_: SettingNotFoundException) {
            -1f
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (v.id) {
            R.id.surface_container -> {
                speedGestureDetector.onTouchEvent(event)
                when (event.action) {
                    MotionEvent.ACTION_UP -> {
                        if (isSpeedGestureDetected) {
                            setSpeedInternal(videoSpeed)
                            isSpeedGestureDetected = false
                        }
                    }
                }
            }
        }
        return super.onTouch(v, event)
    }

    fun autoFullscreen(orientation: OrientationManager.ScreenOrientation) {
        autoFullscreen(if (orientation === OrientationManager.ScreenOrientation.LANDSCAPE) 1.0f else -1.0f)
    }

    override fun onClickUiToggle() {
        if (!bottomContainer.isVisible) {
            setSystemTimeAndBattery()
            clarity.text = jzDataSource.currentKey.toString()
        }
        when (state) {
            STATE_PREPARING -> {
                changeUiToPreparing()
                if (!bottomContainer.isVisible) {
                    setSystemTimeAndBattery()
                }
            }

            STATE_PLAYING -> {
                if (gestureLocked) {
                    post {
                        gestureLock.isVisible = !gestureLock.isVisible
                    }
                    changeUiToPlayingClearSafe()
                } else {
                    if (bottomContainer.isVisible) {
                        post {
                            gestureLock.isVisible = false
                        }
                        changeUiToPlayingClearSafe()
                    } else {
                        post {
                            gestureLock.isVisible = true
                        }
                        changeUiToPlayingShowSafe()
                    }
                }
            }

            STATE_PAUSE -> {
                if (bottomContainer.isVisible) {
                    changeUiToPauseClear()
                } else {
                    changeUiToPauseShow()
                }
            }

            STATE_PREPARING_PLAYING -> {
                if (bottomContainer.isVisible) {
                    changeUiToPreparingPlayingClear()
                } else {
                    changeUiToPreparingPlayingShow()
                }
            }
        }
    }

    override fun onStatePreparingPlaying() {
        super.onStatePreparingPlaying()
        if (jzDataSource.objects == null) {
            jzDataSource.objects = arrayOf(userDefSpeedIndex)
            currentSpeedIndex = userDefSpeedIndex
        } else {
            currentSpeedIndex = jzDataSource.objects.first() as Int
        }
    }

    override fun changeUIToPreparingPlaying() {
        when (screen) {
            SCREEN_FULLSCREEN -> {
                setAllControlsVisibilitySafe(
                    INVISIBLE, INVISIBLE, INVISIBLE,
                    VISIBLE, INVISIBLE, INVISIBLE, INVISIBLE
                )
                updateStartImage()
            }
        }
    }

    override fun setScreenNormal() {
        super.setScreenNormal()
        updateVideoPlayerSize(false)
        backButton.isVisible = true
        tvSpeed.isVisible = false
        tvKeyframe.isVisible = false
        titleTextView.isInvisible = true
        tvTimer.isInvisible = true
        btnGoHome.isVisible = true
        topBarContainer.isVisible = false
        superResolution.isVisible = false
        gestureLock.isVisible = false

        layoutTop.updatePadding(left = 0, right = 0)
        layoutBottom.updatePadding(left = 0, right = 0)
        tvTimer.updatePadding(left = 0, right = 0)
        bottomProgressBar.updatePadding(left = 0, right = 0)
    }

    override fun setScreenFullscreen() {
        super.setScreenFullscreen()
        updateVideoPlayerSize(true)
        tvSpeed.isVisible = true
        if (isHKeyframeEnabled) tvKeyframe.isVisible = true
        titleTextView.isVisible = true
        btnGoHome.isVisible = false
        topBarContainer.isVisible = true
        clarity.isVisible = true
        superResolution.isVisible = switchPlayerKernel == HMediaKernel.Type.MpvPlayer.name
        gestureLock.isVisible = true
        val statusBarHeight = statusBarHeight
        val navBarHeight = navBarHeight
        layoutTop.updatePadding(left = statusBarHeight, right = navBarHeight)
        layoutBottom.updatePadding(left = statusBarHeight, right = navBarHeight)
        tvTimer.updatePadding(left = statusBarHeight)
        bottomProgressBar.updatePadding(left = statusBarHeight, right = navBarHeight)
    }

    override fun clickBack() {
        Log.i("fun_clickBack", "player_backBtn_clicked")
        if (context is MainActivity && screen == SCREEN_FULLSCREEN) {
            gotoNormalScreen()
            return
        }
        when {
            CONTAINER_LIST.isNotEmpty() && CURRENT_JZVD != null -> {
                CURRENT_JZVD.gotoNormalScreen()
            }

            CONTAINER_LIST.isEmpty() && CURRENT_JZVD != null && CURRENT_JZVD.screen != SCREEN_NORMAL -> {
                CURRENT_JZVD.clearFloatScreen()
            }
            else -> {
                context.findActivityOrNull<FragmentActivity>()
                    ?.onBackPressedDispatcher
                    ?.onBackPressed()
            }
        }
    }

    override fun clickClarity() {
        this.onCLickUiToggleToClear()
        val colorPrimary = MaterialColors.getColor(
            context,
            androidx.appcompat.R.attr.colorPrimary,
            Color.RED)
        val inflater = this.jzvdContext.getSystemService("layout_inflater") as LayoutInflater
        val layout = inflater.inflate(R.layout.layout_jzvd_clarity, null as ViewGroup?) as LinearLayout
        val mQualityListener = OnClickListener { v1: View? ->
            val index = v1!!.tag as Int
            this.jzDataSource.currentUrlIndex = index
            this.changeUrl(this.jzDataSource, this.currentPositionWhenPlaying)
            this.clarity.text = this.jzDataSource.currentKey.toString()

            for (j in 0..<layout.size) {
                if (j == this.jzDataSource.currentUrlIndex) {
                    (layout.getChildAt(j) as TextView).setTextColor(colorPrimary)
                } else {
                    (layout.getChildAt(j) as TextView).setTextColor("#ffffff".toColorInt())
                }
            }
            if (this.clarityPopWindow != null) {
                this.clarityPopWindow.dismiss()
            }
        }

        for (j in 0..<this.jzDataSource.urlsMap.size) {
            val key = this.jzDataSource.getKeyFromDataSource(j)
            val clarityItem = inflate(
                this.jzvdContext,
                R.layout.layout_jzvd_clarity_item,
                null as ViewGroup?
            ) as TextView
            clarityItem.text = key
            clarityItem.tag = j
            layout.addView(clarityItem, j)
            clarityItem.setOnClickListener(mQualityListener)
            if (j == this.jzDataSource.currentUrlIndex) {
                clarityItem.setTextColor(colorPrimary)
            }
        }

        this.clarityPopWindow =
            PopupWindow(layout, JZUtils.dip2px(this.jzvdContext, 240.0f), -1, true)
        this.clarityPopWindow.animationStyle = cn.jzvd.R.style.pop_animation
        this.clarityPopWindow.contentView = layout
        this.clarityPopWindow.showAtLocation(this.textureViewContainer, 8388613, 0, 0)
    }

    override fun onClick(v: View) {
        super.onClick(v)
        when (v.id) {
            R.id.tv_speed -> clickSpeed()
            R.id.tv_keyframe -> onKeyframeClickListener?.invoke(v)
            R.id.super_resolution -> clickSuperResolution()
            R.id.btn_loop -> {
                isLooping = !isLooping
                applyLooping()
                GlobalToasts.show(
                    context.getString(
                        if (isLooping) R.string.loop_play_on else R.string.loop_play_off
                    ),
                    level = if (isLooping) GlobalToasts.ToastLevel.SUCCESS else GlobalToasts.ToastLevel.INFO,
                )
            }
            R.id.go_home -> {
                if (screen != SCREEN_FULLSCREEN) {
                    context.findActivityOrNull<MainActivity>()?.let { activity ->
                        activity.navController.popBackStack(HomeRoute, false)
                        return
                    }
                } else {
                    onGoHomeClickListener?.invoke(v)
                }
            }

        }
    }

    @SuppressLint("InflateParams")
    fun clickSuperResolution() {
        onCLickUiToggleToClear()
        val inflater = LayoutInflater.from(context).inflate(R.layout.jz_layout_speed, null)
        val rv = inflater.findViewById<RecyclerView>(R.id.rv_video_speed)
        val popup = PopupWindow(
            inflater, JZUtils.dip2px(jzvdContext, 240f),
            LayoutParams.MATCH_PARENT, true
        ).apply {
            contentView = inflater
            animationStyle = cn.jzvd.R.style.pop_animation
        }
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = SuperResolutionAdapter(superResolutionIndex,getSuperResolutionArray().toList()).apply {
            setOnItemClickListener { _, _, position ->
                superResolutionIndex = position
                popup.dismiss()
            }
        }
        popup.showAtLocation(textureViewContainer, Gravity.END, 0, 0)
    }

    override fun onLongClick(v: View): Boolean {
        return when (v.id) {
            R.id.tv_keyframe -> {
                onKeyframeLongClickListener?.invoke(v)
                true
            }
            else -> false
        }
    }

    override fun onCompletion() {
        if (isLooping) {
            restartLoop()
            return
        }
        if (screen == SCREEN_FULLSCREEN) {
            onStateAutoComplete()
        } else {
            super.onCompletion()
        }
        posterImageView.isGone = true
    }

    private fun restartLoop() {
        mediaInterface?.seekTo(0L)
        mediaInterface?.start()
        onStatePlaying()
    }

    private fun applyLooping() {
        jzDataSource.looping = isLooping
        when (val mi = mediaInterface) {
            is ExoMediaKernel -> mi.setLooping(isLooping)
            is MpvMediaKernel -> mi.setLooping(isLooping)
            is SystemMediaKernel -> mi.setLooping(isLooping)
        }
    }

    override fun touchActionMove(x: Float, y: Float) {
        if (gestureLocked) {
            return
        }
        val deltaX = x - mDownX
        var deltaY = y - mDownY
        val absDeltaX = deltaX.absoluteValue
        val absDeltaY = deltaY.absoluteValue

        Log.d(TAG, "mDownX=$mDownX, screenWidth=${JZUtils.getScreenWidth(context)}")
        if (screen != SCREEN_TINY && !isSpeedGestureDetected) {

            if (mDownX > appScreenWidth
                || mDownY < JZUtils.getStatusBarHeight(context)
            ) {
                return
            }
            if (!mChangePosition && !mChangeVolume && !mChangeBrightness) {
                if (absDeltaX > THRESHOLD || absDeltaY > THRESHOLD) {
                    cancelProgressTimer()
                    if (absDeltaX >= THRESHOLD) {

                        if (state != STATE_ERROR) {
                            mChangePosition = true
                            mGestureDownPosition = currentPositionWhenPlaying
                        }
                    } else {

                        Log.i("appScreenWidth",appScreenWidth.toString())
                        if (mDownX < appScreenWidth * 0.5f) {
                            mChangeBrightness = true
                            isAdjustBrightness = true
                            val lp = JZUtils.getWindow(context).attributes
                            if (lp.screenBrightness < 0) {
                                try {
                                    mGestureDownBrightness = Settings.System.getInt(
                                        context.contentResolver,
                                        Settings.System.SCREEN_BRIGHTNESS
                                    ).toFloat()
                                    Log.i(
                                        TAG,
                                        "current system brightness: $mGestureDownBrightness"
                                    )
                                } catch (e: SettingNotFoundException) {
                                    e.printStackTrace()
                                }
                            } else {
                                mGestureDownBrightness = lp.screenBrightness * 255
                                Log.i(
                                    TAG,
                                    "current activity brightness: $mGestureDownBrightness"
                                )
                            }
                        } else {
                            mChangeVolume = true
                            if (mAudioManager == null) {
                                mAudioManager = context.getSystemService()
                            }
                            mGestureDownVolume =
                                mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        }
                    }
                }
            }
        }

        if (mChangePosition) {
            val totalTimeDuration = duration
            mSeekTimePosition =
                (mGestureDownPosition + deltaX * totalTimeDuration / (mScreenWidth * userDefSlideSensitivity)).toLong()
            if (mSeekTimePosition < 0) mSeekTimePosition = 0
            if (mSeekTimePosition > totalTimeDuration) mSeekTimePosition = totalTimeDuration
            val seekTime = JZUtils.stringForTime(mSeekTimePosition)
            val totalTime = JZUtils.stringForTime(totalTimeDuration)
            showProgressDialog(deltaX, seekTime, mSeekTimePosition, totalTime, totalTimeDuration)
        }

        if (mChangeVolume) {
            deltaY = -deltaY
            val max = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val deltaV = (max * deltaY * 3 / mScreenHeight).toInt()
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mGestureDownVolume + deltaV, 0)

            val volumePercent =
                (mGestureDownVolume * 100 / max + deltaY * 3 * 100 / mScreenHeight).toInt()
            showVolumeDialog(-deltaY, volumePercent)
        }

        if (mChangeBrightness) {
            deltaY = -deltaY
            val deltaV = (255 * deltaY * 3 / mScreenHeight).toInt()
            val params = JZUtils.getWindow(context).attributes
            if ((mGestureDownBrightness + deltaV) / 255 >= 1) {
                params.screenBrightness = 1f
            } else if ((mGestureDownBrightness + deltaV) / 255 <= 0) {
                params.screenBrightness = 0.01f
            } else {
                params.screenBrightness = (mGestureDownBrightness + deltaV) / 255
            }
            JZUtils.getWindow(context).attributes = params

            val brightnessPercent =
                (mGestureDownBrightness * 100 / 255 + deltaY * 3 * 100 / mScreenHeight).toInt()
            showBrightnessDialog(brightnessPercent)

        }
    }

    private var savedConstraintLayoutParams: ConstraintLayout.LayoutParams? = null

    override fun gotoNormalScreen() {
        gobakFullscreenTime = System.currentTimeMillis()
        fullscreenListener?.onFullscreenChanged(false)
        Log.i(TAG,"${isAdjustBrightness}、${screenBrightnessBK}、${JZUtils.getWindow(context).attributes.screenBrightness}")
        if (isAdjustBrightness) {
            val window = JZUtils.getWindow(context)
            if (window != null) {
                val params = window.attributes
                params.screenBrightness = screenBrightnessBK.coerceIn(0f, 1f)
                window.attributes = params
            }
            isAdjustBrightness = false
        }

        val decorView = (JZUtils.scanForActivity(jzvdContext)).window.decorView as ViewGroup
        decorView.removeView(this)

        val originalContainer = CONTAINER_LIST.lastOrNull()
        if (originalContainer != null){
            CONTAINER_LIST.pop()
        } else {
            Log.e("JZVD", "CONTAINER_LIST is empty!")
            return
        }
        var layoutParams = blockLayoutParams
        if (originalContainer is ConstraintLayout) {
            layoutParams = savedConstraintLayoutParams ?: ConstraintLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        } else if (originalContainer is FrameLayout) {
            layoutParams = LayoutParams(blockLayoutParams)
        }

        originalContainer.addView(this, blockIndex, layoutParams)
        originalContainer.requestLayout()

        setScreenNormal()
        JZUtils.showStatusBar(jzvdContext)
        val activity = JZUtils.scanForActivity(jzvdContext)
        if (activity != null) {
            orientationManager.unlockOrientation(activity)
        }
        JZUtils.showSystemUI(jzvdContext)
    }

    override fun gotoFullscreen() {
        gotoFullscreenTime = System.currentTimeMillis()
        fullscreenListener?.onFullscreenChanged(true)
        val vg = parent as? ViewGroup ?: return
        val activity = JZUtils.scanForActivity(jzvdContext)
        jzvdContext = vg.context

        blockLayoutParams = layoutParams
        blockIndex = vg.indexOfChild(this)
        blockWidth = width
        blockHeight = height

        if (blockLayoutParams is ConstraintLayout.LayoutParams) {
            savedConstraintLayoutParams =
                ConstraintLayout.LayoutParams(blockLayoutParams as ConstraintLayout.LayoutParams)
        }

        vg.removeView(this)
        CONTAINER_LIST.push(vg)
        val decorView = (JZUtils.scanForActivity(jzvdContext)).window.decorView as ViewGroup
        val fullLayout = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )

        var videoWidth = 0
        var videoHeight = 0
        when (mediaInterface) {
            is ExoMediaKernel -> {
                val mediaKernel = CURRENT_JZVD?.mediaInterface as? ExoMediaKernel
                videoWidth  = mediaKernel?.videoRealWidth?:0
                videoHeight = mediaKernel?.videoRealHeight?:0
                Log.i(TAG,"mediaInterface:$mediaKernel,videoWidth:$videoWidth,videoHeight:$videoHeight")
            }
            is MpvMediaKernel -> {
                val mediaKernel = CURRENT_JZVD?.mediaInterface as? MpvMediaKernel
                videoWidth  = mediaKernel?.videoRealWidth?:0
                videoHeight = mediaKernel?.videoRealHeight?:0
                Log.i(TAG,"mediaInterface:$mediaKernel,videoWidth:$videoWidth,videoHeight:$videoHeight")
            }
            is SystemMediaKernel -> {
                val mediaKernel = CURRENT_JZVD?.mediaInterface as? SystemMediaKernel
                videoWidth  = mediaKernel?.videoRealWidth?:0
                videoHeight = mediaKernel?.videoRealHeight?:0
                Log.i(TAG,"mediaInterface:$mediaKernel,videoWidth:$videoWidth,videoHeight:$videoHeight")
            }
        }

        val isPortraitVideo = videoWidth > 0 && videoHeight > 0 && videoWidth < videoHeight
        Log.i(TAG,"mediaInterface:$mediaInterface,videoWidth:$videoWidth,videoHeight:$videoHeight")
        if (isPortraitVideo) {
            pivotY = 0f
            scaleY = 0.5f
        }
        decorView.addView(this, fullLayout)
        if (isPortraitVideo) {
            animate()
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        setScreenFullscreen()
        JZUtils.hideStatusBar(jzvdContext)
        if (isPortraitVideo) {
            activity?.let {
                orientationManager.lockOrientation(
                    it,
                    OrientationManager.ScreenOrientation.PORTRAIT
                )
            }
        } else {
            JZUtils.setRequestedOrientation(jzvdContext, FULLSCREEN_ORIENTATION)
        }
        JZUtils.hideSystemUI(jzvdContext)
    }

    override fun onStatePreparingChangeUrl() {
        Log.i(TAG, "onStatePreparingChangeUrl " + " [" + this.hashCode() + "] ")
        state = STATE_PREPARING_CHANGE_URL

        CURRENT_JZVD?.let {
            it.reset()
            CURRENT_JZVD = null
        }

        startVideo()
    }

    override fun showWifiDialog() {
        GlobalDialogs.show(
            GlobalDialogs.ConfirmRequest(
                title = jzvdContext.getString(R.string.warning),
                message = jzvdContext.getString(cn.jzvd.R.string.tips_not_wifi),
                confirmText = jzvdContext.getString(cn.jzvd.R.string.tips_not_wifi_confirm),
                dismissText = jzvdContext.getString(cn.jzvd.R.string.tips_not_wifi_cancel),
                onConfirm = {
                    WIFI_TIP_DIALOG_SHOWED = true
                    if (state == STATE_PAUSE) startButton.performClick() else startVideo()
                },
                onCancel = {
                    releaseAllVideos()
                    clearFloatScreen()
                },
            )
        )
    }

    override fun startProgressTimer() {
        Log.i(TAG, "startProgressTimer: " + " [" + this.hashCode() + "] ")
        cancelProgressTimer()
        UPDATE_PROGRESS_TIMER = Timer()
        mProgressTimerTask = ProgressTimerTask()
        UPDATE_PROGRESS_TIMER.schedule(mProgressTimerTask, 0, 100)
    }

    override fun onProgress(progress: Int, position: Long, duration: Long) {
        super.onProgress(progress, position, duration)
        if (screen == SCREEN_FULLSCREEN) hKeyframe?.let {
            var match = false
            for ((index, kf) in it.keyframes.withIndex()) {
                val interval = kf.position - position
                if (interval in 0L..<userDefWhenCountdownRemind) {
                    val timeLong = interval / 1_000L
                    val spannable = spannable {
                        if (userDefShowCommentWhenCountdown) {
                            "#${index + 1}".span {
                                relativeSize(proportion = 0.7F)
                            }
                            if (!kf.prompt.isNullOrBlank()) {
                                " ${kf.prompt}".span {
                                    relativeSize(proportion = 0.7F)
                                }
                            }
                            newline()
                        }
                        val time = if (timeLong >= 1) {
                            (timeLong + 1).toString()
                        } else {
                            val timeFloat = interval / 1_000F
                            "%.1f".format(timeFloat)
                        }
                        time.span {
                            style(Typeface.BOLD)
                        }
                    }
                    tvTimer.text = spannable
                    match = true
                    break
                }
            }
            tvTimer.isInvisible = !match
        } ?: run { tvTimer.isInvisible = true }
    }

    private fun changeUiToPreparingPlayingClear() {
        when (screen) {
            SCREEN_NORMAL, SCREEN_FULLSCREEN -> {
                setAllControlsVisibilitySafe(
                    INVISIBLE, INVISIBLE, INVISIBLE,
                    VISIBLE, INVISIBLE, INVISIBLE, INVISIBLE
                )
            }
        }
    }

    private fun changeUiToPreparingPlayingShow() {
        when (screen) {
            SCREEN_NORMAL, SCREEN_FULLSCREEN -> {
                setAllControlsVisibilitySafe(
                    VISIBLE, VISIBLE, INVISIBLE,
                    VISIBLE, INVISIBLE, VISIBLE, INVISIBLE
                )
            }
        }
    }

    fun setAllControlsVisibilitySafe(
        topCon: Int, bottomCon: Int, startBtn: Int, loadingPro: Int,
        posterImg: Int, bottomPro: Int, retryLayout: Int
    ) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            setAllControlsVisiblity(topCon, bottomCon, startBtn, loadingPro, posterImg, bottomPro, retryLayout)
        } else {
            post {
                setAllControlsVisiblity(topCon, bottomCon, startBtn, loadingPro, posterImg, bottomPro, retryLayout)
            }
        }
    }
    fun changeUiToPlayingClearSafe() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            changeUiToPlayingClear()
        } else {
            post {
                changeUiToPlayingClear()
            }
        }
    }
    fun changeUiToPlayingShowSafe() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            changeUiToPlayingShow()
        } else {
            post {
                changeUiToPlayingShow()
            }
        }
    }

    override fun onStatePlaying() {
        Log.i(TAG, "onStatePlaying " + " [" + this.hashCode() + "] ")
        onVideoStateChanged?.invoke(STATE_PLAYING)
        if (isNeedResumeProgress()) {
            post {
                btnResumeProgress.visibility = VISIBLE
                handler.removeCallbacks(hideResumeBtnRunnable)
                handler.postDelayed(hideResumeBtnRunnable, 5000)
            }
        }
        if (state == STATE_PREPARED) {
            Log.d(TAG, "onStatePlaying:STATE_PREPARED ")
            mAudioManager =
                applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setOnAudioFocusChangeListener(onAudioFocusChangeListener)
                .build()
            mAudioManager.requestAudioFocus(audioFocusRequest)
            if (seekToInAdvance != 0L) {
                mediaInterface.seekTo(seekToInAdvance)
                seekToInAdvance = 0
            } else {
                val position = JZUtils.getSavedProgress(context, jzDataSource.currentUrl)
                if (position != 0L) {
                    mediaInterface.seekTo(position)
                }
            }
        }
        if (isNeedResumeProgress()) {
            mediaInterface.seekTo(savedProgress)
            hasRestoredProgress = true
        }
        state = STATE_PLAYING
        startProgressTimer()
        changeUiToPlayingClearSafe()
    }
    override fun onStatePause() {
        super.onStatePause()
        onVideoStateChanged?.invoke(STATE_PAUSE)
    }
    override fun onStateAutoComplete() {
        super.onStateAutoComplete()
        onVideoStateChanged?.invoke(STATE_AUTO_COMPLETE)
    }

    override fun onStatePreparing() {
        super.onStatePreparing()
        onVideoStateChanged?.invoke(STATE_PREPARING)
    }

    @SuppressLint("InflateParams")
    fun clickSpeed() {
        onCLickUiToggleToClear()
        val inflater = LayoutInflater.from(context).inflate(R.layout.jz_layout_speed, null)
        val rv = inflater.findViewById<RecyclerView>(R.id.rv_video_speed)
        val popup = PopupWindow(
            inflater, JZUtils.dip2px(jzvdContext, 240f),
            LayoutParams.MATCH_PARENT, true
        ).apply {
            contentView = inflater
            animationStyle = cn.jzvd.R.style.pop_animation
        }
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = VideoSpeedAdapter(currentSpeedIndex).apply {
            setOnItemClickListener { _, _, position ->
                currentSpeedIndex = position
                popup.dismiss()
            }
        }
        popup.showAtLocation(textureViewContainer, Gravity.END, 0, 0)
    }

    @SuppressLint("InflateParams")
    fun clickHKeyframe(v: View) {
        onCLickUiToggleToClear()
        val inflater = LayoutInflater.from(context).inflate(R.layout.jz_layout_speed, null)
        val rv = inflater.findViewById<RecyclerView>(R.id.rv_video_speed)
        val popup = PopupWindow(
            inflater, JZUtils.dip2px(jzvdContext, 240f),
            LayoutParams.MATCH_PARENT, true
        ).apply {
            contentView = inflater
            animationStyle = cn.jzvd.R.style.pop_animation
        }
        rv.layoutManager = LinearLayoutManager(v.context)
        val adapter = hKeyframeAdapter
        rv.adapter = adapter
        adapter?.setStateViewLayout(
            inflate(v.context, R.layout.layout_empty_view, null),
            this@HJzvdStd.context.getString(R.string.here_is_empty) + "\n"
                    + this@HJzvdStd.context.getString(R.string.long_press_to_add_h_keyframe)
        )
        popup.showAtLocation(textureViewContainer, Gravity.END, 0, 0)
    }

    private fun setSpeedInternal(speed: Float) {
        mediaInterface?.setSpeed(speed)
    }

    private fun @receiver:IntRange(from = 1, to = 9) Int.toRealSensitivity(): Int {
        return when (this) {
            1, 2, 3, 4, 5 -> this
            6 -> 7
            7 -> 10
            8 -> 20
            9 -> 40
            else -> throw IllegalStateException("Invalid sensitivity value: $this")
        }
    }

    private fun updateVideoPlayerSize(fullscreen: Boolean) {
        if (mediaInterface is MpvMediaKernel) {
            val kernel = mediaInterface as MpvMediaKernel
            post {
                if (fullscreen) {
                    Log.i(TAG, "updateVideoPlayerSize: $width x $height")
                    kernel.updateSurFaceSize(width, height)
                } else {
                    kernel.updateSurFaceSize(width, height)
                }
            }
        }
    }
}
