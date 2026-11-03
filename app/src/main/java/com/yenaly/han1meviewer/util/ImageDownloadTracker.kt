package com.yenaly.han1meviewer.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

data class ImageDownloadJob(
    val id: Long,
    val label: String,
    val done: Int,
    val total: Int,
) {
    val progress: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

object ImageDownloadTracker {
    private val ids = AtomicLong(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _jobs = MutableStateFlow<List<ImageDownloadJob>>(emptyList())
    val jobs: StateFlow<List<ImageDownloadJob>> = _jobs.asStateFlow()

    fun start(label: String, total: Int): Long {
        val id = ids.incrementAndGet()
        _jobs.update { it + ImageDownloadJob(id, label, 0, total) }
        return id
    }

    fun update(id: Long, done: Int) {
        _jobs.update { list -> list.map { if (it.id == id) it.copy(done = done) else it } }
    }

    fun finish(id: Long) {
        scope.launch {
            update(id, _jobs.value.firstOrNull { it.id == id }?.total ?: 0)
            delay(800)
            _jobs.update { list -> list.filterNot { it.id == id } }
        }
    }
}
