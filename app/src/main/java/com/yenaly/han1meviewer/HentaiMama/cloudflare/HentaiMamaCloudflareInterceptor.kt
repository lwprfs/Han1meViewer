package com.yenaly.han1meviewer.HentaiMama.cloudflare

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaConstants
import com.yenaly.han1meviewer.ui.component.GlobalToasts
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.CountDownLatch

class HentaiMamaCloudflareInterceptor(
    private val context: Context
) : Interceptor {

    companion object {
        private const val TAG = "HMCloudflareInterceptor"
        private val HOST_HINTS = listOf(
            "hentaimama.io",
            "hentaimama.com",
            "hentaimama.net",
        )
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val host = request.url.host
        if (!isTargetHost(host)) return response
        if (!isChallenge(response)) return response

        val url = request.url.toString()
        response.close()

        Log.w(TAG, "Cloudflare challenge detected for $url")

        if (HentaiMamaCloudflareCookieManager.hasValidCookieForHost(host)) {
            val retry = chain.proceed(request)
            if (!isChallenge(retry)) return retry
            retry.close()
            Log.w(TAG, "Retry with stored cookie still 403; opening solver")
        }

        val latch = CountDownLatch(1)

        HentaiMamaCloudflareActivity.onFinished?.invoke()
        HentaiMamaCloudflareActivity.onFinished = { latch.countDown() }

        try {
            val intent = Intent(context, HentaiMamaCloudflareActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(HentaiMamaCloudflareActivity.EXTRA_URL, url)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                GlobalToasts.show(
                    "Cloudflare activity failed to start: ${e.javaClass.simpleName}",
                    level = GlobalToasts.ToastLevel.ERROR
                )
            }
            HentaiMamaCloudflareActivity.onFinished?.invoke()
            HentaiMamaCloudflareActivity.onFinished = null
        }

        latch.await()

        return chain.proceed(request)
    }

    private fun isChallenge(response: Response): Boolean {
        if (response.code != 403) return false

        if (response.header("cf-mitigated") == "challenge") return true

        val server = response.header("Server").orEmpty()
        if (server.contains("cloudflare", ignoreCase = true)) return true

        val body = runCatching { response.peekBody(8192).string() }.getOrDefault("")
        if (body.isEmpty()) return false

        return body.contains("Just a moment", ignoreCase = true) ||
                body.contains("cf-chl", ignoreCase = true) ||
                body.contains("__cf_chl", ignoreCase = true) ||
                body.contains("challenge-platform", ignoreCase = true) ||
                body.contains("cf_clearance", ignoreCase = true) ||
                body.contains("Attention Required", ignoreCase = true) ||
                body.contains("403 Forbidden", ignoreCase = true)
    }

    private fun isTargetHost(host: String): Boolean {
        val normalized = host.lowercase()
        if (HOST_HINTS.any { normalized == it || normalized.endsWith(".$it") }) return true
        return runCatching {
            HentaiMamaConstants.BASE_URL.contains(normalized, ignoreCase = true)
        }.getOrDefault(false)
    }
}
