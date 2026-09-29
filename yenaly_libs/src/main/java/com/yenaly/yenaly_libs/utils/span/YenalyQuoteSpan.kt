@file:Suppress("unused")

package com.yenaly.yenaly_libs.utils.span

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.style.LeadingMarginSpan
import androidx.annotation.ColorInt

class YenalyQuoteSpan @JvmOverloads constructor(
    @param:ColorInt private val color: Int = STANDARD_COLOR,
    private val stripeWidth: Int = STANDARD_STRIPE_WIDTH_PX,
    private val gapWidth: Int = STANDARD_GAP_WIDTH_PX
) : LeadingMarginSpan {

    @ColorInt
    fun getColor() = this.color

    fun getStripeWidth() = this.stripeWidth

    fun getGapWidth() = this.gapWidth

    override fun getLeadingMargin(first: Boolean): Int {
        return stripeWidth + gapWidth
    }

    override fun drawLeadingMargin(
        c: Canvas,
        p: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout
    ) {
        val style = p.style
        val color = p.color

        p.style = Paint.Style.FILL
        p.color = this.color

        c.drawRect(
            x.toFloat(),
            top.toFloat(),
            (x + dir * this.stripeWidth).toFloat(),
            bottom.toFloat(),
            p
        )

        p.style = style
        p.color = color
    }

    companion object {

        const val STANDARD_STRIPE_WIDTH_PX = 2

        const val STANDARD_GAP_WIDTH_PX = 2

        @ColorInt
        const val STANDARD_COLOR = -0xffff01
    }
}
