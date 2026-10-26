package com.yenaly.han1meviewer.OnAir.data.remote

import android.util.Log
import com.yenaly.han1meviewer.OnAir.data.model.OnAirMirrorStatus
import com.yenaly.han1meviewer.OnAir.data.parser.TorWatchStatusParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object OnAirMirrorStatusRepo {

    private const val TAG = "OnAirMirrorStatusRepo"
    private const val BASE = "https://tordotwatch.org"
    private const val PER_CHECK_TIMEOUT_MS = 40_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun check(host: String): OnAirMirrorStatus = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext OnAirMirrorStatus.Error(host, "Empty host")
        }
        try {
            val body = FormBody.Builder().add("url", host).build()
            val request = Request.Builder()
                .url("$BASE/is-it-offline")
                .header("User-Agent", "Mozilla/5.0 (Android) OnAirViewer")
                .header("Accept", "text/html")
                .post(body)
                .build()

            withTimeout(PER_CHECK_TIMEOUT_MS) {
                client.newCall(request).execute().use { resp ->
                    val html = resp.body?.string().orEmpty()
                    Log.d(TAG, "check($host): http=${resp.code} htmlLen=${html.length}")
                    if (!resp.isSuccessful && html.isBlank()) {
                        OnAirMirrorStatus.Error(host, "TorWatch HTTP ${resp.code}")
                    } else {
                        TorWatchStatusParser.parse(html, host)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "check($host) failed", e)
            OnAirMirrorStatus.Error(host, e.message ?: "Network error")
        }
    }

    suspend fun checkAll(
        hosts: List<String>,
        onResult: suspend (OnAirMirrorStatus) -> Unit,
    ) {
        hosts.forEach { host ->
            onResult(OnAirMirrorStatus.Checking(host))
            val result = check(host)
            onResult(result)
        }
    }
}
