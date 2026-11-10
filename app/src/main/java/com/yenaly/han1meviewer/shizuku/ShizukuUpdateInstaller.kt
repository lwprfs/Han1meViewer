package com.yenaly.han1meviewer.shizuku

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import android.app.PendingIntent
import com.yenaly.han1meviewer.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import java.io.File
import kotlin.coroutines.resume

object UpdateInstaller {
    const val SYSTEM = "system"
    const val SHIZUKU = "shizuku"
}

sealed interface ShizukuInstallResult {
    data object Success : ShizukuInstallResult
    data object NotInstalled : ShizukuInstallResult
    data object NotRunning : ShizukuInstallResult
    data object PermissionDenied : ShizukuInstallResult
    data class Failed(val message: String?) : ShizukuInstallResult
}

object ShizukuUpdateInstaller {

    private const val TAG = "ShizukuUpdateInstaller"
    private const val PERMISSION_REQUEST_CODE = 14046
    private const val BINDER_TIMEOUT_MS = 2_000L
    private const val SERVICE_TIMEOUT_MS = 10_000L
    private const val INSTALL_TIMEOUT_MS = 120_000L
    private const val ACTION_INSTALL_RESULT = "${BuildConfig.APPLICATION_ID}.ACTION_SHIZUKU_INSTALL_RESULT"

    fun isShizukuInstalled(context: Context): Boolean {
        if (isRunning()) return true
        return try {
            context.packageManager.getPermissionInfo(ShizukuProvider.PERMISSION, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    private fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    private suspend fun awaitBinder(): Boolean {
        if (isRunning()) return true
        return withTimeoutOrNull(BINDER_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val listener = object : Shizuku.OnBinderReceivedListener {
                    override fun onBinderReceived() {
                        Shizuku.removeBinderReceivedListener(this)
                        if (cont.isActive) cont.resume(true)
                    }
                }
                Shizuku.addBinderReceivedListener(listener)
                cont.invokeOnCancellation { Shizuku.removeBinderReceivedListener(listener) }
            }
        } ?: false
    }

    suspend fun requestPermission(context: Context): ShizukuInstallResult {
        if (!isShizukuInstalled(context)) return ShizukuInstallResult.NotInstalled
        if (!awaitBinder()) return ShizukuInstallResult.NotRunning
        if (Shizuku.isPreV11()) return ShizukuInstallResult.Failed("Shizuku version is too old")
        if (hasPermission()) return ShizukuInstallResult.Success

        if (Shizuku.shouldShowRequestPermissionRationale()) return ShizukuInstallResult.PermissionDenied

        val granted = suspendCancellableCoroutine { cont ->
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode != PERMISSION_REQUEST_CODE) return
                    Shizuku.removeRequestPermissionResultListener(this)
                    if (cont.isActive) cont.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            cont.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
            try {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            } catch (e: Throwable) {
                Log.e(TAG, "requestPermission failed", e)
                Shizuku.removeRequestPermissionResultListener(listener)
                if (cont.isActive) cont.resume(false)
            }
        }
        return if (granted) ShizukuInstallResult.Success else ShizukuInstallResult.PermissionDenied
    }

    private fun userServiceArgs(context: Context) = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, ShellInterface::class.java.name),
    )
        .tag("han1meviewer_shizuku_service")
        .processNameSuffix("shizuku_service")
        .debuggable(BuildConfig.DEBUG)
        .daemon(false)
        .version(1)

    suspend fun install(context: Context, apk: File): ShizukuInstallResult {
        val appContext = context.applicationContext

        when (val perm = requestPermission(appContext)) {
            ShizukuInstallResult.Success -> Unit
            else -> return perm
        }

        val args = userServiceArgs(appContext)
        var connection: ServiceConnection? = null
        try {
            val shell = withTimeoutOrNull(SERVICE_TIMEOUT_MS) {
                suspendCancellableCoroutine<IShellInterface> { cont ->
                    val conn = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                            if (service != null && service.pingBinder() && cont.isActive) {
                                cont.resume(IShellInterface.Stub.asInterface(service))
                            }
                        }

                        override fun onServiceDisconnected(name: ComponentName?) = Unit
                    }
                    connection = conn
                    Shizuku.bindUserService(args, conn)
                }
            } ?: return ShizukuInstallResult.Failed("Shizuku service did not start")

            return performInstall(appContext, shell, apk)
        } catch (e: Throwable) {
            Log.e(TAG, "Shizuku install failed", e)
            return ShizukuInstallResult.Failed(e.localizedMessage ?: e.javaClass.simpleName)
        } finally {
            connection?.let {
                try {
                    if (isRunning()) Shizuku.unbindUserService(args, it, true)
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to unbind Shizuku service", e)
                }
            }
        }
    }

    private suspend fun performInstall(
        context: Context,
        shell: IShellInterface,
        apk: File,
    ): ShizukuInstallResult {
        val result = CompletableDeferred<ShizukuInstallResult>()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                when (status) {
                    PackageInstaller.STATUS_SUCCESS -> result.complete(ShizukuInstallResult.Success)
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {

                        val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                        if (confirm != null) {
                            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(confirm)
                        } else {
                            result.complete(ShizukuInstallResult.Failed(message))
                        }
                    }

                    else -> {
                        Log.w(TAG, "Shizuku install failed ($status): $message")
                        result.complete(ShizukuInstallResult.Failed(message))
                    }
                }
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_INSTALL_RESULT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        try {
            val statusIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_INSTALL_RESULT).setPackage(context.packageName),
                PendingIntent.FLAG_MUTABLE,
            )

            withContext(Dispatchers.IO) {
                ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    AssetFileDescriptor(pfd, 0, apk.length()).use { afd ->
                        shell.install(afd, statusIntent.intentSender)
                    }
                }
            }

            return withTimeoutOrNull(INSTALL_TIMEOUT_MS) { result.await() }
                ?: ShizukuInstallResult.Failed("Install timed out")
        } finally {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
            }
        }
    }
}
