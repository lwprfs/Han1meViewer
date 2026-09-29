package com.yenaly.han1meviewer.util

import com.google.common.util.concurrent.ListenableFuture
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.exception.CloudFlareBlockedException
import com.yenaly.han1meviewer.logic.exception.IPBlockedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <R> ListenableFuture<R>.await(): R {

    if (isDone) {
        try {
            return get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }
    return suspendCancellableCoroutine { cancellableContinuation ->
        addListener(
            {
                try {
                    cancellableContinuation.resume(get())
                } catch (throwable: Throwable) {
                    val cause = throwable.cause ?: throwable
                    when (throwable) {
                        is java.util.concurrent.CancellationException ->
                            cancellableContinuation.cancel(cause)

                        else -> cancellableContinuation.resumeWithException(cause)
                    }
                }
            },
            DirectExecutor
        )

        cancellableContinuation.invokeOnCancellation {
            cancel(false)
        }
    }
}

suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
        })
        continuation.invokeOnCancellation { cancel() }
    }
}

inline fun <R> runSuspendCatching(block: () -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (c: CancellationException) {
        throw c
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

private data object DirectExecutor : Executor {

    override fun execute(command: Runnable) {
        command.run()
    }
}

fun Throwable.toNetworkErrorMessageRes(): Int {
    val rawMessage = message.orEmpty().lowercase()
    return when {
        this is UnknownHostException ||
                rawMessage.contains("unable to resolve host") ||
                rawMessage.contains("no address associated with hostname") -> {
            R.string.home_error_dns
        }

        this is SocketTimeoutException || rawMessage.contains("timeout") -> {
            R.string.home_error_timeout
        }

        this is SSLHandshakeException ||
                rawMessage.contains("ssl") ||
                rawMessage.contains("certificate") -> {
            R.string.home_error_ssl
        }

        this is ConnectException || rawMessage.contains("failed to connect") -> {
            R.string.home_error_connect
        }

        this is SocketException && rawMessage.contains("connection reset") -> {
            R.string.home_error_connection_interrupted
        }

        this is IPBlockedException -> {
            R.string.cloudflare_ip_block_warning
        }

        this is CloudFlareBlockedException -> {
            R.string.cloudflare_network_mismatch
        }

        rawMessage.contains("connection reset") -> {
            R.string.home_error_connection_reset
        }

        rawMessage.contains("403") -> {
            R.string.home_error_forbidden
        }

        rawMessage.contains("404") -> {
            R.string.home_error_not_found
        }

        rawMessage.contains("500") || rawMessage.contains("502") ||
                rawMessage.contains("503") || rawMessage.contains("504") -> {
            R.string.home_error_server_unavailable
        }

        else -> {
            R.string.home_error_generic
        }
    }
}
