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

private val versionCodeRegex = Regex("""\+(\d+)""")

/**
 * 版本号形如 `v1.2.0-ci+26110910`，`+` 后面是版本码。
 *
 * 只取 `+` 后面的数字，因此即使 tag 末尾带有 `.apk` 之类的后缀也能正确解析。
 * 解析不出版本码时视为「无法判断」，不提示更新，避免已是最新版本时仍然反复提示。
 */
fun checkNeedUpdate(versionName: String): Boolean {
    val latestVersionCode = versionCodeRegex.findAll(versionName).lastOrNull()
        ?.groupValues?.get(1)?.toIntOrNull()
    if (latestVersionCode == null) {
        Log.w(VERSIONS_TAG, "无法从版本号「$versionName」解析出版本码，跳过更新提示")
        return false
    }
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

/**
 * 按用户在设置里选的安装方式安装 [file]。
 *
 * - [ApkInstallMethod.LEGACY]：调起系统安装器
 * - [ApkInstallMethod.PACKAGE_INSTALLER] / [ApkInstallMethod.SHIZUKU]：交给 [ApkInstallService]；
 *   前台服务启动失败时回退到系统安装器
 */
suspend fun Context.installApkPackage(file: File) {
    val method = Preferences.apkInstallMethod
    // Shizuku 以 shell 权限安装，不需要「安装未知应用」权限
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

/** 点击「安装」通知时使用的 [PendingIntent]，同样遵循用户选择的安装方式。 */
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
