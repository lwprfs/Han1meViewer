package com.yenaly.han1meviewer.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.yenaly.han1meviewer.BuildConfig
import com.yenaly.han1meviewer.FILE_PROVIDER_AUTHORITY
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.model.github.Latest
import com.yenaly.han1meviewer.util.install.ApkInstallMethod
import com.yenaly.han1meviewer.util.install.ApkInstallService
import java.io.File

val Context.updateFile: File get() = File(applicationContext.cacheDir, "update.apk")

fun checkNeedUpdate(versionName: String): Boolean {
    val latestVersionCode = versionName.substringAfter("+", "").toIntOrNull() ?: Int.MAX_VALUE
    return BuildConfig.VERSION_CODE < latestVersionCode
}

internal fun Context.getUpdateIfExists(latest: Latest): File? {
    val nodeId = Preferences.updateNodeId
    return updateFile.takeIf { file ->
        !BuildConfig.DEBUG && file.exists() && nodeId.isNotEmpty() && nodeId == latest.nodeId
    }
}

private const val VERSIONS_TAG = "Versions"
private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

suspend fun Context.installApkPackage(file: File) {
    val method = Preferences.apkInstallMethod

    if (method != ApkInstallMethod.SHIZUKU && !requestInstallPermission()) return

    if (method != ApkInstallMethod.LEGACY) {
        val started = runCatching {
            ContextCompat.startForegroundService(
                applicationContext,
                ApkInstallService.getIntent(applicationContext, file, method),
            )
        }
        if (started.isSuccess) return
        Log.w(VERSIONS_TAG, "无法启动安装服务，回退到系统安装器", started.exceptionOrNull())
    }
    startActivity(legacyInstallIntent(file))
}

fun Context.apkInstallPendingIntent(file: File): PendingIntent {
    val method = Preferences.apkInstallMethod
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    if (method == ApkInstallMethod.LEGACY) {
        return PendingIntent.getActivity(this, 0, legacyInstallIntent(file), flags)
    }
    val intent = ApkInstallService.getIntent(applicationContext, file, method)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        PendingIntent.getForegroundService(this, 0, intent, flags)
    } else {
        PendingIntent.getService(this, 0, intent, flags)
    }
}

private fun Context.legacyInstallIntent(file: File): Intent {
    val uri = FileProvider.getUriForFile(applicationContext, FILE_PROVIDER_AUTHORITY, file)
    return Intent(Intent.ACTION_VIEW).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setDataAndType(uri, APK_MIME_TYPE)
    }
}
