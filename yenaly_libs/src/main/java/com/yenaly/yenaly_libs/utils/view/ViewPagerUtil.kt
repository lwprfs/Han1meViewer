@file:JvmName("ViewPagerUtil")

package com.yenaly.yenaly_libs.utils.view

import androidx.core.view.get
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

typealias NewFragment = () -> Fragment

inline val ViewPager2.innerRecyclerView
    get() = this[0] as? RecyclerView

inline var ViewPager2.realOverScrollMode: Int
    get() {
        return innerRecyclerView?.overScrollMode ?: -1
    }
    set(value) {
        innerRecyclerView?.overScrollMode = value
    }

inline fun ViewPager2.setUpFragmentStateAdapter(
    fragmentActivity: FragmentActivity,
    crossinline addAction: SimpleFragmentStateAdapter.() -> Unit,
) {
    adapter = SimpleFragmentStateAdapter(fragmentActivity).apply(addAction)
}

inline fun ViewPager2.setUpFragmentStateAdapter(
    fragment: Fragment,
    crossinline addAction: SimpleFragmentStateAdapter.() -> Unit,
) {
    adapter = SimpleFragmentStateAdapter(fragment).apply(addAction)
}
