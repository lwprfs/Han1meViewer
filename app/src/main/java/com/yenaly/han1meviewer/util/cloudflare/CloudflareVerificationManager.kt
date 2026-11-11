package com.yenaly.han1meviewer.util.cloudflare

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared "open the WebView, wait for cf_clearance" coordinator.
 *
 * Several requests can hit a challenge at the same time; only the first one launches
 * [activityClass], the rest just wait for the cookie to show up in [cookieStore].
 *
 * @param baseUrl lazily resolved because some sites (mirrors) can change it at runtime.
 */
open class CloudflareVerificationManager(
    private val tag: String,
    private val cookieStore: CloudflareCookieStore,
    private val activityClass: Class<out BaseCloudflareActivity>,
    private val baseUrl: () -> String,
) {

    private companion object {
        const val VERIFICATION_TIMEOUT_MS = 120_000L
    }

    private val isVerifying = AtomicBoolean(false)

    suspend fun handleChallenge(context: Context, url: String): Boolean {
        val first = isVerifying.compareAndSet(false, true)
        if (first) {
            Log.d(tag, "First challenge detected. This request will manage the verification.")
            launchVerificationActivity(context, url)
        } else {
            Log.d(tag, "Another verification is in progress. Waiting for it to complete...")
        }

        val result = awaitVerificationResult()
        if (first) {
            releaseState()
        }
        return result
    }

    private suspend fun awaitVerificationResult(): Boolean =
        withContext(Dispatchers.IO) {
            val host = baseUrl().toHttpUrlOrNull()?.host
            val deadline = System.currentTimeMillis() + VERIFICATION_TIMEOUT_MS

            while (System.currentTimeMillis() < deadline) {
                if (host != null && cookieStore.hasValidCookieForHost(host)) {
                    Log.d(tag, "Valid cf_clearance observed for $host")
                    return@withContext true
                }
                if (!isVerifying.get()) {
                    return@withContext host != null && cookieStore.hasValidCookieForHost(host)
                }
                Thread.sleep(250L)
            }

            Log.w(tag, "Verification timed out after $VERIFICATION_TIMEOUT_MS ms")
            false
        }

    private fun launchVerificationActivity(context: Context, url: String) {
        BaseCloudflareActivity.setOnFinished(activityClass) {
            Log.d(tag, "CloudflareActivity has finished. Releasing state.")
            releaseState()
        }

        try {
            val intent = Intent(context, activityClass).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(BaseCloudflareActivity.EXTRA_URL, url)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "Failed to start CloudflareActivity", e)
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
        BaseCloudflareActivity.setOnFinished(activityClass, null)
    }

    fun forceReset() {
        Log.d(tag, "Force resetting Cloudflare manager state.")
        releaseState()
    }
}
