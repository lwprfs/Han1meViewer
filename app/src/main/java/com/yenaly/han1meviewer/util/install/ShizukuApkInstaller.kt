package com.yenaly.han1meviewer.util.install

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.yenaly.han1meviewer.BuildConfig
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.shizuku.IShellInterface
import com.yenaly.han1meviewer.shizuku.ShellInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.io.File

class ShizukuApkInstaller(
    service: Service,
    onFinished: (ApkInstallResult) -> Unit,
) : ApkInstaller(service, onFinished) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var shellInterface: IShellInterface? = null

    @Volatile
    private var pendingFile: File? = null

    private val shizukuArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(service, ShellInterface::class.java))
            .tag("shizuku_service")
            .processNameSuffix("shizuku_service")
            .debuggable(BuildConfig.DEBUG)
            .daemon(false)
            .version(2)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            shellInterface = IShellInterface.Stub.asInterface(binder)
            pendingFile?.let(::doInstall)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shellInterface = null
        }
    }

    private val action = "${BuildConfig.APPLICATION_ID}.ACTION_SHIZUKU_INSTALL_RESULT"

    private val statusIntent by lazy {
        PendingIntent.getBroadcast(
            service.applicationContext,
            0,
            Intent(action).setPackage(BuildConfig.APPLICATION_ID),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
        )
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
            if (status == PackageInstaller.STATUS_SUCCESS) {
                finish(ApkInstallResult.SUCCESS)
            } else {
                Log.w(
                    TAG,
                    "Shizuku 安装失败 ($status): " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                )
                finish(ApkInstallResult.FAILED)
            }
        }
    }

    private val deadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku 意外退出")
        finish(ApkInstallResult.FAILED)
    }

    private val permissionListener = object : Shizuku.OnRequestPermissionResultListener {
        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            if (requestCode != PERMISSION_REQUEST_CODE) return
            Shizuku.removeRequestPermissionResultListener(this)
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                Shizuku.bindUserService(shizukuArgs, connection)
            } else {
                toast(R.string.apk_install_shizuku_permission_denied)
                finish(ApkInstallResult.FAILED)
            }
        }
    }

    init {
        Shizuku.addBinderDeadListener(deadListener)
        ContextCompat.registerReceiver(
            service,
            receiver,
            IntentFilter(action),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        initShizuku()
    }

    private fun initShizuku() {
        if (!Shizuku.pingBinder()) {
            Log.w(TAG, "Shizuku 未运行")
            toast(R.string.apk_install_shizuku_stopped)
            finish(ApkInstallResult.FAILED)
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            Shizuku.bindUserService(shizukuArgs, connection)
        } else {
            Shizuku.addRequestPermissionResultListener(permissionListener)
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }
    }

    override fun install(file: File) {
        pendingFile = file

        if (shellInterface != null) doInstall(file)
    }

    private fun doInstall(file: File) {
        val shell = shellInterface ?: return
        pendingFile = null
        scope.launch {
            try {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                AssetFileDescriptor(fd, 0, file.length()).use {
                    shell.install(it, statusIntent.intentSender)
                }
            } catch (e: Exception) {
                Log.w(TAG, "通过 Shizuku 安装失败", e)
                finish(ApkInstallResult.FAILED)
            }
        }
    }

    private fun toast(resId: Int) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(service.applicationContext, resId, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        Shizuku.removeBinderDeadListener(deadListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        if (Shizuku.pingBinder()) {
            try {
                Shizuku.unbindUserService(shizukuArgs, connection, true)
            } catch (e: Exception) {
                Log.w(TAG, "解绑 Shizuku 服务失败", e)
            }
        }
        runCatching { service.unregisterReceiver(receiver) }
        scope.cancel()
    }

    private companion object {
        const val TAG = "ShizukuApkInstaller"
        const val PERMISSION_REQUEST_CODE = 14046
    }
}
