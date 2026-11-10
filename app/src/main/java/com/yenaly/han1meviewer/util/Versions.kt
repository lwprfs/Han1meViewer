package com.yenaly.han1meviewer.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.yenaly.han1meviewer.BuildConfig
import com.yenaly.han1meviewer.FILE_PROVIDER_AUTHORITY
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.shizuku.ShizukuInstallResult
import com.yenaly.han1meviewer.shizuku.ShizukuUpdateInstaller
import com.yenaly.han1meviewer.shizuku.UpdateInstaller
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import com.yenaly.han1meviewer.logic.model.github.Latest
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

suspend fun Context.installApkPackage(file: File) {
    if (Preferences.updateInstaller == UpdateInstaller.SHIZUKU) {
        when (val result = ShizukuUpdateInstaller.install(this, file)) {
            ShizukuInstallResult.Success -> return
            else -> {
                val msgRes = when (result) {
                    ShizukuInstallResult.NotInstalled -> R.string.shizuku_not_installed
                    ShizukuInstallResult.NotRunning -> R.string.shizuku_not_running
                    ShizukuInstallResult.PermissionDenied -> R.string.shizuku_permission_denied
                    else -> null
                }
                val msg = if (msgRes != null) getString(msgRes)
                else getString(
                    R.string.shizuku_install_failed,
                    (result as? ShizukuInstallResult.Failed)?.message.orEmpty(),
                )
                GlobalToasts.show(msg, level = GlobalToasts.ToastLevel.WARNING)
                // fall through to the system installer
            }
        }
    }
    installApkWithSystem(file)
}

private suspend fun Context.installApkWithSystem(file: File) {
    val canInstall = requestInstallPermission()
    if (canInstall) {
        val uri = FileProvider.getUriForFile(this.applicationContext, FILE_PROVIDER_AUTHORITY, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(uri, "application/vnd.android.package-archive")
        }
        startActivity(intent)
    }
}
