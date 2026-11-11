package com.yenaly.han1meviewer.util.cloudflare

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

open class CloudflareChallengeInterceptor(
    private val context: Context,
    private val tag: String,
    private val cookieStore: CloudflareCookieStore,
    private val manager: CloudflareVerificationManager,
    private val hostnames: () -> Array<String>,
    private val baseUrls: () -> List<String>,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val host = request.url.host
        if (!isTargetHost(host)) return response
        if (!isChallenge(response)) return response

        Log.w(tag, "Cloudflare challenge detected for ${request.url}")
        response.close()

        if (cookieStore.hasValidCookieForHost(host)) {
            val retryResponse = chain.proceed(request)
            if (!isChallenge(retryResponse)) {
                Log.i(tag, "Retry with existing cookie succeeded for ${request.url}")
                return retryResponse
            }
            retryResponse.close()
            Log.w(tag, "Retry with existing cookie still 403. Marking as rejected.")
            cookieStore.markCurrentCookieRejected(host)
        }

        val solved = runBlocking {
            manager.handleChallenge(context, request.url.toString())
        }

        if (solved) {
            Log.i(tag, "Verification successful. Retrying original request for ${request.url}")
            val finalResponse = chain.proceed(request)
            if (isChallenge(finalResponse)) {
                Log.e(tag, "Final retry still got a challenge for ${request.url}")
                finalResponse.close()
                return buildErrorResponse(request, 403, "Cloudflare challenge persists after verification")
            }
            return finalResponse
        } else {
            Log.e(tag, "Cloudflare verification failed or timed out for ${request.url}")
            return buildErrorResponse(request, 403, "Cloudflare challenge not solved")
        }
    }

    private fun buildErrorResponse(
        request: Request,
        code: Int,
        message: String,
    ): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(message)
            .body(message.toResponseBody(null))
            .build()
    }

    private fun isChallenge(response: Response): Boolean {
        if (response.code != 403) return false
        if (response.header("cf-mitigated") == "challenge") return true

        val server = response.header("Server").orEmpty()
        if (server.contains("cloudflare", ignoreCase = true)) {
            return true
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
        if (hostnames().any { normalized == it || normalized.endsWith(".$it") }) {
            return true
        }
        return runCatching {
            baseUrls().any { it.contains(normalized, ignoreCase = true) }
        }.getOrDefault(false)
    }
}
