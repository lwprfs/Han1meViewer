package com.yenaly.han1meviewer.worker

import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.DatabaseRepo
import com.yenaly.han1meviewer.logic.dao.DownloadDatabase
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.state.DownloadState
import com.yenaly.yenaly_libs.utils.applicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single owner of the download queue.
 *
 * Rules that keep the UI and the real work in sync:
 *  - Only this manager decides what runs. Every message is handled one at a time by a single
 *    consumer coroutine, so [slots] and [waitingQueue] are never touched concurrently.
 *  - A slot stays occupied until WorkManager reports the work as finished, so the concurrency
 *    limit is never exceeded while a paused worker is still shutting down.
 *  - The manager writes the user-visible state (Queued / Downloading / Paused) itself, immediately,
 *    instead of waiting for the worker to get around to it. The worker only writes progress and
 *    terminal results (Finished / Failed / retry-wait).
 */
object HanimeDownloadManagerV2 {

    private const val TAG = "HanimeDownloadManager"

    const val MAX_CONCURRENT_DOWNLOAD_DEF = 2

    private sealed class DownloadMsg {

        data class Add(
            val args: HanimeDownloadWorker.Args,
            val redownload: Boolean = false,
            val state: DownloadState = DownloadState.Unknown,
        ) : DownloadMsg()

        /** [preempt]: if all slots are busy, push the oldest running task back to the queue. */
        data class Resume(
            val args: HanimeDownloadWorker.Args,
            val preempt: Boolean,
        ) : DownloadMsg()

        data class Stop(val args: HanimeDownloadWorker.Args) : DownloadMsg()

        data class Delete(val args: HanimeDownloadWorker.Args) : DownloadMsg()

        data class Finished(val videoCode: String, val token: Long) : DownloadMsg()

        data object Pump : DownloadMsg()
    }

    private enum class Exit { NONE, PAUSE, REQUEUE, DELETE }

    private class Slot(val args: HanimeDownloadWorker.Args, val token: Long) {
        var exit: Exit = Exit.NONE
        var job: Job? = null
    }

    private val downloadChannel = Channel<DownloadMsg>(capacity = Channel.UNLIMITED)

    @Volatile
    var maxConcurrentDownloadCount = 0
        set(value) {
            field = if (value > 0) value else Int.MAX_VALUE
            downloadChannel.trySend(DownloadMsg.Pump)
        }

    private val workManager = WorkManager.getInstance(applicationContext)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Only touched from the consumer coroutine below.
    private val slots = linkedMapOf<String, Slot>()
    private val waitingQueue = ArrayDeque<HanimeDownloadWorker.Args>()
    private val redownloadPending = hashSetOf<String>()
    private var tokenCounter = 0L

    private val initialized = AtomicBoolean(false)

    init {
        maxConcurrentDownloadCount = Preferences.downloadCountLimit

        scope.launch {
            for (msg in downloadChannel) {
                try {
                    handle(msg)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "failed to handle $msg", e)
                }
            }
        }
    }

    private suspend fun handle(msg: DownloadMsg) {
        when (msg) {
            is DownloadMsg.Add -> onAdd(msg)
            is DownloadMsg.Resume -> onResume(msg)
            is DownloadMsg.Stop -> onStop(msg.args)
            is DownloadMsg.Delete -> onDelete(msg.args)
            is DownloadMsg.Finished -> onFinished(msg)
            DownloadMsg.Pump -> pump()
        }
    }

    private fun hasFreeSlot() = slots.size < maxConcurrentDownloadCount

    private suspend fun onAdd(msg: DownloadMsg.Add) {
        val args = msg.args
        val code = args.videoCode

        when (msg.state) {
            DownloadState.Paused, DownloadState.Failed, DownloadState.Finished -> return
            else -> Unit
        }

        val slot = slots[code]
        if (slot != null) {
            // Already running. If it is currently being stopped, bring it back afterwards.
            if (slot.exit == Exit.PAUSE) {
                slot.exit = Exit.REQUEUE
                markQueued(args, redownload = false)
            }
            return
        }
        if (waitingQueue.any { it.videoCode == code }) {
            Log.d(TAG, "already waiting: $code")
            return
        }

        if (hasFreeSlot()) {
            start(args, msg.redownload)
        } else {
            Log.d(TAG, "slots full, queue: $code")
            if (msg.redownload) redownloadPending += code
            waitingQueue.addLast(args)
            markQueued(args, msg.redownload)
        }
    }

    private suspend fun onResume(msg: DownloadMsg.Resume) {
        val args = msg.args
        val code = args.videoCode

        val slot = slots[code]
        if (slot != null) {
            if (slot.exit == Exit.PAUSE) {
                slot.exit = Exit.REQUEUE
                markQueued(args, redownload = false)
            }
            return
        }

        waitingQueue.removeAll { it.videoCode == code }

        when {
            hasFreeSlot() -> start(args, redownload = false)

            msg.preempt -> {
                val victim = slots.values.firstOrNull { it.exit == Exit.NONE }
                if (victim != null) {
                    Log.d(TAG, "preempt ${victim.args.videoCode} for $code")
                    victim.exit = Exit.REQUEUE
                    markQueued(victim.args, redownload = false)
                    cancelWork(victim.args)
                }
                waitingQueue.addFirst(args)
                markQueued(args, redownload = false)
            }

            else -> {
                waitingQueue.addLast(args)
                markQueued(args, redownload = false)
            }
        }
    }

    private suspend fun onStop(args: HanimeDownloadWorker.Args) {
        val code = args.videoCode
        val slot = slots[code]
        if (slot != null) {
            if (slot.exit != Exit.DELETE) slot.exit = Exit.PAUSE
            cancelWork(args)
        } else {
            waitingQueue.removeAll { it.videoCode == code }
            redownloadPending -= code
            // a not-yet-finished "create queue row" placeholder must not appear afterwards
            cancelWork(args)
        }
        // Instant feedback. Late progress writes from the dying worker are ignored by the DAO.
        DatabaseRepo.HanimeDownload.markPaused(code)
    }

    private suspend fun onDelete(args: HanimeDownloadWorker.Args) {
        val code = args.videoCode
        waitingQueue.removeAll { it.videoCode == code }
        redownloadPending -= code
        slots[code]?.exit = Exit.DELETE
        // REPLACE cancels whatever is running for this code, then the delete worker cleans up.
        startWork(args, delete = true)
    }

    private suspend fun onFinished(msg: DownloadMsg.Finished) {
        val slot = slots[msg.videoCode]
        if (slot == null || slot.token != msg.token) return
        slots.remove(msg.videoCode)
        Log.d(TAG, "finished ${msg.videoCode}, exit=${slot.exit}")

        when (slot.exit) {
            Exit.PAUSE -> DatabaseRepo.HanimeDownload.markPaused(msg.videoCode)

            Exit.REQUEUE -> {
                waitingQueue.addLast(slot.args)
                DatabaseRepo.HanimeDownload.markQueued(msg.videoCode)
            }

            Exit.DELETE -> Unit

            Exit.NONE -> {
                // Worker is gone. If the row still claims to be active although nothing runs it
                // (cancelled from outside, crashed...), do not leave a fake "downloading" behind.
                val entity = DatabaseRepo.HanimeDownload.find(msg.videoCode, slot.args.quality.orEmpty())
                    ?: DatabaseRepo.HanimeDownload.find(msg.videoCode)
                if (entity != null &&
                    (entity.state == DownloadState.Downloading || entity.state == DownloadState.Queued)
                ) {
                    DatabaseRepo.HanimeDownload.markPaused(msg.videoCode)
                }
            }
        }
        pump()
    }

    private fun pump() {
        while (hasFreeSlot() && waitingQueue.isNotEmpty()) {
            val next = waitingQueue.removeFirst()
            val redownload = redownloadPending.remove(next.videoCode)
            start(next, redownload)
        }
    }

    private fun start(args: HanimeDownloadWorker.Args, redownload: Boolean) {
        val slot = Slot(args, ++tokenCounter)
        slots[args.videoCode] = slot
        Log.d(TAG, "start ${args.videoCode}")
        slot.job = scope.launch {
            try {
                // Show "Downloading" right away instead of after the worker's first progress tick.
                DatabaseRepo.HanimeDownload.markDownloading(args.videoCode)
                val workId = startWork(args, redownload)
                awaitWorkCompletion(workId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "download ${args.videoCode} failed to run", e)
            } finally {
                downloadChannel.trySend(DownloadMsg.Finished(args.videoCode, slot.token))
            }
        }
    }

    /**
     * Cancels leftovers from the previous process. WorkManager restarts interrupted work on its
     * own; those workers are invisible to the queue and are the ones that kept downloading while
     * the UI said "queued" or "paused".
     */
    private suspend fun cancelStrayWork() {
        try {
            workManager.cancelAllWorkByTag(HanimeDownloadWorker.TAG).await()
            repeat(20) {
                val running = workManager.getWorkInfosByTagFlow(HanimeDownloadWorker.TAG).first()
                    .any { it.state == WorkInfo.State.RUNNING }
                if (!running) return
                delay(150)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "cancelStrayWork", e)
        }
    }

    suspend fun init() {
        if (!initialized.compareAndSet(false, true)) return
        Log.d(TAG, "init")
        cancelStrayWork()
        val pending = DownloadDatabase.instance.hanimeDownloadDao.loadAllDownloadingHanimeOnce()
        // Things that were actually running go first, then the queue, both oldest first.
        val ordered = pending.sortedWith(
            compareBy<HanimeDownloadEntity> { if (it.state == DownloadState.Downloading) 0 else 1 }
                .thenBy { it.id }
        )
        ordered.forEach { entity ->
            downloadChannel.send(
                DownloadMsg.Add(HanimeDownloadWorker.Args.fromEntity(entity), state = entity.state)
            )
        }
        downloadChannel.send(DownloadMsg.Pump)
    }

    fun addTask(
        args: HanimeDownloadWorker.Args,
        redownload: Boolean = false,
        @Suppress("UNUSED_PARAMETER") waiting: Boolean = false,
    ) {
        downloadChannel.trySend(DownloadMsg.Add(args, redownload))
    }

    /** Start a single item now. If every slot is busy the oldest running one is moved back to the queue. */
    fun resumeTask(entity: HanimeDownloadEntity) {
        downloadChannel.trySend(
            DownloadMsg.Resume(HanimeDownloadWorker.Args.fromEntity(entity), preempt = true)
        )
    }

    /** Put every paused/failed item back in the queue in order, without kicking anything out. */
    fun resumeAll(entities: List<HanimeDownloadEntity>) {
        entities.filter { it.state == DownloadState.Paused || it.state == DownloadState.Unknown }
            .sortedBy { it.id }
            .forEach { entity ->
                downloadChannel.trySend(
                    DownloadMsg.Resume(HanimeDownloadWorker.Args.fromEntity(entity), preempt = false)
                )
            }
    }

    fun stopTask(entity: HanimeDownloadEntity) {
        downloadChannel.trySend(DownloadMsg.Stop(HanimeDownloadWorker.Args.fromEntity(entity)))
    }

    /** Pauses everything that is running or waiting. */
    fun pauseAll(entities: List<HanimeDownloadEntity>) {
        entities.filter {
            it.state == DownloadState.Downloading ||
                    it.state == DownloadState.Queued ||
                    it.state == DownloadState.Unknown
        }.forEach { stopTask(it) }
    }

    fun deleteTask(entity: HanimeDownloadEntity) {
        downloadChannel.trySend(DownloadMsg.Delete(HanimeDownloadWorker.Args.fromEntity(entity)))
    }

    private suspend fun startWork(
        args: HanimeDownloadWorker.Args,
        redownload: Boolean = false,
        waiting: Boolean = false,
        delete: Boolean = false,
    ): UUID = HanimeDownloadWorker.build(constraintsRequired = !delete) {
        setInputData(
            workDataOf(
                HanimeDownloadWorker.QUALITY to args.quality,
                HanimeDownloadWorker.DOWNLOAD_URL to args.downloadUrl,
                HanimeDownloadWorker.VIDEO_TYPE to args.videoType,
                HanimeDownloadWorker.HANIME_NAME to args.hanimeName,
                HanimeDownloadWorker.VIDEO_CODE to args.videoCode,
                HanimeDownloadWorker.COVER_URL to args.coverUrl,
                HanimeDownloadWorker.GROUP_ID to (args.groupId ?: HanimeDownloadWorker.NO_GROUP_ID),
                HanimeDownloadWorker.REDOWNLOAD to redownload,
                HanimeDownloadWorker.IN_WAITING_QUEUE to waiting,
                HanimeDownloadWorker.DELETE to delete
            )
        )
    }.let { request ->
        workManager.beginUniqueWork(
            args.videoCode, ExistingWorkPolicy.REPLACE, request
        ).enqueue().await()
        request.id
    }

    private suspend fun cancelWork(args: HanimeDownloadWorker.Args) {
        try {
            workManager.cancelUniqueWork(args.videoCode).await()
            Log.d(TAG, "cancelWork: ${args.videoCode}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "cancelWork failed: ${args.videoCode}", e)
        }
    }

    /**
     * Marks the row as queued. A brand-new task has no row yet, so a tiny "waiting" worker creates
     * it; that way the item shows up in the list as "Queued" instead of being invisible.
     */
    private suspend fun markQueued(args: HanimeDownloadWorker.Args, redownload: Boolean) {
        val entity = DatabaseRepo.HanimeDownload.find(args.videoCode, args.quality.orEmpty())
        if (entity == null) {
            startWork(args, redownload = redownload, waiting = true)
        } else {
            DatabaseRepo.HanimeDownload.markQueued(args.videoCode)
        }
    }

    private suspend fun awaitWorkCompletion(workId: UUID) {
        workManager.getWorkInfoByIdFlow(workId).first { info ->
            info == null || info.state.isFinished
        }
    }
}
