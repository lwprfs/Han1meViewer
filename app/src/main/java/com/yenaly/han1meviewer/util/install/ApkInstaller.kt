package com.yenaly.han1meviewer.util.install

import android.app.Service
import java.io.File

enum class ApkInstallResult { SUCCESS, CANCELLED, FAILED }

abstract class ApkInstaller(
    protected val service: Service,
    private val onFinished: (ApkInstallResult) -> Unit,
) {

    abstract fun install(file: File)

    protected fun finish(result: ApkInstallResult) = onFinished(result)

    open fun onDestroy() {}
}
