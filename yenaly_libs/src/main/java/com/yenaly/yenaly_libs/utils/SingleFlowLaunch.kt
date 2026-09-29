package com.yenaly.yenaly_libs.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Deprecated("totally trash")
class SingleFlowLaunch {

    private val jobMap = mutableMapOf<SuspendCoroutineScopeBlock, AtomicInteger>()

    fun singleLaunch(
        viewModelScope: CoroutineScope,
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: SuspendCoroutineScopeBlock,
    ): Job? {
        if (jobMap[block] == null) {
            jobMap[block] = AtomicInteger(0)
        }
        jobMap[block]!!.let { int ->
            if (int.getAndIncrement() != 0) {
                return null
            } else {
                return viewModelScope.launch(context, start, block)
            }
        }
    }
}

typealias SuspendCoroutineScopeBlock = suspend CoroutineScope.() -> Unit
