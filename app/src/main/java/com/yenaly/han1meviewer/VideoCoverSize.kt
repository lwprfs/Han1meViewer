package com.yenaly.han1meviewer

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import com.yenaly.yenaly_libs.utils.applicationContext
import com.yenaly.yenaly_libs.utils.dp

@SuppressLint("StaticFieldLeak")
object VideoCoverSize {

    private const val TAG = "VideoCoverSize"

    private val context = applicationContext

    private val screenWidth get() = context.resources.displayMetrics.widthPixels

    private val videoCoverWidth =
        context.resources.getDimension(R.dimen.video_cover_width)
    private val simplifiedVideoCoverWidth =
        context.resources.getDimension(R.dimen.video_cover_simplified_width)

    private const val RATIO = 15 / 22.0

    private val margin = 4.dp

    private val parentMargin = 8.dp

    object Normal {

        private const val AT_LEAST = 2

        val videoInOneLine
            get() = (screenWidth / videoCoverWidth).toInt().coerceAtLeast(AT_LEAST)

        fun ViewGroup.resizeForVideoCover(atLeast: Int = AT_LEAST) {
            require(atLeast > 0)
            val screenWidth = screenWidth
            val videoInOneLine = (screenWidth / videoCoverWidth).toInt()
            if (videoInOneLine < atLeast) {
                val width = (screenWidth - parentMargin * 2) / atLeast - margin * 2
                val height = (width * RATIO).toInt()
                updateLayoutParams {
                    this.width = width
                    this.height = height
                }
            }
        }
    }

    object Simplified {

        private const val AT_LEAST = 3

        val videoInOneLine
            get() = (screenWidth / simplifiedVideoCoverWidth).toInt().coerceAtLeast(AT_LEAST)

        fun ViewGroup.resizeForVideoCover(atLeast: Int = AT_LEAST) {
            require(atLeast > 0)
            val screenWidth = screenWidth
            val videoInOneLine = (screenWidth / simplifiedVideoCoverWidth).toInt()
            if (videoInOneLine < atLeast) {
                val width = (screenWidth - parentMargin * 2) / atLeast - margin * 2
                val height = (width / RATIO).toInt()
                updateLayoutParams {
                    this.width = width
                    this.height = height
                }
            }
        }
    }
}
