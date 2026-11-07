package com.yenaly.han1meviewer.OnAir.data.remote

import android.util.Log
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.network.HDns
import com.yenaly.han1meviewer.logic.network.HProxySelector
import com.yenaly.yenaly_libs.utils.applicationContext
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
                        message = applicationContext.getString(R.string.onair_check_torproject_org_returned_http, response.code),
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
                        message = applicationContext.getString(R.string.onair_not_routed_through_tor, ip ?: "unknown IP"),
                        failure = TorFailure.NonTorExit,
                    )
                } else {
                    OnAirTorStatus(
                        connected = true,
                        isTorExit = true,
                        ip = ip,
                        message = applicationContext.getString(R.string.onair_connected_through_tor, ip?.let { " ($it)" } ?: ""),
                    )
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Tor check timeout", e)
            OnAirTorStatus(
                connected = false,
                isTorExit = false,
                ip = null,
                message = applicationContext.getString(R.string.onair_tor_timeout),
                failure = TorFailure.Timeout,
            )
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Tor check DNS failure", e)
            OnAirTorStatus(
                connected = false,
                isTorExit = false,
                ip = null,
                message = applicationContext.getString(R.string.onair_tor_dns_failed),
                failure = TorFailure.NetworkUnreachable,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Tor check failed", e)
            OnAirTorStatus(
                connected = false,
                isTorExit = false,
                ip = null,
                message = applicationContext.getString(R.string.onair_tor_check_failed, e.message ?: e.javaClass.simpleName),
                failure = TorFailure.Unknown,
            )
        }
    }
}
