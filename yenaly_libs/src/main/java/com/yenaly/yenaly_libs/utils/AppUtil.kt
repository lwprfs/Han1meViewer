@file:JvmName("AppUtil")

package com.yenaly.yenaly_libs.utils

import android.content.pm.ApplicationInfo
import androidx.core.content.pm.PackageInfoCompat

val appName: String
    get() = applicationContext.applicationInfo
        .loadLabel(applicationContext.packageManager).toString()

val appLocalVersionName: String?
    get() {
        return applicationContext.packageManager.getPackageInfo(
            applicationContext.packageName, 0
        ).versionName
    }

val appLocalVersionCode: Long
    get() {
        val packageInfo = applicationContext.packageManager.getPackageInfo(
            applicationContext.packageName, 0
        )
        return PackageInfoCompat.getLongVersionCode(packageInfo)
    }

val appScreenWidth: Int get() = applicationContext.resources.displayMetrics.widthPixels

val appScreenHeight: Int get() = applicationContext.resources.displayMetrics.heightPixels

@Deprecated("Use BuildConfig.DEBUG instead", ReplaceWith("BuildConfig.DEBUG"))
val isDebugEnabled: Boolean
    get() = 0 != applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE
