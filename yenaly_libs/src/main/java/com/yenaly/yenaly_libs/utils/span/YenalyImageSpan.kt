@file:Suppress("unused")

package com.yenaly.yenaly_libs.utils.span

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.style.ImageSpan

class YenalyImageSpan(
    drawable: Drawable,
    verticalAlignment: Int = ALIGN_MIDDLE,
    fontWidthMultiple: Float = -1f,
    marginLeft: Int = 0,
    marginRight: Int = 0
) : ImageSpan(drawable, verticalAlignment) {

    companion object {
        const val ALIGN_MIDDLE = -100

        const val ALIGN_BOTTOM = 0

        const val ALIGN_BASELINE = 1
    }

    private val sVerticalAlignment = verticalAlignment
    private val mFontWidthMultiple = fontWidthMultiple
    private val mMarginLeft = marginLeft
    private val mMarginRight = marginRight
    private var mAvoidSuperChangeFontMetrics = false
    private var mWidth: Int = 0

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        mWidth = if (mAvoidSuperChangeFontMetrics) drawable.bounds.right
        else super.getSize(paint, text, start, end, fm)

        if (mFontWidthMultiple > 0) {
            mWidth = (paint.measureText("子") * mFontWidthMultiple).toInt()
        }

        if ((mMarginLeft > 0) or (mMarginRight > 0)) {
            mWidth = drawable.bounds.right + mMarginLeft + mMarginRight
        }

        return mWidth
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        if (sVerticalAlignment == ALIGN_MIDDLE) {
            canvas.save()
            val fontMetricsInt = paint.fontMetricsInt
            val fontTop = y + fontMetricsInt.top
            val fontMetricsHeight = fontMetricsInt.bottom - fontMetricsInt.top
            val iconHeight = drawable.bounds.bottom - drawable.bounds.top
            val iconTop = fontTop + (fontMetricsHeight - iconHeight) / 2
            canvas.translate(x + mMarginLeft, iconTop.toFloat())
            drawable.draw(canvas)
            canvas.restore()
        } else
            super.draw(canvas, text, start, end, x + mMarginLeft, top, y, bottom, paint)
    }
}
