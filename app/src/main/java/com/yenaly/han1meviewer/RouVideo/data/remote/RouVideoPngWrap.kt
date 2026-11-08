package com.yenaly.han1meviewer.RouVideo.data.remote

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

object RouVideoPngWrap {

    private val SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    private const val CHUNK_ROUD = 0x726F5564

    fun isWrapped(bytes: ByteArray): Boolean =
        bytes.size >= SIGNATURE.size && SIGNATURE.indices.all { bytes[it] == SIGNATURE[it] }

    fun unwrap(bytes: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(bytes)
        var pos = SIGNATURE.size
        while (pos + 8 <= bytes.size) {
            val length = buffer.getInt(pos)
            val type = buffer.getInt(pos + 4)
            val data = pos + 8
            if (length < 0 || data.toLong() + length > bytes.size) break
            if (type == CHUNK_ROUD) {
                if (length < 1) throw IOException("Empty roUd chunk")
                val flags = bytes[data].toInt()
                val payload = bytes.copyOfRange(data + 1, data + length)
                return if (flags and 1 != 0) inflate(payload) else payload
            }
            pos = data + length + 4
        }
        throw IOException("PNG-wrapped response has no roUd chunk")
    }

    private fun inflate(payload: ByteArray): ByteArray = try {
        InflaterInputStream(ByteArrayInputStream(payload)).use { it.readBytes() }
    } catch (_: IOException) {

        InflaterInputStream(ByteArrayInputStream(payload), Inflater(true)).use { it.readBytes() }
    }
}

class RouVideoPngUnwrapInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!response.isSuccessful) return response
        val contentType = response.header("Content-Type").orEmpty()
        val looksLikePng = contentType.startsWith("image/png", ignoreCase = true) ||
                response.request.url.encodedPath.endsWith(".png", ignoreCase = true)
        if (!looksLikePng) return response

        val raw = response.body.bytes()
        val out = if (RouVideoPngWrap.isWrapped(raw)) RouVideoPngWrap.unwrap(raw) else raw
        return response.newBuilder()
            .body(out.toResponseBody("application/octet-stream".toMediaType()))
            .header("Content-Type", "application/octet-stream")
            .removeHeader("Content-Length")
            .build()
    }
}
