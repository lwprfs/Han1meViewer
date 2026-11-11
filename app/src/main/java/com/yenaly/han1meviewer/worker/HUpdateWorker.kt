package com.yenaly.han1meviewer.worker

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import com.yenaly.han1meviewer.EMPTY_STRING
import com.yenaly.han1meviewer.FILE_PROVIDER_AUTHORITY
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.UPDATE_NOTIFICATION_CHANNEL
import com.yenaly.han1meviewer.logic.model.github.Latest
import com.yenaly.han1meviewer.logic.network.HUpdater
import com.yenaly.han1meviewer.logic.update.UpdateManager
import com.yenaly.han1meviewer.logic.update.UpdateMethod
import com.yenaly.han1meviewer.logic.update.UpdatePhase
import com.yenaly.han1meviewer.shizuku.UpdateInstaller
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.util.installApkPackage
import com.yenaly.han1meviewer.util.runSuspendCatching
import com.yenaly.han1meviewer.util.updateFile
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import java.io.File
import java.util.Locale

class HUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams), WorkerMixin {
    companion object {
        const val TAG = "HUpdateWorker"

        const val DOWNLOAD_LINK = "download_link"
        const val NODE_ID = "node_id"
        const val DELTA_LINKS = "delta_links"
        const val DELTA_BASES = "delta_bases"
        const val DELTA_NAMES = "delta_names"
        const val DELTA_SIZES = "delta_sizes"
        const val VERSION = "version"
        const val APK_NAME = "apk_name"
        const val APK_SIZE = "apk_size"
        const val IS_CI = "is_ci"
        const val RUN_ID = "run_id"

        const val NOTIFICATION_ID = 0x7A55
        const val ACTION_OPEN_UPDATE_STATUS = "com.yenaly.han1meviewer.ACTION_OPEN_UPDATE_STATUS"
        const val ACTION_DISCARD_UPDATE = "com.yenaly.han1meviewer.ACTION_DISCARD_UPDATE"
        const val UPDATE_APK = "update_apk"
        const val ACTION_INSTALL_UPDATE = "com.yenaly.han1meviewer.ACTION_INSTALL_UPDATE"

        fun enqueue(context: Context, latest: Latest, runId: Long = System.currentTimeMillis()) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val data = workDataOf(
                DOWNLOAD_LINK to latest.downloadLink,
                NODE_ID to latest.nodeId,
                DELTA_LINKS to latest.deltas.map { it.downloadLink }.toTypedArray(),
                DELTA_BASES to latest.deltas.map { it.baseSha256Prefix.orEmpty() }.toTypedArray(),
                DELTA_NAMES to latest.deltas.map { it.name }.toTypedArray(),
                DELTA_SIZES to latest.deltas.map { it.sizeBytes }.toLongArray(),
                VERSION to latest.version,
                APK_NAME to latest.apkName,
                APK_SIZE to latest.apkSize,
                IS_CI to latest.isCi,
                RUN_ID to runId,
            )
            val req = OneTimeWorkRequestBuilder<HUpdateWorker>()
                .addTag(TAG)
                .setConstraints(constraints)
                .setInputData(data)
                .build()
            WorkManager.getInstance(context)
                .beginUniqueWork(TAG, ExistingWorkPolicy.REPLACE, req)
                .enqueue()
        }

        @Suppress("UNUSED_PARAMETER")
        suspend fun collectOutput(context: Context) = Unit
    }

    private val notificationManager = NotificationManagerCompat.from(context)

    private val downloadLink by inputData(DOWNLOAD_LINK, EMPTY_STRING)
    private val nodeId by inputData(NODE_ID, EMPTY_STRING)
    private val deltaLinks: Array<String> by lazy { inputData.getStringArray(DELTA_LINKS) ?: emptyArray() }
    private val deltaBases: Array<String> by lazy { inputData.getStringArray(DELTA_BASES) ?: emptyArray() }
    private val deltaNames: Array<String> by lazy { inputData.getStringArray(DELTA_NAMES) ?: emptyArray() }
    private val deltaSizes: LongArray by lazy { inputData.getLongArray(DELTA_SIZES) ?: LongArray(0) }
    private val downloadId = NOTIFICATION_ID
    private val runId by lazy { inputData.getLong(RUN_ID, 0L) }

    override suspend fun doWork(): Result {
        val foregroundInfo = createForegroundInfo()
        setForeground(foregroundInfo)
        showDownloadNotification()
        val deltas = deltaLinks.mapIndexedNotNull { i, link ->
            if (link.isEmpty()) null
            else Latest.Delta(
                downloadLink = link,
                baseSha256Prefix = deltaBases.getOrNull(i)?.takeIf { it.isNotEmpty() },
                name = deltaNames.getOrNull(i).orEmpty(),
                sizeBytes = deltaSizes.getOrNull(i) ?: 0L,
            )
        }
        UpdateManager.onWorkerStarted(
            runId,
            Latest(
                version = inputData.getString(VERSION).orEmpty(),
                changelog = "",
                downloadLink = downloadLink,
                nodeId = nodeId,
                deltas = deltas,
                apkName = inputData.getString(APK_NAME).orEmpty(),
                apkSize = inputData.getLong(APK_SIZE, 0L),
                isCi = inputData.getBoolean(IS_CI, false),
            )
        )
        with(HUpdater) {
            val file = context.updateFile.apply { delete() }
            val inject = try {
                runSuspendCatching {
                    file.injectUpdate(
                        downloadLink,
                        deltas,
                        inputData.getString(APK_NAME).orEmpty(),
                        inputData.getLong(APK_SIZE, 0L),
                    ) { progress, fileSize, downloadedSize ->
                        UpdateManager.onProgress(progress, fileSize, downloadedSize)
                        updateNotification(progress, fileSize, downloadedSize)
                    }
                }
            } catch (e: CancellationException) {

                if (UpdateManager.isCurrentRun(runId)) {
                    file.delete()
                    cancelDownloadNotification()
                    UpdateManager.onCancelled()
                }
                throw e
            }
            val error = inject.exceptionOrNull()
            if (error == null) {
                val outputData = workDataOf(UPDATE_APK to file.toUri().toString())
                Preferences.updateNodeId = nodeId
                cancelDownloadNotification()
                UpdateManager.onDownloaded(file.length())
                showInstallNotification(file)
                return Result.success(outputData)
            }

            error.printStackTrace()
            val diagnostic = HUpdater.errorDetail(error)
            Firebase.crashlytics.recordException(
                if (diagnostic != null) IllegalStateException(diagnostic, error) else error
            )
            file.delete()
            cancelDownloadNotification()
            val reason = HUpdater.errorMessage(error)?.toString()?.takeIf { it.isNotBlank() }
                ?: error.localizedMessage?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.update_download_failed_unknown)
            UpdateManager.onFailed(reason, diagnostic)
            showFailureNotification(error)
            return Result.failure()
        }
    }

    private fun openStatusIntent(requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_UPDATE_STATUS
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun methodText(downloadedSizeMB: String, fileSizeMB: String): String {
        val plan = UpdateManager.state.value.plan
        val size = "$downloadedSizeMB MB / $fileSizeMB MB"
        return when (plan.method) {
            UpdateMethod.DELTA -> context.getString(R.string.upd_notif_delta_text, size)
            UpdateMethod.FULL -> context.getString(R.string.upd_notif_full_text, size)
            UpdateMethod.UNDECIDED -> context.getString(R.string.upd_stage_preparing)
        }
    }

    private fun createNotification(progress: Int = 0, fileSizeMB: String = "0", downloadedSizeMB: String = "0", isPending: Boolean = true): Notification {
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(id)
        return NotificationCompat.Builder(context, UPDATE_NOTIFICATION_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher_new)
            .setOngoing(true)
            .setContentTitle(
                context.getString(R.string.downloading_update_percent, progress)
            )
            .setContentText(methodText(downloadedSizeMB, fileSizeMB))
            .setContentIntent(openStatusIntent(3))
            .addAction(R.drawable.ic_baseline_cancel_24, context.getString(R.string.upd_notif_cancel), cancelIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(100, progress, isPending)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun showDownloadNotification() {
        notificationManager.notify(downloadId, createNotification(isPending = true))
    }

    private var lastNotifyTime = 0L
    @SuppressLint("MissingPermission")
    private fun updateNotification(progress: Int, fileSize: Long, downloadedSize: Long) {
        val now = System.currentTimeMillis()
        if (progress == 100 || now - lastNotifyTime > 1000) {
            lastNotifyTime = now
            val fileSizeMB = String.format(Locale.US, "%.2f", fileSize.toDouble() / (1024 * 1024))
            val downloadedSizeMB = String.format(Locale.US, "%.2f", downloadedSize.toDouble() / (1024 * 1024))
            notificationManager.notify(
                downloadId,
                createNotification(
                    progress,
                    fileSizeMB,
                    downloadedSizeMB,
                    false
                )
            )
        }
    }

    private fun createForegroundInfo(progress: Int = 0): ForegroundInfo {
        return ForegroundInfo(
            downloadId,
            createNotification(progress, isPending = true),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else 0
        )
    }

    private fun cancelDownloadNotification() {
        notificationManager.cancel(downloadId)
    }

    @SuppressLint("MissingPermission")
    private fun showInstallNotification(file: File) {
        val installIntent = if (Preferences.updateInstaller == UpdateInstaller.SHIZUKU) {

            Intent(context, MainActivity::class.java).apply {
                action = ACTION_INSTALL_UPDATE
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        } else Intent(Intent.ACTION_VIEW).apply {
            val uri = FileProvider.getUriForFile(
                context,
                FILE_PROVIDER_AUTHORITY,
                file
            )
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, UPDATE_NOTIFICATION_CHANNEL)
            .setContentTitle(context.getString(R.string.download_complete))
            .setContentText(context.getString(R.string.click_to_install_update))
            .setSmallIcon(R.mipmap.ic_launcher_new)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.mipmap.ic_launcher_new,
                    context.getString(R.string.install),
                    pendingIntent
                ).build()
            )
            .addAction(
                R.drawable.ic_baseline_info_24,
                context.getString(R.string.upd_notif_details),
                openStatusIntent(1),
            )
            .addAction(
                R.drawable.ic_baseline_cancel_24,
                context.getString(R.string.upd_notif_discard),
                PendingIntent.getBroadcast(
                    context, 2,
                    Intent(context, UpdateActionReceiver::class.java).apply {
                        action = ACTION_DISCARD_UPDATE
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        notificationManager.notify(downloadId + 1, notification)
    }

    @SuppressLint("MissingPermission")
    private fun showFailureNotification(error: Throwable? = null) {

        val reason = HUpdater.errorMessage(error)?.takeIf { it.isNotBlank() }
        val notification = NotificationCompat.Builder(context, UPDATE_NOTIFICATION_CHANNEL)
            .setContentTitle(context.getString(R.string.update_download_failed))
            .setContentText(
                reason ?: error?.localizedMessage?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.update_download_failed_unknown)
            )
            .setSmallIcon(R.mipmap.ic_launcher_new)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(downloadId + 2, notification)
    }
}
