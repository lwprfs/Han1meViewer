package com.yenaly.han1meviewer.util.install

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.UPDATE_NOTIFICATION_CHANNEL
import java.io.File

class ApkInstallService : Service() {

    private var installer: ApkInstaller? = null

    override fun onCreate() {
        super.onCreate()
        val notification: Notification = NotificationCompat.Builder(this, UPDATE_NOTIFICATION_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher_new)
            .setContentTitle(getString(R.string.apk_installing))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, 100, true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val path = intent?.getStringExtra(EXTRA_PATH)
        val method = ApkInstallMethod.fromKey(intent?.getStringExtra(EXTRA_METHOD))
        val file = path?.let(::File)?.takeIf { it.isFile }
        if (file == null || method == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (installer == null) {
            installer = when (method) {
                ApkInstallMethod.PACKAGE_INSTALLER -> PackageInstallerApkInstaller(this, ::onFinished)
                ApkInstallMethod.SHIZUKU -> ShizukuApkInstaller(this, ::onFinished)
                ApkInstallMethod.LEGACY -> {
                    Log.e(TAG, "LEGACY 不经过前台服务")
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        installer?.install(file)
        return START_NOT_STICKY
    }

    private fun onFinished(result: ApkInstallResult) {
        if (result == ApkInstallResult.FAILED) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, R.string.apk_install_failed, Toast.LENGTH_LONG).show()
            }
        }
        stopSelf()
    }

    override fun onDestroy() {
        installer?.onDestroy()
        installer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "ApkInstallService"
        private const val NOTIFICATION_ID = 0x4841
        private const val EXTRA_PATH = "extra_path"
        private const val EXTRA_METHOD = "extra_method"

        fun getIntent(context: Context, file: File, method: ApkInstallMethod): Intent =
            Intent(context, ApkInstallService::class.java)
                .putExtra(EXTRA_PATH, file.absolutePath)
                .putExtra(EXTRA_METHOD, method.key)
    }
}
