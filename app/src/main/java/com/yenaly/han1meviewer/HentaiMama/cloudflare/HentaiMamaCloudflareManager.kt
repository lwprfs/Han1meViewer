package com.yenaly.han1meviewer.HentaiMama.cloudflare

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaConstants
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

object HentaiMamaCloudflareManager {

    private const val TAG = "HMCloudflareManager"
    private const val VERIFICATION_TIMEOUT_MS = 120_000L

    private val isVerifying = AtomicBoolean(false)
    private var pendingContinuation: ((Boolean) -> Unit)? = null
    private var pendingLatchOwnerThread: Thread? = null

    suspend fun handleChallenge(context: Context, url: String): Boolean {
        val first = isVerifying.compareAndSet(false, true)
        if (first) {
            Log.d(TAG, "First challenge detected. This request will manage the verification.")
            launchVerificationActivity(context, url)
        } else {
            Log.d(TAG, "Another verification is in progress. Waiting for it to complete...")
        }

        val result = awaitVerificationResult()
        if (first) {
            releaseState()
        }
        return result
    }

    private suspend fun awaitVerificationResult(): Boolean =
        withContext(Dispatchers.IO) {
            val checker = HentaiMamaCloudflareCookieManager
            val host = HentaiMamaConstants.BASE_URL.toHttpUrlOrNull()?.host
            val deadline = System.currentTimeMillis() + VERIFICATION_TIMEOUT_MS

            while (System.currentTimeMillis() < deadline) {
                if (host != null && checker.hasValidCookieForHost(host)) {
                    Log.d(TAG, "Valid cf_clearance observed for $host")
                    return@withContext true
                }
                if (pendingContinuation == null && !isVerifying.get()) {
                    return@withContext host != null && checker.hasValidCookieForHost(host)
                }
                Thread.sleep(250L)
            }

            Log.w(TAG, "Verification timed out after $VERIFICATION_TIMEOUT_MS ms")
            false
        }

    private fun launchVerificationActivity(context: Context, url: String) {
        HentaiMamaCloudflareActivity.onFinished = {
            Log.d(TAG, "CloudflareActivity has finished. Releasing state.")
            releaseState()
        }

        try {
            val intent = Intent(context, HentaiMamaCloudflareActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(HentaiMamaCloudflareActivity.EXTRA_URL, url)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start CloudflareActivity", e)
            Handler(Looper.getMainLooper()).post {
                GlobalToasts.show(
                    "Failed to start verification activity: ${e.javaClass.simpleName}",
                    level = GlobalToasts.ToastLevel.ERROR
                )
            }
            releaseState()
        }
    }

    @Synchronized
    private fun releaseState() {
        isVerifying.set(false)
        pendingContinuation = null
        pendingLatchOwnerThread = null
        HentaiMamaCloudflareActivity.onFinished = null
    }

    fun forceReset() {
        Log.d(TAG, "Force resetting Cloudflare manager state.")
        releaseState()
    }
}
