package com.yenaly.han1meviewer.HentaiMama.cloudflare

import android.content.Context
import android.util.Log
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaConstants
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class HentaiMamaCloudflareInterceptor(
    private val context: Context
) : Interceptor {

    companion object {
        private const val TAG = "HMCloudflareInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)

        val host = request.url.host
        if (!isTargetHost(host)) {
            return response
        }

        if (!isChallenge(response)) {
            return response
        }

        Log.w(TAG, "Cloudflare challenge detected for $request.url")
        response.close()

        if (HentaiMamaCloudflareCookieManager.hasValidCookieForHost(host)) {
            val retryResponse = chain.proceed(request)
            if (!isChallenge(retryResponse)) {
                Log.i(TAG, "Retry with existing cookie succeeded for $request.url")
                return retryResponse
            }
            retryResponse.close()
            Log.w(TAG, "Retry with existing cookie still 403. Marking as rejected.")
            HentaiMamaCloudflareCookieManager.markCurrentCookieRejected(host)
        }

        val solved = runBlocking {
            HentaiMamaCloudflareManager.handleChallenge(context, request.url.toString())
        }

        if (solved) {
            Log.i(TAG, "Verification successful. Retrying original request for $request.url")
            val finalResponse = chain.proceed(request)
            if (isChallenge(finalResponse)) {
                Log.e(TAG, "Final retry still got a challenge for $request.url")
                finalResponse.close()
                return buildErrorResponse(request, 403, "Cloudflare challenge persists after verification")
            }
            return finalResponse
        } else {
            Log.e(TAG, "Cloudflare verification failed or timed out for $request.url")
            return buildErrorResponse(request, 403, "Cloudflare challenge not solved")
        }
    }

    private fun buildErrorResponse(
        request: okhttp3.Request,
        code: Int,
        message: String,
    ): Response {
        return Response.Builder()
            .request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(code)
            .message(message)
            .body(okhttp3.ResponseBody.create(null, message))
            .build()
    }

    private fun isChallenge(response: Response): Boolean {
        if (response.code != 403) return false
        if (response.header("cf-mitigated") == "challenge") return true

        val server = response.header("Server").orEmpty()
        if (server.contains("cloudflare", ignoreCase = true)) {
            val body = runCatching { response.peekBody(8192).string() }.getOrDefault("")
            if (body.isEmpty()) return true
        }

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
        if (HentaiMamaConstants.HOSTNAME.any { normalized == it || normalized.endsWith(".$it") }) {
            return true
        }
        return runCatching {
            HentaiMamaConstants.BASE_URL.contains(normalized, ignoreCase = true)
        }.getOrDefault(false)
    }
}
