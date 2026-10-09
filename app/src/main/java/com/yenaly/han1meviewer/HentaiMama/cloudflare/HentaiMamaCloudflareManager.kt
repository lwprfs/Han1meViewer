package com.yenaly.han1meviewer.HentaiMama.cloudflare

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaConstants
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object HentaiMamaCloudflareManager {

    private const val TAG = "HMCloudflareManager"
    private const val VERIFICATION_TIMEOUT_SECONDS = 120L

    private val isVerifying = AtomicBoolean(false)
    private var verificationLatch: CountDownLatch? = null

    suspend fun handleChallenge(context: Context, url: String): Boolean {
        if (isVerifying.compareAndSet(false, true)) {
            Log.d(TAG, "First challenge detected. This request will manage the verification.")
            verificationLatch = CountDownLatch(1)
            launchVerificationActivity(context, url)
            return awaitVerificationResult()
        } else {
            Log.d(TAG, "Another verification is in progress. Waiting for it to complete...")
            return awaitVerificationResult()
        }
    }

    private fun awaitVerificationResult(): Boolean {
        return try {
            val latch = verificationLatch
            if (latch == null) {
                Log.w(TAG, "Latch was null, checking cookie directly.")
                return checkCookieValidity()
            }
            val completed = latch.await(VERIFICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!completed) {
                Log.w(TAG, "Verification timed out after $VERIFICATION_TIMEOUT_SECONDS seconds.")
                resetState()
                return false
            }
            Log.d(TAG, "Verification latch released. Checking cookie validity.")
            checkCookieValidity()
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while waiting for Cloudflare verification.", e)
            Thread.currentThread().interrupt()
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while waiting for verification.", e)
            false
        }
    }

    private fun checkCookieValidity(): Boolean {
        val host = HentaiMamaConstants.BASE_URL.toHttpUrlOrNull()?.host ?: return false
        return HentaiMamaCloudflareCookieManager.hasValidCookieForHost(host)
    }

    private fun launchVerificationActivity(context: Context, url: String) {
        HentaiMamaCloudflareActivity.onFinished = {
            Log.d(TAG, "CloudflareActivity has finished. Releasing latch.")
            resetState()
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
            resetState()
        }
    }

    private fun resetState() {
        isVerifying.set(false)
        verificationLatch?.countDown()
        verificationLatch = null
        HentaiMamaCloudflareActivity.onFinished = null
    }

    fun forceReset() {
        Log.d(TAG, "Force resetting Cloudflare manager state.")
        resetState()
    }
}
