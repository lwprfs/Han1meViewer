package com.yenaly.han1meviewer.logic.network

import com.yenaly.han1meviewer.logic.network.interceptor.UserAgentInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed interface MirrorProbeResult {
    data object Pending : MirrorProbeResult

    data class Ok(val millis: Int) : MirrorProbeResult

    data class Unexpected(val code: Int) : MirrorProbeResult

    data class Failed(val message: String) : MirrorProbeResult
}

object MirrorLatency {

    private const val ROUND_TRIPS = 2.5

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor(UserAgentInterceptor)
            .dns(HDns())
            .proxySelector(HProxySelector())
            .build()
    }

    val httpClient: OkHttpClient
        get() = client

    fun probe(node: MirrorNode): MirrorProbeResult {
        val hasStatusPage = node.statusUrl != null
        val url = if (hasStatusPage) {
            "${node.statusUrl}?ts=${System.currentTimeMillis()}"
        } else {
            node.baseUrl
        }
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            val started = System.nanoTime()
            client.newCall(request).execute().use { response ->
                val body = if (hasStatusPage) response.body.string().trim() else ""
                val elapsedMs = (System.nanoTime() - started) / 1_000_000.0
                val millis = (elapsedMs / ROUND_TRIPS).toInt().coerceAtLeast(1)
                val healthy = if (hasStatusPage) {
                    response.isSuccessful && body == "ok"
                } else {
                    response.code < 500
                }
                if (healthy) MirrorProbeResult.Ok(millis) else MirrorProbeResult.Unexpected(response.code)
            }
        } catch (e: Exception) {
            MirrorProbeResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    fun fastestHost(results: Map<String, MirrorProbeResult>): String? =
        results.entries
            .mapNotNull { (host, result) -> (result as? MirrorProbeResult.Ok)?.let { host to it.millis } }
            .minByOrNull { it.second }
            ?.first
}
