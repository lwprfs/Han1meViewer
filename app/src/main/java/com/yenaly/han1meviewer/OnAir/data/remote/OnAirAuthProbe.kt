package com.yenaly.han1meviewer.OnAir.data.remote

import android.content.Context
import android.util.Log
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetwork.baseUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class OnAirProbeFailure {
    NoCookies,
    RateLimited,
    ServerError,
    SiteDown,
    NotFound,
    SessionExpired,
    Network,
    Unknown,
}

data class OnAirProbeResult(
    val ok: Boolean,
    val failure: OnAirProbeFailure? = null,
    val userMessage: String? = null,
    val httpCode: Int? = null,
    val suggestSwitchSite: Boolean = false,
    val suggestWait: Boolean = false,
    val suggestRelogin: Boolean = false,
)

object OnAirAuthProbe {

    private const val TAG = "OnAirAuthProbe"

    suspend fun probe(context: Context): OnAirProbeResult = withContext(Dispatchers.IO) {
        if (!OnAirCookieManager.hasCookies) {
            return@withContext OnAirProbeResult(
                ok = false,
                failure = OnAirProbeFailure.NoCookies,
                userMessage = "No cookies saved. Sign in or paste cookies to continue.",
                suggestRelogin = true,
            )
        }
        try {
            val url = baseUrl.trimEnd('/') + "/settings"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "text/html")
                .build()
            OnAirNetwork.sharedOkHttpClient.newCall(request).execute().use { resp ->
                val code = resp.code
                val body = resp.body?.string().orEmpty()
                Log.d(TAG, "probe /settings: code=$code, htmlLen=${body.length}")

                when {
                    code == 429 -> OnAirProbeResult(
                        ok = false,
                        failure = OnAirProbeFailure.RateLimited,
                        userMessage = "OnAir is rate limiting requests (HTTP 429). " +
                                "Wait a few minutes before trying again.",
                        httpCode = code,
                        suggestWait = true,
                    )

                    code == 401 -> {
                        OnAirCookieManager.clearCookies()
                        OnAirProbeResult(
                            ok = false,
                            failure = OnAirProbeFailure.SessionExpired,
                            userMessage = "Your session was rejected (HTTP 401). " +
                                    "The cookies are no longer valid — sign in again.",
                            httpCode = code,
                            suggestRelogin = true,
                        )
                    }

                    code == 403 -> OnAirProbeResult(
                        ok = false,
                        failure = OnAirProbeFailure.SessionExpired,
                        userMessage = "OnAir refused the request (HTTP 403). " +
                                "Try switching mirrors, or wait a moment and retry.",
                        httpCode = code,
                        suggestSwitchSite = true,
                        suggestWait = true,
                    )

                    code in 500..599 -> OnAirProbeResult(
                        ok = false,
                        failure = OnAirProbeFailure.ServerError,
                        userMessage = "OnAir returned a server error (HTTP $code). " +
                                "Wait a bit or switch mirrors.",
                        httpCode = code,
                        suggestSwitchSite = true,
                        suggestWait = true,
                    )

                    code == 404 -> OnAirProbeResult(
                        ok = false,
                        failure = OnAirProbeFailure.SiteDown,
                        userMessage = "This mirror returned 404. Switch mirrors.",
                        httpCode = code,
                        suggestSwitchSite = true,
                    )

                    !resp.isSuccessful -> OnAirProbeResult(
                        ok = false,
                        failure = OnAirProbeFailure.Unknown,
                        userMessage = "Unexpected response (HTTP $code). Try again.",
                        httpCode = code,
                        suggestSwitchSite = true,
                    )

                    else -> {
                        val looksLikeLogin =
                            body.contains("action=\"/login\"", ignoreCase = true) ||
                                    body.contains("<title>Log in", ignoreCase = true) ||
                                    body.contains("<title>Login", ignoreCase = true)
                        val hasAuthMarker =
                            body.contains("nav-username", ignoreCase = true) ||
                                    body.contains("action=\"/logout\"", ignoreCase = true)
                        if (!hasAuthMarker || (looksLikeLogin && !hasAuthMarker)) {
                            OnAirCookieManager.clearCookies()
                            OnAirProbeResult(
                                ok = false,
                                failure = OnAirProbeFailure.SessionExpired,
                                userMessage = "Your cookies are not signed in on this mirror. " +
                                        "Sign in again or paste fresh cookies.",
                                httpCode = code,
                                suggestRelogin = true,
                            )
                        } else {
                            OnAirProbeResult(ok = true)
                        }
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "probe timeout", e)
            OnAirProbeResult(
                ok = false,
                failure = OnAirProbeFailure.Network,
                userMessage = "The request timed out. Tor might be slow.",
                suggestWait = true,
            )
        } catch (e: UnknownHostException) {
            Log.e(TAG, "probe DNS failure", e)
            OnAirProbeResult(
                ok = false,
                failure = OnAirProbeFailure.Network,
                userMessage = "Cannot resolve the .onion address. Confirm Tor is running.",
                suggestSwitchSite = true,
            )
        } catch (e: Exception) {
            Log.e(TAG, "probe failed", e)
            OnAirProbeResult(
                ok = false,
                failure = OnAirProbeFailure.Unknown,
                userMessage = "Could not reach OnAir: " +
                        (e.message ?: e.javaClass.simpleName),
                suggestSwitchSite = true,
            )
        }
    }
}
