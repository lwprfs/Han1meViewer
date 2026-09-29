package com.yenaly.yenaly_libs.utils.view

import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs

fun AppBarLayout.offsetChanges(): Flow<Int> {
    return callbackFlow {
        val listener = AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
            trySend(verticalOffset)
        }
        addOnOffsetChangedListener(listener)
        awaitClose { removeOnOffsetChangedListener(listener) }
    }
}

abstract class AppBarLayoutStateChangeListener : AppBarLayout.OnOffsetChangedListener {

    enum class State {
        EXPANDED,
        COLLAPSED,
        INTERMEDIATE;
    }

    private var mCurrentState: State = State.INTERMEDIATE

    override fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {
        when {
            verticalOffset == 0 -> {
                if (mCurrentState != State.EXPANDED) {
                    onStateChanged(appBarLayout, State.EXPANDED)
                }
                mCurrentState = State.EXPANDED
            }

            abs(verticalOffset) >= appBarLayout.totalScrollRange -> {
                if (mCurrentState != State.COLLAPSED) {
                    onStateChanged(appBarLayout, State.COLLAPSED)
                }
                mCurrentState = State.COLLAPSED
            }

            else -> {
                if (mCurrentState != State.INTERMEDIATE) {
                    onStateChanged(appBarLayout, State.INTERMEDIATE)
                }
                mCurrentState = State.INTERMEDIATE
            }
        }
    }

    abstract fun onStateChanged(appBarLayout: AppBarLayout, state: State)
}
