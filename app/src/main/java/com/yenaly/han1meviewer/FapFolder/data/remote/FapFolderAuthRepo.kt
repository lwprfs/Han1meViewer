package com.yenaly.han1meviewer.FapFolder.data.remote

import android.util.Log
import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.data.local.FapFolderSessionCache
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderSessionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class FapFolderProbeFailure {
    NoCookies,
    SessionExpired,
    CloudflareChallenge,
    RateLimited,
    ServerError,
    SiteDown,
    Network,
    Unknown,
}

data class FapFolderProbeResult(
    val ok: Boolean,
    val failure: FapFolderProbeFailure? = null,
    val userMessage: String? = null,
    val httpCode: Int? = null,
    val suggestSwitchCookies: Boolean = false,
    val suggestWait: Boolean = false,
    val suggestRelogin: Boolean = false,
    val viewerUsername: String? = null,
    val viewerAvatarUrl: String? = null,
    val viewerUserId: Long? = null,
)

sealed interface FapFolderAuthResult {
    data object Success : FapFolderAuthResult
    data class FormError(val message: String) : FapFolderAuthResult
    data class NetworkError(val throwable: Throwable) : FapFolderAuthResult
    data object InvalidCredentials : FapFolderAuthResult
    data object CloudflareChallenge : FapFolderAuthResult
    data object SessionExpired : FapFolderAuthResult
}

object FapFolderAuthRepo {

    private const val TAG = "FapFolderAuthRepo"

    private val loginMutex = Mutex()

    fun probe(context: android.content.Context) = flow {
        try {
            if (!FapFolderNetwork.hasAuthCookies()) {
                emit(
                    FapFolderProbeResult(
                        ok = false,
                        failure = FapFolderProbeFailure.NoCookies,
                        userMessage = "No FapFolder cookies saved. Please sign in or paste cookies.",
                        suggestRelogin = true,
                    )
                )
                return@flow
            }

            val url = FapFolderConstants.BASE_URL.trimEnd('/') + "/"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            FapFolderNetwork.sharedOkHttpClient.newCall(request).execute().use { resp ->
                val code = resp.code
                val body = resp.body.string().orEmpty()
                Log.d(TAG, "probe /: code=$code bodyLen=${body.length}")

                if (FapFolderNetwork.isChallengeResponse(body)) {
                    emit(
                        FapFolderProbeResult(
                            ok = false,
                            failure = FapFolderProbeFailure.CloudflareChallenge,
                            userMessage = "Cloudflare challenge. Wait a moment and try again.",
                            httpCode = code,
                            suggestWait = true,
                        )
                    )
                    return@use
                }

                when {
                    code == 429 -> emit(
                        FapFolderProbeResult(
                            ok = false,
                            failure = FapFolderProbeFailure.RateLimited,
                            userMessage = "FapFolder is rate limiting (HTTP 429). Wait a minute.",
                            httpCode = code,
                            suggestWait = true,
                        )
                    )

                    code == 401 || code == 403 -> {
                        FapFolderNetwork.clearCookies()
                        FapFolderSessionCache.clear()
                        emit(
                            FapFolderProbeResult(
                                ok = false,
                                failure = FapFolderProbeFailure.SessionExpired,
                                userMessage = "Session rejected (HTTP $code). Paste fresh cookies.",
                                httpCode = code,
                                suggestRelogin = true,
                            )
                        )
                    }

                    code in 500..599 -> emit(
                        FapFolderProbeResult(
                            ok = false,
                            failure = FapFolderProbeFailure.ServerError,
                            userMessage = "FapFolder server error (HTTP $code). Try later.",
                            httpCode = code,
                            suggestWait = true,
                        )
                    )

                    code == 404 -> emit(
                        FapFolderProbeResult(
                            ok = false,
                            failure = FapFolderProbeFailure.SiteDown,
                            userMessage = "FapFolder returned 404. Site may be down.",
                            httpCode = code,
                            suggestSwitchCookies = true,
                        )
                    )

                    code in 200..299 -> {
                        val session = FapFolderSessionParser.parse(body, url)
                        FapFolderSessionCache.update(session)
                        if (session.loggedIn) {
                            emit(
                                FapFolderProbeResult(
                                    ok = true,
                                    httpCode = code,
                                    viewerUsername = session.viewer.username,
                                    viewerAvatarUrl = session.viewer.avatarUrl,
                                    viewerUserId = session.viewer.userId,
                                )
                            )
                        } else {
                            FapFolderNetwork.clearCookies()
                            FapFolderSessionCache.clear()
                            emit(
                                FapFolderProbeResult(
                                    ok = false,
                                    failure = FapFolderProbeFailure.SessionExpired,
                                    userMessage = "Cookies are not signed in on this site. Sign in again.",
                                    httpCode = code,
                                    suggestRelogin = true,
                                )
                            )
                        }
                    }

                    else -> emit(
                        FapFolderProbeResult(
                            ok = false,
                            failure = FapFolderProbeFailure.Unknown,
                            userMessage = "Unexpected response (HTTP $code).",
                            httpCode = code,
                        )
                    )
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "probe timeout", e)
            emit(
                FapFolderProbeResult(
                    ok = false,
                    failure = FapFolderProbeFailure.Network,
                    userMessage = "FapFolder request timed out.",
                    suggestWait = true,
                )
            )
        } catch (e: UnknownHostException) {
            Log.e(TAG, "probe DNS failure", e)
            emit(
                FapFolderProbeResult(
                    ok = false,
                    failure = FapFolderProbeFailure.Network,
                    userMessage = "Cannot resolve fapfolder.club. Check your connection.",
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "probe failed", e)
            emit(
                FapFolderProbeResult(
                    ok = false,
                    failure = FapFolderProbeFailure.Unknown,
                    userMessage = "Could not reach FapFolder: ${e.message ?: e::class.simpleName}",
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    fun login(
        usernameOrEmail: String,
        password: String,
        remember: Boolean = true,
    ) = flow {
        loginMutex.withLock {
            try {
                val trimmedUser = usernameOrEmail.trim()
                if (trimmedUser.isEmpty() || password.isEmpty()) {
                    emit(FapFolderAuthResult.FormError("Username and password cannot be empty."))
                    return@withLock
                }

                val priming = Request.Builder()
                    .url(FapFolderConstants.BASE_URL.trimEnd('/') + "/signin")
                    .get()
                    .build()
                runCatching {
                    FapFolderNetwork.sharedOkHttpClient.newCall(priming).execute().use { resp ->
                        Log.d(TAG, "login priming /signin: ${resp.code}")
                    }
                }

                val form = FormBody.Builder()
                    .add("username_email", trimmedUser)
                    .add("password", password)
                    .add("remember", if (remember) "on" else "off")
                    .build()

                val request = Request.Builder()
                    .url(FapFolderConstants.BASE_URL + FapFolderConstants.ENDPOINT_SIGNIN)
                    .post(form)
                    .header("x-requested-with", "XMLHttpRequest")
                    .header("origin", FapFolderConstants.HEADER_ORIGIN)
                    .header("referer", FapFolderConstants.HEADER_REFERER_SIGNIN)
                    .header("content-type", FapFolderConstants.CONTENT_TYPE_FORM)
                    .header("accept", "application/json, text/javascript, */*; q=0.01")
                    .build()

                FapFolderNetwork.sharedOkHttpClient.newCall(request).execute().use { resp ->
                    val code = resp.code
                    val body = resp.body.string().orEmpty()
                    val setCookies = resp.headers("Set-Cookie").toList()

                    Log.d(
                        TAG,
                        "login response: code=$code bodyLen=${body.length} cookies=${setCookies.size}"
                    )

                    if (FapFolderNetwork.isChallengeResponse(body)) {
                        emit(FapFolderAuthResult.CloudflareChallenge)
                        return@use
                    }

                    val hasCUser = setCookies.any { it.startsWith("c_user=", ignoreCase = true) }
                    val hasXs = setCookies.any { it.startsWith("xs=", ignoreCase = true) }

                    if (code in 200..399 && hasCUser && hasXs) {
                        FapFolderNetwork.persistCookies()
                        emit(FapFolderAuthResult.Success)
                        return@use
                    }

                    if (code == 401 || code == 403) {
                        emit(FapFolderAuthResult.InvalidCredentials)
                        return@use
                    }

                    if (body.contains("\"error\"", ignoreCase = true)) {
                        val errorMsg = extractErrorMessage(body)
                        emit(FapFolderAuthResult.FormError(errorMsg ?: "Incorrect credentials."))
                        return@use
                    }

                    if (body.contains("callback", ignoreCase = true)) {
                        FapFolderNetwork.persistCookies()
                        emit(FapFolderAuthResult.Success)
                        return@use
                    }

                    emit(FapFolderAuthResult.InvalidCredentials)
                }
            } catch (e: Exception) {
                Log.e(TAG, "login failed", e)
                emit(FapFolderAuthResult.NetworkError(e))
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun logout(): FapFolderAuthResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(FapFolderConstants.BASE_URL.trimEnd('/') + "/signout")
                .get()
                .build()
            runCatching {
                FapFolderNetwork.sharedOkHttpClient.newCall(request).execute().use {
                    Log.d(TAG, "logout response: ${it.code}")
                }
            }
            FapFolderAuthResult.Success
        } catch (_: Exception) {
            FapFolderAuthResult.NetworkError(IllegalStateException("Logout failed"))
        } finally {
            FapFolderNetwork.clearCookies()
            FapFolderSessionCache.clear()
        }
    }

    suspend fun saveRawCookies(raw: String?): FapFolderCookieJar.ValidationResult =
        withContext(Dispatchers.IO) {
            val jar = FapFolderNetwork.cookieJar
            val validation = jar.validateRawCookies(raw)
            if (validation.valid) {
                val parsed = jar.parseRawCookieString(raw)
                jar.seedFromSnapshot(parsed)
                FapFolderNetwork.persistCookies()
                FapFolderSessionCache.clear()
            }
            validation
        }

    private fun extractErrorMessage(body: String): String? {
        return Regex(""""message"\s*:\s*"([^"]+)"""").find(body)
            ?.groupValues?.getOrNull(1)
            ?: Regex("""class=["']alert-danger["'][^>]*>([^<]+)<""").find(body)
                ?.groupValues?.getOrNull(1)
                ?.trim()
    }
}
