package com.yenaly.han1meviewer.util.install

import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import com.yenaly.han1meviewer.R
import rikka.shizuku.ShizukuProvider

enum class ApkInstallMethod(val key: String, @StringRes val titleRes: Int) {

    LEGACY("legacy", R.string.apk_install_legacy),

    PACKAGE_INSTALLER("package_installer", R.string.apk_install_package_installer),

    SHIZUKU("shizuku", R.string.apk_install_shizuku);

    companion object {

        fun fromKey(key: String?): ApkInstallMethod? = entries.firstOrNull { it.key == key }

        fun available(context: Context): List<ApkInstallMethod> =
            if (context.hasMiuiPackageInstaller) {
                entries.filter { it != PACKAGE_INSTALLER }
            } else {
                entries.toList()
            }

        fun default(context: Context): ApkInstallMethod =
            if (context.hasMiuiPackageInstaller) LEGACY else PACKAGE_INSTALLER

        fun resolve(context: Context, stored: String?): ApkInstallMethod {
            val method = fromKey(stored) ?: return default(context)
            return when {
                method == PACKAGE_INSTALLER && context.hasMiuiPackageInstaller -> LEGACY
                method == SHIZUKU && !context.isShizukuInstalled -> default(context)
                else -> method
            }
        }
    }
}

private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
private const val MIUI_INSTALLER_PACKAGE = "com.miui.packageinstaller"

private fun Context.isPackageInstalled(packageName: String): Boolean = try {
    packageManager.getApplicationInfo(packageName, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}

val Context.hasMiuiPackageInstaller: Boolean
    get() = isPackageInstalled(MIUI_INSTALLER_PACKAGE)

val Context.isShizukuInstalled: Boolean
    get() = isPackageInstalled(SHIZUKU_PACKAGE) || try {
        packageManager.getPermissionInfo(ShizukuProvider.PERMISSION, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
