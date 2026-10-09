package com.yenaly.han1meviewer.HentaiMama.cloudflare

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
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

        if (!isChallenge(response)) return response
        if (!isTargetHost(request.url.host)) return response

        val url = request.url.toString()
        val host = request.url.host

        response.close()

        if (HentaiMamaCloudflareCookieManager.hasValidCookieForHost(host)) {
            return chain.proceed(request)
        }

        val latch = CountDownLatch(1)

        HentaiMamaCloudflareActivity.onFinished?.invoke()
        HentaiMamaCloudflareActivity.onFinished = { latch.countDown() }

        try {
            val intent = Intent(context, HentaiMamaCloudflareActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
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
        val body = runCatching { response.peekBody(2048).string() }.getOrDefault("")
        return body.contains("Just a moment", ignoreCase = true) ||
                body.contains("cf-chl", ignoreCase = true) ||
                body.contains("__cf_chl", ignoreCase = true) ||
                body.contains("challenge-platform", ignoreCase = true)
    }

    private fun isTargetHost(host: String): Boolean {
        val normalized = host.lowercase()
        if (HOST_HINTS.any { normalized == it || normalized.endsWith(".$it") }) return true
        return runCatching {
            HentaiMamaConstants.BASE_URL.contains(normalized, ignoreCase = true)
        }.getOrDefault(false)
    }
}
