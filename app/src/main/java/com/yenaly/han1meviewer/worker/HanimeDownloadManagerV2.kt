package com.yenaly.han1meviewer.worker

import android.util.Log
import androidx.lifecycle.Observer
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
import com.yenaly.han1meviewer.util.runSuspendCatching
import com.yenaly.yenaly_libs.utils.applicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.resume

object HanimeDownloadManagerV2 {

    private const val TAG = "HanimeDownloadManager"

    const val MAX_CONCURRENT_DOWNLOAD_DEF = 2
    var maxConcurrentDownloadCount = 0
        set(value) {
            field = if (value > 0) value else Int.MAX_VALUE

            semaphore = Semaphore(field)
        }

    private val workManager = WorkManager.getInstance(applicationContext)

    private var semaphore: Semaphore = Semaphore(1)

    init {

        maxConcurrentDownloadCount = Preferences.downloadCountLimit
    }

    private val activeDownloads = linkedMapOf<String, HanimeDownloadWorker.Args>()
    private val waitingQueue = ArrayDeque<HanimeDownloadWorker.Args>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private sealed class DownloadMsg {

        data class Add(
            val args: HanimeDownloadWorker.Args,
            val redownload: Boolean = false,
            val waiting: Boolean = false,
            val state: DownloadState = DownloadState.Unknown
        ) : DownloadMsg()

        data class Resume(val args: HanimeDownloadWorker.Args) : DownloadMsg()

        data class Stop(val args: HanimeDownloadWorker.Args) : DownloadMsg()

        data class Delete(val args: HanimeDownloadWorker.Args) : DownloadMsg()

        data object ProcessNext : DownloadMsg()
    }

    private val downloadChannel = Channel<DownloadMsg>(capacity = Channel.UNLIMITED)

    init {
        scope.launch {
            for (msg in downloadChannel) {
                when (msg) {
                    is DownloadMsg.Add -> {
                        if (msg.args.videoCode in activeDownloads) {
                            Log.d(TAG, "任务已存在：${msg.args.videoCode}")
                        } else if (waitingQueue.any { it.videoCode == msg.args.videoCode }) {
                            Log.d(TAG, "任务已在等待队列：${msg.args.videoCode}")
                        } else {

                            if (activeDownloads.size < maxConcurrentDownloadCount &&
                                (msg.state == DownloadState.Downloading || msg.state == DownloadState.Unknown)
                            ) {
                                Log.d(TAG, "添加任务：${msg.args.videoCode}")
                                activeDownloads[msg.args.videoCode] = msg.args
                                launchDownload(msg.args, msg.redownload, msg.waiting)
                            } else {
                                Log.d(TAG, "任务已满，加入等待队列：${msg.args.videoCode}")
                                when (msg.state) {
                                    DownloadState.Downloading -> {

                                        waitingQueue.addFirst(msg.args)
                                        enqueueWaitingWork(msg.args, msg.redownload)
                                    }

                                    DownloadState.Queued, DownloadState.Unknown -> {
                                        waitingQueue.addLast(msg.args)
                                        enqueueWaitingWork(msg.args, msg.redownload)
                                    }

                                    else -> Unit
                                }
                            }
                        }
                    }

                    is DownloadMsg.Resume -> {
                        if (msg.args.videoCode in activeDownloads) {
                            Log.d(TAG, "任务已在下载中，无需恢复：${msg.args.videoCode}")
                        } else {
                            waitingQueue.removeIf { it.videoCode == msg.args.videoCode }
                            Log.d(TAG, "恢复任务：${msg.args.videoCode}")

                            while (activeDownloads.size >= maxConcurrentDownloadCount && activeDownloads.isNotEmpty()) {
                                val (videoCode, task) = activeDownloads.entries.first()
                                activeDownloads.remove(videoCode)
                                stopWork(task)
                                waitingQueue.addLast(task)
                                markQueued(task)
                                Log.d(TAG, "任务已满，暂停任务：$videoCode")
                            }
                            activeDownloads[msg.args.videoCode] = msg.args
                            launchDownload(msg.args, redownload = false, waiting = false)
                        }
                    }

                    is DownloadMsg.Stop -> {
                        if (activeDownloads.remove(msg.args.videoCode) != null) {
                            Log.d(TAG, "停止任务：${msg.args.videoCode}")
                            stopWork(msg.args)
                            processNext()
                        } else {
                            Log.e(TAG, "停止任务，不应该走到这里：${msg.args.videoCode}")
                            waitingQueue.removeIf { it.videoCode == msg.args.videoCode }
                            markPaused(msg.args)
                        }
                    }

                    is DownloadMsg.Delete -> {
                        if (activeDownloads.remove(msg.args.videoCode) != null) {
                            Log.d(TAG, "从正在下载列表中删除任务：${msg.args.videoCode}")
                        } else {
                            waitingQueue.removeIf { it.videoCode == msg.args.videoCode }
                            Log.d(TAG, "从等待队列中删除任务：${msg.args.videoCode}")
                        }
                        deleteWork(msg.args)
                        processNext()
                    }

                    DownloadMsg.ProcessNext -> processNext()
                }
            }
        }
    }

    suspend fun init() {
        Log.d(TAG, "init")
        val allDownloading =
            DownloadDatabase.instance.hanimeDownloadDao.loadAllDownloadingHanimeOnce()
        allDownloading.forEach { entity ->
            val args = HanimeDownloadWorker.Args.fromEntity(entity)

            downloadChannel.send(DownloadMsg.Add(args, state = entity.state))
        }
    }

    fun addTask(
        args: HanimeDownloadWorker.Args,
        redownload: Boolean = false, waiting: Boolean = false
    ) {
        scope.launch { downloadChannel.send(DownloadMsg.Add(args, redownload, waiting)) }
    }

    fun resumeTask(entity: HanimeDownloadEntity) {
        val args = HanimeDownloadWorker.Args.fromEntity(entity)
        scope.launch { downloadChannel.send(DownloadMsg.Resume(args)) }
    }

    fun stopTask(entity: HanimeDownloadEntity) {
        val args = HanimeDownloadWorker.Args.fromEntity(entity)
        scope.launch { downloadChannel.send(DownloadMsg.Stop(args)) }
    }

    fun deleteTask(entity: HanimeDownloadEntity) {
        val args = HanimeDownloadWorker.Args.fromEntity(entity)
        scope.launch { downloadChannel.send(DownloadMsg.Delete(args)) }
    }

    private fun processNext() {
        Log.d(TAG, "processNext")
        while (activeDownloads.size < maxConcurrentDownloadCount && waitingQueue.isNotEmpty()) {
            val next = waitingQueue.removeFirst()
            activeDownloads[next.videoCode] = next
            launchDownload(next, redownload = false, waiting = false)
        }
    }

    private fun launchDownload(
        args: HanimeDownloadWorker.Args,
        redownload: Boolean,
        waiting: Boolean
    ) {
        scope.launch {

            if (waiting) {
                Log.d(TAG, "launchDownload (waiting): ${args.videoCode}")
                markQueued(args)
            } else {

                semaphore.withPermit {
                    Log.d(TAG, "launchDownload (start): ${args.videoCode}")

                    val workId = startWork(args, redownload)

                    awaitWorkCompletion(args.videoCode, workId.toString())
                }

                activeDownloads.remove(args.videoCode)
                Log.d(TAG, "launchDownload (end): ${args.videoCode}")
                downloadChannel.send(DownloadMsg.ProcessNext)
            }
        }
    }

    private suspend fun startWork(
        args: HanimeDownloadWorker.Args,
        redownload: Boolean = false,
        waiting: Boolean = false,
        delete: Boolean = false
    ) = HanimeDownloadWorker.build(constraintsRequired = !delete) {
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
        }.apply {
            workManager.beginUniqueWork(
                args.videoCode, ExistingWorkPolicy.REPLACE, this
            ).enqueue().await()
        }.id

    private suspend fun stopWork(args: HanimeDownloadWorker.Args) {
        runSuspendCatching {
            workManager.cancelUniqueWork(args.videoCode).await()
            markPaused(args)
            Log.d(TAG, "stopWork (cancelUniqueWork): ${args.videoCode}")
        }.onFailure { t ->
            t.printStackTrace()
            markPaused(args)
        }
    }

    private suspend fun markQueued(args: HanimeDownloadWorker.Args) {
        DatabaseRepo.HanimeDownload.find(args.videoCode, args.quality.orEmpty())?.let { entity ->
            DatabaseRepo.HanimeDownload.update(entity.copy(state = DownloadState.Queued))
        }
    }

    private suspend fun enqueueWaitingWork(
        args: HanimeDownloadWorker.Args,
        redownload: Boolean = false
    ) {
        val entity = DatabaseRepo.HanimeDownload.find(args.videoCode, args.quality.orEmpty())
        if (entity == null) {
            startWork(args, redownload = redownload, waiting = true)
        } else {
            DatabaseRepo.HanimeDownload.update(entity.copy(state = DownloadState.Queued))
        }
    }

    private suspend fun markPaused(args: HanimeDownloadWorker.Args) {
        DatabaseRepo.HanimeDownload.find(args.videoCode, args.quality.orEmpty())?.let { entity ->
            if (entity.state != DownloadState.Finished) {
                DatabaseRepo.HanimeDownload.update(entity.copy(state = DownloadState.Paused))
            }
        }
    }

    private suspend fun deleteWork(args: HanimeDownloadWorker.Args) = startWork(args, delete = true)

    private suspend fun awaitWorkCompletion(videoCode: String, workId: String) =
        suspendCancellableCoroutine { cont ->
            val liveData = workManager.getWorkInfosForUniqueWorkLiveData(videoCode)
            Log.d(TAG, "获取 LiveData：$videoCode")
            val observer = object : Observer<List<WorkInfo>> {
                override fun onChanged(value: List<WorkInfo>) {
                    val info = value.firstOrNull { it.id.toString() == workId } ?: return
                    if (info.state.isFinished) {
                        Log.d(TAG, "任务完成，移除 observer：$videoCode")
                        liveData.removeObserver(this)
                        cont.resume(Unit)
                    }
                }
            }
            CoroutineScope(Dispatchers.Main).launch {
                liveData.observeForever(observer)
                Log.d(TAG, "添加 observer：$videoCode")
            }
            cont.invokeOnCancellation { liveData.removeObserver(observer) }
        }
}
