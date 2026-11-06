package com.yenaly.han1meviewer.MissAV.cloudflare

import android.content.Context
import android.content.Intent
import android.util.Log
import com.yenaly.han1meviewer.MissAV.common.MissAvConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.coroutines.resume

object MissAvCloudflareSolver {

    private const val TAG = "MissAvCfSolver"
    private const val TIMEOUT_MS = 120_000L

    fun hasClearance(): Boolean {
        val host = MissAvConstants.getPrimaryUrl().toHttpUrlOrNull()?.host ?: return false
        return MissAvCloudflareCookieManager.hasValidCookieForHost(host)
    }

    suspend fun solve(context: Context): Boolean {
        val app = context.applicationContext
        withContext(Dispatchers.Main) {
            withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine<Unit> { cont ->
                    MissAvCloudflareActivity.onFinished = {
                        if (cont.isActive) cont.resume(Unit)
                    }
                    cont.invokeOnCancellation { MissAvCloudflareActivity.onFinished = null }
                    try {
                        app.startActivity(
                            Intent(app, MissAvCloudflareActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                putExtra(MissAvCloudflareActivity.EXTRA_URL, MissAvConstants.getPrimaryUrl())
                            }
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open verification screen", e)
                        MissAvCloudflareActivity.onFinished = null
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
            }
        }
        return hasClearance()
    }
}
