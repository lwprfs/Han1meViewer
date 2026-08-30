package com.yenaly.han1meviewer.MissAV

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.yenaly.han1meviewer.R
import okhttp3.Interceptor
import okhttp3.Response

class MissAvCloudflareInterceptor(
    private val context: Context
) : Interceptor {

    companion object {
        @Volatile
        var pendingUrl: String? = null
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code == 403 && response.header("cf-mitigated") == "challenge") {
            response.close()
            val url = request.url.toString()
            if (MissAvConstants.MISSAV_HOSTNAME.any { url.contains(it) }) {
                throw CloudflareChallengeException(url)
            }
        }

        if (response.code == 403) {
            val body = response.peekBody(1024).string()
            if (body.contains("Just a moment") || body.contains("Cloudflare") || body.contains("cf_")) {
                response.close()
                val url = request.url.toString()
                if (MissAvConstants.MISSAV_HOSTNAME.any { url.contains(it) }) {
                    throw CloudflareChallengeException(url)
                }
            }
        }

        return response
    }
}

class CloudflareChallengeException(val url: String) : Exception("Cloudflare challenge required for $url")