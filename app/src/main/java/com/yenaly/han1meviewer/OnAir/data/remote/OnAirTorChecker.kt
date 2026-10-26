package com.yenaly.han1meviewer.OnAir.data.remote

import android.util.Log
import com.yenaly.han1meviewer.logic.network.HDns
import com.yenaly.han1meviewer.logic.network.HProxySelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class OnAirTorStatus(
    val connected: Boolean,
    val isTorExit: Boolean,
    val ip: String?,
    val message: String,
    val failure: TorFailure? = null,
)

enum class TorFailure {
    NetworkUnreachable,
    Timeout,
    NonTorExit,
    Unknown,
}

object OnAirTorChecker {

    private const val TAG = "OnAirTorChecker"
    private const val CHECK_URL = "https://check.torproject.org/api/ip"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .dns(HDns())
        .proxySelector(HProxySelector())
        .build()

    suspend fun check(): OnAirTorStatus = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(CHECK_URL)
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext OnAirTorStatus(
                        connected = false,
                        isTorExit = false,
                        ip = null,
                        message = "check.torproject.org returned HTTP ${response.code}",
                        failure = TorFailure.NetworkUnreachable,
                    )
                }
                val body = response.body.string()
                Log.d(TAG, "check.torproject.org response: $body")
                val ip = Regex(""""IP"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.getOrNull(1)
                val isTor = Regex(""""IsTor"\s*:\s*(true|false)""", RegexOption.IGNORE_CASE)
                    .find(body)?.groupValues?.getOrNull(1)?.equals("true", ignoreCase = true) == true
                if (!isTor) {
                    OnAirTorStatus(
                        connected = true,
                        isTorExit = false,
                        ip = ip,
                        message = "Not routed through Tor (${ip ?: "unknown IP"})",
                        failure = TorFailure.NonTorExit,
                    )
                } else {
                    OnAirTorStatus(
                        connected = true,
                        isTorExit = true,
                        ip = ip,
                        message = "Connected through Tor${ip?.let { " ($it)" } ?: ""}",
                    )
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Tor check timeout", e)
            OnAirTorStatus(
                connected = false,
                isTorExit = false,
                ip = null,
                message = "Timed out contacting check.torproject.org. Is Tor running?",
                failure = TorFailure.Timeout,
            )
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Tor check DNS failure", e)
            OnAirTorStatus(
                connected = false,
                isTorExit = false,
                ip = null,
                message = "Cannot resolve check.torproject.org. Turn on Tor and try again.",
                failure = TorFailure.NetworkUnreachable,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Tor check failed", e)
            OnAirTorStatus(
                connected = false,
                isTorExit = false,
                ip = null,
                message = "Tor check failed: ${e.message ?: e.javaClass.simpleName}",
                failure = TorFailure.Unknown,
            )
        }
    }
}
