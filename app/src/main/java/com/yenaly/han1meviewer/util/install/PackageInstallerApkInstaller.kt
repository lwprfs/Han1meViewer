package com.yenaly.han1meviewer.util.install

import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class PackageInstallerApkInstaller(
    service: Service,
    onFinished: (ApkInstallResult) -> Unit,
) : ApkInstaller(service, onFinished) {

    private val packageInstaller = service.packageManager.packageInstaller
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val action = "${service.packageName}.APK_INSTALL_ACTION"

    @Volatile
    private var sessionId = -1

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirm = confirmIntent(intent)
                    if (confirm == null) {
                        Log.w(TAG, "系统安装器要求用户操作，但没有给出 Intent: $intent")
                        finish(ApkInstallResult.FAILED)
                        return
                    }
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        service.startActivity(confirm)
                    } catch (e: ActivityNotFoundException) {
                        Log.w(TAG, "没有可以确认安装的界面", e)
                        abandon()
                        finish(ApkInstallResult.FAILED)
                    }
                }

                PackageInstaller.STATUS_FAILURE_ABORTED -> finish(ApkInstallResult.CANCELLED)
                PackageInstaller.STATUS_SUCCESS -> finish(ApkInstallResult.SUCCESS)
                else -> {
                    Log.w(
                        TAG,
                        "系统安装器安装失败: " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    )
                    finish(ApkInstallResult.FAILED)
                }
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            service,
            receiver,
            IntentFilter(action),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun install(file: File) {
        scope.launch {
            try {
                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                params.setSize(file.length())
                sessionId = packageInstaller.createSession(params)

                packageInstaller.openSession(sessionId).use { session ->
                    file.inputStream().use { input ->
                        session.openWrite("update", 0, file.length()).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }
                    val intentSender = PendingIntent.getBroadcast(
                        service,
                        sessionId,
                        Intent(action).setPackage(service.packageName),
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
                    ).intentSender
                    session.commit(intentSender)
                }
            } catch (e: Exception) {
                Log.w(TAG, "创建/提交安装会话失败", e)
                abandon()
                finish(ApkInstallResult.FAILED)
            }
        }
    }

    private fun abandon() {
        val id = sessionId
        if (id != -1) runCatching { packageInstaller.abandonSession(id) }
    }

    @Suppress("DEPRECATION")
    private fun confirmIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

    override fun onDestroy() {
        runCatching { service.unregisterReceiver(receiver) }
        scope.cancel()
    }

    private companion object {
        const val TAG = "PkgInstallerApk"
    }
}
