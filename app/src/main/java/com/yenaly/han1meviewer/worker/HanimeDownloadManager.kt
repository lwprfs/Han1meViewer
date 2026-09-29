package com.yenaly.han1meviewer.worker

import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.dao.DownloadDatabase
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.util.runSuspendCatching
import com.yenaly.yenaly_libs.utils.applicationContext
import java.util.Queue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

@Deprecated("使用 HanimeDownloadWorkerV2")
object HanimeDownloadManager {

    const val TAG = "HanimeDownloadManager"
    private const val DOWNLOADING = "downloading"
    private const val WAITING = "waiting"

    const val MAX_CONCURRENT_DOWNLOAD_DEF = 2
    var maxConcurrentDownloadCount = Preferences.downloadCountLimit
        set(value) {
            field = if (value > 0) value else Int.MAX_VALUE
        }

    private val downloadingQueue = DownloadQueue.withTag(DOWNLOADING)
    private val waitingQueue = DownloadQueue.withTag(WAITING)

    private val workManager = WorkManager.getInstance(applicationContext)

    suspend fun init() {
        Log.d(TAG, "init")
        val all = DownloadDatabase.instance.hanimeDownloadDao.loadAllDownloadingHanimeOnce()

        for (i in all.indices) {
            if (i < maxConcurrentDownloadCount) {
                addTask(HanimeDownloadWorker.Args.fromEntity(all[i]), redownload = false)
            } else {
                waitingQueue.offer(HanimeDownloadWorker.Args.fromEntity(all[i]))
            }
        }
    }

    internal fun notify(entity: HanimeDownloadEntity) = notify(
        HanimeDownloadWorker.Args.fromEntity(entity)
    )

    private fun notify(args: HanimeDownloadWorker.Args) {
        when (args) {
            in downloadingQueue -> {
                downloadingQueue.remove(args)
                waitingQueue.poll()?.let { waitingTask ->
                    addTask(waitingTask, redownload = false)
                }
            }

            in waitingQueue -> {
                waitingQueue.remove(args)
            }
        }
    }

    fun addTask(args: HanimeDownloadWorker.Args, redownload: Boolean = false) {
        Log.d(TAG, "addTask: $args")
        if (args in downloadingQueue) {
            return
        }
        if (downloadingQueue.size < maxConcurrentDownloadCount) {

            downloadingQueue.offer(args)
            startWork(args, redownload = redownload)
        } else {
            waitingQueue.offer(args)
            startWork(args, redownload = redownload, waiting = true)
        }
    }

    fun resumeTask(entity: HanimeDownloadEntity) = resumeTask(
        HanimeDownloadWorker.Args.fromEntity(entity)
    )

    fun resumeTask(args: HanimeDownloadWorker.Args) {
        Log.d(TAG, "resumeTask: $args")
        when (args) {
            in downloadingQueue -> return
            else -> {
                while (downloadingQueue.size >= maxConcurrentDownloadCount) {
                    downloadingQueue.poll()?.let { dead ->
                        stopTask(dead)
                        waitingQueue.offer(dead)
                    }
                }
                waitingQueue.remove(args)
                addTask(args, redownload = false)
            }
        }
    }

    fun stopTask(args: HanimeDownloadWorker.Args): Operation {
        Log.d(TAG, "stopTask: $args")
        notify(args)
        return stopWork(args)
    }

    fun stopTask(entity: HanimeDownloadEntity): Operation = stopTask(
        HanimeDownloadWorker.Args.fromEntity(entity)
    )

    suspend fun deleteTaskCrazily(entity: HanimeDownloadEntity) {
        Log.d(TAG, "deleteTask: ${entity.videoCode}")

        val downloadRequest = OneTimeWorkRequestBuilder<HanimeDownloadWorker>()
            .addTag(HanimeDownloadWorker.TAG)
            .setInputData(workDataOf(HanimeDownloadWorker.FAST_PATH_CANCEL to true))
            .build()
        runSuspendCatching {
            workManager.beginUniqueWork(
                entity.videoCode, ExistingWorkPolicy.REPLACE, downloadRequest
            ).enqueue().await()
        }.onSuccess {
            notify(entity)
        }
    }

    fun getDownloadState(videoCode: String): DownloadState {
        return when (videoCode) {
            in downloadingQueue -> DownloadState.Downloading
            in waitingQueue -> DownloadState.Waiting
            else -> DownloadState.Stopped
        }
    }

    private fun stopWork(args: HanimeDownloadWorker.Args): Operation {
        return workManager.cancelUniqueWork(args.videoCode)
    }

    private fun startWork(
        args: HanimeDownloadWorker.Args,
        redownload: Boolean = false,
        waiting: Boolean = false
    ) {
        HanimeDownloadWorker.build {
            setInputData(
                workDataOf(
                    HanimeDownloadWorker.QUALITY to args.quality,
                    HanimeDownloadWorker.DOWNLOAD_URL to args.downloadUrl,
                    HanimeDownloadWorker.VIDEO_TYPE to args.videoType,
                    HanimeDownloadWorker.HANIME_NAME to args.hanimeName,
                    HanimeDownloadWorker.VIDEO_CODE to args.videoCode,
                    HanimeDownloadWorker.COVER_URL to args.coverUrl,
                    HanimeDownloadWorker.REDOWNLOAD to redownload,
                    HanimeDownloadWorker.IN_WAITING_QUEUE to waiting
                )
            )
        }.apply {
            workManager.beginUniqueWork(
                args.videoCode, ExistingWorkPolicy.REPLACE, this
            ).enqueue()
        }
    }

    enum class DownloadState {
        Downloading, Waiting, Stopped
    }

    private class DownloadQueue private constructor(private val tag: String) {

        companion object {
            @JvmStatic
            fun withTag(tag: String) = DownloadQueue("DownloadQueue-$tag")
        }

        private val codeSet: MutableSet<String> = ConcurrentHashMap.newKeySet()
        private val queue: Queue<HanimeDownloadWorker.Args> = ConcurrentLinkedQueue()

        val size: Int get() = queue.size

        fun offer(e: HanimeDownloadWorker.Args) {
            codeSet += e.videoCode
            queue.offer(e)
            Log.d(tag, codeSet.toString())
        }

        fun poll(): HanimeDownloadWorker.Args? {
            val poll = queue.poll()
            if (poll != null) {
                codeSet -= poll.videoCode
            }
            Log.d(tag, codeSet.toString())
            return poll
        }

        fun remove(o: HanimeDownloadWorker.Args?): Boolean {
            if (o != null && codeSet.remove(o.videoCode)) {
                val r = queue.remove(o)
                Log.d(tag, codeSet.toString())
                return r
            }
            return false
        }

        operator fun contains(o: HanimeDownloadWorker.Args?): Boolean {
            return o != null && o.videoCode in codeSet
        }

        operator fun contains(videoCode: String): Boolean {
            return videoCode in codeSet
        }
    }
}
