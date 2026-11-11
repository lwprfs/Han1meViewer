package com.yenaly.han1meviewer.logic.update

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.model.github.Latest
import com.yenaly.han1meviewer.util.getUpdateIfExists
import com.yenaly.han1meviewer.util.installApkPackageForResult
import com.yenaly.han1meviewer.util.updateFile
import com.yenaly.han1meviewer.worker.HUpdateWorker
import com.yenaly.yenaly_libs.utils.applicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class UpdatePhase { IDLE, DOWNLOADING, DOWNLOADED, INSTALLING, FAILED, CANCELLED }

enum class UpdateStage {
    PREPARING,
    CHECKING_DELTA,
    DOWNLOADING_DELTA,
    APPLYING_PATCH,
    VERIFYING,
    DOWNLOADING_FULL,
}

enum class UpdateMethod { UNDECIDED, DELTA, FULL }

enum class DeltaUnavailableReason(val titleRes: Int, val explainRes: Int) {
    NO_DELTA_PUBLISHED(R.string.upd_reason_no_delta_title, R.string.upd_reason_no_delta_body),
    SPLIT_INSTALL(R.string.upd_reason_split_title, R.string.upd_reason_split_body),
    INSTALLED_APK_UNREADABLE(R.string.upd_reason_unreadable_title, R.string.upd_reason_unreadable_body),
    BASE_MISMATCH(R.string.upd_reason_base_title, R.string.upd_reason_base_body),
    BAD_METADATA(R.string.upd_reason_metadata_title, R.string.upd_reason_metadata_body),
    DOWNLOAD_FAILED(R.string.upd_reason_download_title, R.string.upd_reason_download_body),
    PATCH_FAILED(R.string.upd_reason_patch_title, R.string.upd_reason_patch_body),
    VERIFY_FAILED(R.string.upd_reason_verify_title, R.string.upd_reason_verify_body),
    SIGNER_MISMATCH(R.string.upd_reason_signer_title, R.string.upd_reason_signer_body),
}

data class UpdatePlan(
    val method: UpdateMethod = UpdateMethod.UNDECIDED,
    val deltaName: String = "",
    val deltaBasePrefix: String = "",
    val deltaSize: Long = 0L,
    val fullName: String = "",
    val fullSize: Long = 0L,
    val installedBuildPrefix: String = "",
    val reason: DeltaUnavailableReason? = null,
    val reasonDetail: String? = null,
) {

    val activeName: String
        get() = if (method == UpdateMethod.DELTA) deltaName else fullName

    val activeSize: Long
        get() = if (method == UpdateMethod.DELTA) deltaSize else fullSize
}

data class UpdateUiState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val latest: Latest? = null,
    val stage: UpdateStage = UpdateStage.PREPARING,
    val plan: UpdatePlan = UpdatePlan(),
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val apkBytes: Long = 0L,
    val error: String? = null,
    val errorDetail: String? = null,
) {
    val isBusy: Boolean
        get() = phase == UpdatePhase.DOWNLOADING || phase == UpdatePhase.INSTALLING
}

object UpdateManager {

    private const val TAG = "UpdateManager"

    private const val INSTALL_WAIT_MS = 25_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val _preview = MutableStateFlow<Pair<String, UpdatePlan>?>(null)
    val preview: StateFlow<Pair<String, UpdatePlan>?> = _preview.asStateFlow()

    @Volatile
    private var currentRun = 0L

    fun isCurrentRun(runId: Long): Boolean = runId == currentRun

    private var installWatchdog: Job? = null
    private var lastSpeedTime = 0L
    private var lastSpeedBytes = 0L

    private fun str(id: Int, vararg args: Any): String =
        applicationContext.getString(id, *args)

    fun start(context: Context, latest: Latest) {
        installWatchdog?.cancel()
        lastSpeedTime = 0L
        lastSpeedBytes = 0L
        _state.value = UpdateUiState(
            phase = UpdatePhase.DOWNLOADING,
            latest = latest,
            stage = UpdateStage.PREPARING,
            plan = UpdatePlan(fullName = latest.apkName, fullSize = latest.apkSize),
        )
        val runId = System.currentTimeMillis().coerceAtLeast(currentRun + 1)
        currentRun = runId
        HUpdateWorker.enqueue(context.applicationContext, latest, runId)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(HUpdateWorker.TAG)
        clearNotifications(context)
        runCatching { context.updateFile.delete() }
        _state.update { it.copy(phase = UpdatePhase.CANCELLED, error = null, errorDetail = null) }
    }

    fun discard(context: Context) {
        installWatchdog?.cancel()
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(HUpdateWorker.TAG)
        runCatching { context.updateFile.delete() }
        Preferences.updateNodeId = ""
        clearNotifications(context)
        _state.update { it.copy(phase = UpdatePhase.CANCELLED, error = null, errorDetail = null) }
    }

    fun dismiss() {
        _state.update { UpdateUiState(latest = it.latest) }
    }

    fun stopWaiting() {
        installWatchdog?.cancel()
        _state.update { it.copy(phase = UpdatePhase.DOWNLOADED) }
    }

    suspend fun install(context: Context) {
        val file = context.updateFile
        if (!file.exists()) {
            _state.update {
                it.copy(
                    phase = UpdatePhase.FAILED,
                    error = str(R.string.upd_file_missing),
                    errorDetail = null,
                )
            }
            return
        }
        installWatchdog?.cancel()
        _state.update {
            it.copy(phase = UpdatePhase.INSTALLING, error = null, errorDetail = null, apkBytes = file.length())
        }
        val error = try {
            context.installApkPackageForResult(file)
        } catch (e: Exception) {
            Log.e(TAG, "install failed", e)
            e.message ?: e::class.java.simpleName
        }
        if (error != null) {
            _state.update {
                it.copy(phase = UpdatePhase.DOWNLOADED, error = error, errorDetail = null)
            }
            return
        }
        installWatchdog = scope.launch {
            delay(INSTALL_WAIT_MS)
            _state.update {
                if (it.phase == UpdatePhase.INSTALLING) {
                    it.copy(phase = UpdatePhase.DOWNLOADED, error = str(R.string.upd_install_no_response))
                } else it
            }
        }
    }

    fun syncWithDisk(context: Context, latest: Latest?) {
        val current = _state.value
        if (current.phase != UpdatePhase.IDLE && current.phase != UpdatePhase.CANCELLED &&
            current.phase != UpdatePhase.FAILED
        ) return
        if (latest == null) return
        val file = context.getUpdateIfExists(latest) ?: return
        _state.value = UpdateUiState(
            phase = UpdatePhase.DOWNLOADED,
            latest = latest,
            apkBytes = file.length(),
            plan = current.plan,
        )
    }

    suspend fun preview(latest: Latest) {
        if (_preview.value?.first == latest.nodeId) return
        val plan = withContext(Dispatchers.IO) {
            val base = UpdatePlan(fullName = latest.apkName, fullSize = latest.apkSize)
            if (latest.deltas.isEmpty()) {
                return@withContext base.copy(
                    method = UpdateMethod.FULL,
                    reason = DeltaUnavailableReason.NO_DELTA_PUBLISHED,
                )
            }
            val inspection = com.yenaly.han1meviewer.logic.network.HDeltaUpdater.inspect(latest.deltas)
            val delta = inspection.delta
            if (delta != null) {
                base.copy(
                    method = UpdateMethod.DELTA,
                    deltaName = delta.name,
                    deltaBasePrefix = delta.baseSha256Prefix.orEmpty(),
                    deltaSize = delta.sizeBytes,
                    installedBuildPrefix = inspection.installedPrefix,
                )
            } else {
                base.copy(
                    method = UpdateMethod.FULL,
                    installedBuildPrefix = inspection.installedPrefix,
                    reason = inspection.reason,
                    reasonDetail = inspection.detail,
                )
            }
        }
        _preview.value = latest.nodeId to plan
    }

    fun onWorkerStarted(runId: Long, latest: Latest) {

        if (runId >= currentRun) currentRun = runId
        _state.update {
            if (it.phase == UpdatePhase.DOWNLOADING && it.latest != null) it
            else UpdateUiState(
                phase = UpdatePhase.DOWNLOADING,
                latest = latest,
                plan = UpdatePlan(fullName = latest.apkName, fullSize = latest.apkSize),
            )
        }
    }

    fun onStage(stage: UpdateStage) {
        lastSpeedTime = 0L
        lastSpeedBytes = 0L
        _state.update { it.copy(stage = stage, progress = 0, downloadedBytes = 0L, totalBytes = 0L, speedBytesPerSec = 0L) }
    }

    fun onFullApkInfo(name: String, size: Long) {
        _state.update {
            it.copy(
                plan = it.plan.copy(
                    fullName = name.ifEmpty { it.plan.fullName },
                    fullSize = if (size > 0) size else it.plan.fullSize,
                )
            )
        }
    }

    fun onInstalledBuild(prefix: String) {
        _state.update { it.copy(plan = it.plan.copy(installedBuildPrefix = prefix)) }
    }

    fun onDeltaSelected(delta: Latest.Delta) {
        _state.update {
            it.copy(
                plan = it.plan.copy(
                    method = UpdateMethod.DELTA,
                    deltaName = delta.name,
                    deltaBasePrefix = delta.baseSha256Prefix.orEmpty(),
                    deltaSize = delta.sizeBytes,
                    reason = null,
                    reasonDetail = null,
                )
            )
        }
    }

    fun onDeltaUnavailable(reason: DeltaUnavailableReason, detail: String? = null) {
        Log.i(TAG, "delta unavailable: $reason ($detail)")
        _state.update {
            it.copy(
                plan = it.plan.copy(method = UpdateMethod.FULL, reason = reason, reasonDetail = detail),
            )
        }
    }

    fun onFullDownloadStarted() {
        _state.update {
            it.copy(plan = it.plan.copy(method = UpdateMethod.FULL))
        }
        onStage(UpdateStage.DOWNLOADING_FULL)
    }

    fun onProgress(percent: Int, total: Long, downloaded: Long) {
        val now = System.currentTimeMillis()
        var speed = _state.value.speedBytesPerSec
        if (lastSpeedTime == 0L) {
            lastSpeedTime = now
            lastSpeedBytes = downloaded
        } else if (now - lastSpeedTime >= 700) {
            val instant = (downloaded - lastSpeedBytes) * 1000 / (now - lastSpeedTime)
            speed = if (speed == 0L) instant else (speed * 6 + instant * 4) / 10
            lastSpeedTime = now
            lastSpeedBytes = downloaded
        }
        _state.update {
            it.copy(
                progress = percent.coerceIn(0, 100),
                totalBytes = total,
                downloadedBytes = downloaded,
                speedBytesPerSec = speed.coerceAtLeast(0L),
            )
        }
    }

    fun onDownloaded(sizeBytes: Long) {
        _state.update {
            it.copy(
                phase = UpdatePhase.DOWNLOADED,
                progress = 100,
                apkBytes = sizeBytes,
                speedBytesPerSec = 0L,
                error = null,
                errorDetail = null,
            )
        }
    }

    fun onFailed(message: String, detail: String?) {
        _state.update {
            it.copy(
                phase = UpdatePhase.FAILED,
                error = message,
                errorDetail = detail,
                speedBytesPerSec = 0L,
            )
        }
    }

    fun onCancelled() {
        _state.update {
            if (it.phase == UpdatePhase.DOWNLOADING) it.copy(phase = UpdatePhase.CANCELLED, speedBytesPerSec = 0L) else it
        }
    }

    private fun clearNotifications(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(HUpdateWorker.NOTIFICATION_ID)
        nm?.cancel(HUpdateWorker.NOTIFICATION_ID + 1)
        nm?.cancel(HUpdateWorker.NOTIFICATION_ID + 2)
    }
}
