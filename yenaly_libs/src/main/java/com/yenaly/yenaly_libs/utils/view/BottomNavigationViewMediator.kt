package com.yenaly.yenaly_libs.utils.view

import android.util.SparseIntArray
import androidx.annotation.IdRes
import androidx.core.util.set
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import com.yenaly.yenaly_libs.utils.activity

@Suppress("unused")
class BottomNavigationViewMediator @JvmOverloads constructor(
    private val fragmentActivity: FragmentActivity,
    private val bottomNavigationView: BottomNavigationView,
    private val viewPager2: ViewPager2,
    private val itemIdWithFragmentList: List<Pair<Int, Fragment>>,
    var slide: Boolean = true,
    var smoothScroll: Boolean = true
) {

    var currentFragment: Fragment? = null
        private set

    private var listener: OnFragmentChangedListener? = null

    private val fragmentList = itemIdWithFragmentList.map { it.second }

    private val itemIdWithIndexMap = SparseIntArray().also { map ->
        itemIdWithFragmentList.forEachIndexed { index, pair ->
            map[pair.first] = index
        }
    }

    private var attached = false
    private var viewPager2Adapter: RecyclerView.Adapter<*>? = null

    private var onItemSelectedListener: NavigationBarView.OnItemSelectedListener? = null
    private var onPageChangeCallback: ViewPager2.OnPageChangeCallback? = null

    @JvmOverloads
    constructor(
        bottomNavigationView: BottomNavigationView,
        viewPager2: ViewPager2,
        itemIdWithFragmentList: List<Pair<Int, Fragment>>,
        slide: Boolean = true,
        smoothScroll: Boolean = true
    ) : this(
        viewPager2.context.activity as? FragmentActivity
            ?: throw IllegalStateException("context cannot be cast to FragmentActivity!"),
        bottomNavigationView,
        viewPager2,
        itemIdWithFragmentList,
        slide,
        smoothScroll
    )

    fun attach(): BottomNavigationViewMediator {
        if (attached) {
            throw IllegalStateException("${javaClass.simpleName} is already attached")
        }
        if (itemIdWithFragmentList.isEmpty()) {
            throw IllegalStateException("fragment list could not be empty!")
        }
        viewPager2Adapter = object : FragmentStateAdapter(fragmentActivity) {
            override fun getItemCount(): Int {
                return itemIdWithFragmentList.size
            }

            override fun createFragment(position: Int): Fragment {
                val softCopyFragmentList: MutableList<Fragment> = ArrayList(fragmentList)
                return softCopyFragmentList[position]
            }
        }
        attached = true

        onItemSelectedListener = NavigationBarView.OnItemSelectedListener { item ->
            val currentItem = itemIdWithIndexMap[item.itemId]
            viewPager2.setCurrentItem(currentItem, smoothScroll)
            currentFragment = itemIdWithFragmentList[currentItem].second

            true
        }
        onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val currentItem = itemIdWithFragmentList[position]
                bottomNavigationView.selectedItemId = currentItem.first
                currentFragment = currentItem.second
                listener?.onFragmentChanged(currentItem.second)
            }
        }

        viewPager2.isUserInputEnabled = slide
        viewPager2.adapter = viewPager2Adapter
        bottomNavigationView.setOnItemSelectedListener(onItemSelectedListener!!)
        viewPager2.registerOnPageChangeCallback(onPageChangeCallback!!)

        return this
    }

    @JvmOverloads
    fun jumpToFragment(@IdRes fragmentItemId: Int, smoothScroll: Boolean = false): Fragment {
        if (onItemSelectedListener == null || onPageChangeCallback == null) {
            throw IllegalStateException("must call attach() first!")
        }
        val fragmentIndex = itemIdWithIndexMap[fragmentItemId]
        val fragment = itemIdWithFragmentList[fragmentIndex].second
        viewPager2.setCurrentItem(fragmentIndex, smoothScroll)
        bottomNavigationView.selectedItemId = fragmentItemId
        currentFragment = fragment
        listener?.onFragmentChanged(fragment)
        return fragment
    }

    fun setOnFragmentChangedListener(listener: OnFragmentChangedListener) {
        this.listener = listener
    }

    fun interface OnFragmentChangedListener {
        fun onFragmentChanged(currentFragment: Fragment)
    }
}
