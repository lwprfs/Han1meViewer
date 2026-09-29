@file:JvmName("BottomNavViewUtil")

package com.yenaly.yenaly_libs.utils.view

import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.updateLayoutParams
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView

fun BottomNavigationView.toggleBottomNavBehavior(view: View, scrollToHide: Boolean) {
    val layoutParams = this.layoutParams as? CoordinatorLayout.LayoutParams
        ?: throw IllegalStateException("parent needs to be coordinator layout!")
    val scrollBehavior = YenalyHideBottomViewOnScrollBehavior<BottomNavigationView>()
    layoutParams.behavior = scrollBehavior
    val behavior = layoutParams.behavior as YenalyHideBottomViewOnScrollBehavior
    behavior.slideUp(this)
    if (scrollToHide) {
        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            setMargins(leftMargin, topMargin, rightMargin, 0)
        }
    } else {
        layoutParams.behavior = null
        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            setMargins(
                leftMargin, topMargin, rightMargin,
                this@toggleBottomNavBehavior.measuredHeight + layoutParams.bottomMargin
            )
        }
    }
}

fun BottomNavigationView.attachViewPager2(
    viewPager2: ViewPager2,
    smoothScroll: Boolean = true,
    addAction: SimpleBottomNavViewMediator.() -> Unit
) = SimpleBottomNavViewMediator(this, viewPager2, smoothScroll).apply(addAction).attach()
