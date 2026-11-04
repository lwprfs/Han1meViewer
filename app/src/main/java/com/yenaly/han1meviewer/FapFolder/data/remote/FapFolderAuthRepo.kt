package com.yenaly.han1meviewer.FapFolder.data.remote

import android.util.Log
import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.data.local.FapFolderCookieManager
import com.yenaly.han1meviewer.FapFolder.data.local.FapFolderSessionCache
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderSessionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.HttpUrl
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

    private const val PROBE_CONFIRM_ATTEMPTS = 2

    private class ProbeFetch(val code: Int, val body: String)

    private fun looksSignedOut(result: FapFolderProbeResult): Boolean {
        val code = result.httpCode ?: return false
        return result.failure == FapFolderProbeFailure.SessionExpired && code in 200..299
    }

    /**
     * True only when [setCookies] contains a usable [name] cookie: non-blank, not "deleted",
     * and not already expired. PHP-style sites answer a failed login with
     * `c_user=deleted; expires=<past>`, which must not count as a session.
     */
    private fun isRealCookie(setCookies: List<String>, name: String, url: HttpUrl): Boolean {
        val now = System.currentTimeMillis()
        return setCookies.any { header ->
            val c = Cookie.parse(url, header) ?: return@any false
            c.name.equals(name, ignoreCase = true) &&
                    c.value.isNotBlank() &&
                    !c.value.equals("deleted", ignoreCase = true) &&
                    c.expiresAt > now
        }
    }

    private fun fetchHomeForProbe(url: String): ProbeFetch {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        FapFolderNetwork.sharedOkHttpClient.newCall(request).execute().use { resp ->
            val body = resp.body.string().orEmpty()
            Log.d(TAG, "probe /: code=${resp.code} bodyLen=${body.length}")
            return ProbeFetch(resp.code, body)
        }
    }

    private fun evaluateProbe(fetch: ProbeFetch, url: String): FapFolderProbeResult {
        val code = fetch.code
        val body = fetch.body

        if (FapFolderNetwork.isChallengeResponse(body)) {
            return FapFolderProbeResult(
                ok = false,
                failure = FapFolderProbeFailure.CloudflareChallenge,
                userMessage = "Cloudflare challenge. Wait a moment and try again.",
                httpCode = code,
                suggestWait = true,
            )
        }

        return when {
            code == 429 -> FapFolderProbeResult(
                ok = false,
                failure = FapFolderProbeFailure.RateLimited,
                userMessage = "FapFolder is rate limiting (HTTP 429). Wait a minute.",
                httpCode = code,
                suggestWait = true,
            )

            code == 403 -> FapFolderProbeResult(
                ok = false,
                failure = FapFolderProbeFailure.CloudflareChallenge,
                userMessage = "Request blocked (HTTP 403). Wait a moment and try again.",
                httpCode = code,
                suggestWait = true,
            )

            code == 401 -> {
                FapFolderSessionCache.clear()
                FapFolderProbeResult(
                    ok = false,
                    failure = FapFolderProbeFailure.SessionExpired,
                    userMessage = "Session rejected (HTTP 401). Sign in again.",
                    httpCode = code,
                    suggestRelogin = true,
                )
            }

            code in 500..599 -> FapFolderProbeResult(
                ok = false,
                failure = FapFolderProbeFailure.ServerError,
                userMessage = "FapFolder server error (HTTP $code). Try later.",
                httpCode = code,
                suggestWait = true,
            )

            code == 404 -> FapFolderProbeResult(
                ok = false,
                failure = FapFolderProbeFailure.SiteDown,
                userMessage = "FapFolder returned 404. Site may be down.",
                httpCode = code,
                suggestSwitchCookies = true,
            )

            code in 200..299 -> {
                val session = FapFolderSessionParser.parse(body, url)
                if (session.loggedIn) {
                    FapFolderSessionCache.update(session)
                    FapFolderProbeResult(
                        ok = true,
                        httpCode = code,
                        viewerUsername = session.viewer.username,
                        viewerAvatarUrl = session.viewer.avatarUrl,
                        viewerUserId = session.viewer.userId,
                    )
                } else {
                    FapFolderSessionCache.clear()
                    FapFolderProbeResult(
                        ok = false,
                        failure = FapFolderProbeFailure.SessionExpired,
                        userMessage = "Cookies are not signed in on this site. Sign in again.",
                        httpCode = code,
                        suggestRelogin = true,
                    )
                }
            }

            else -> FapFolderProbeResult(
                ok = false,
                failure = FapFolderProbeFailure.Unknown,
                userMessage = "Unexpected response (HTTP $code).",
                httpCode = code,
            )
        }
    }

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
            var attempt = 1
            var result = evaluateProbe(fetchHomeForProbe(url), url)

            while (attempt < PROBE_CONFIRM_ATTEMPTS && looksSignedOut(result)) {
                delay(FapFolderConstants.SESSION_RECHECK_DELAY_MS)
                result = evaluateProbe(fetchHomeForProbe(url), url)
                attempt++
            }
            emit(result)
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

                // Never send a stale/dead session with the sign-in request. Cloudflare cookies stay.
                FapFolderNetwork.clearAuthCookies()

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

                    // Stale cookies were cleared above, so anything in the jar now is fresh
                    // (this also covers cookies set on an intermediate redirect hop).
                    val hasCUser = isRealCookie(setCookies, "c_user", resp.request.url)
                    val hasXs = isRealCookie(setCookies, "xs", resp.request.url)
                    val jarHasAuth = FapFolderCookieJar.hasRequiredAuthCookie()

                    if (code in 200..399 && ((hasCUser && hasXs) || jarHasAuth)) {
                        FapFolderNetwork.persistCookies()
                        emit(FapFolderAuthResult.Success)
                        return@use
                    }

                    if (code == 403) {
                        // Same as probe(): a 403 is a Cloudflare/WAF block, not wrong credentials.
                        emit(FapFolderAuthResult.CloudflareChallenge)
                        return@use
                    }

                    if (code == 401) {
                        emit(FapFolderAuthResult.InvalidCredentials)
                        return@use
                    }

                    if (body.contains("\"error\"", ignoreCase = true)) {
                        val errorMsg = extractErrorMessage(body)
                        emit(FapFolderAuthResult.FormError(errorMsg ?: "Incorrect credentials."))
                        return@use
                    }

                    if (body.contains("callback", ignoreCase = true) && jarHasAuth) {
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
            FapFolderCredentialStore.clear()
            FapFolderSessionCache.clear()
        }
    }

    suspend fun saveRawCookies(raw: String?): FapFolderCookieJar.ValidationResult =
        withContext(Dispatchers.IO) {
            val input = raw.orEmpty()
            val validation = FapFolderCookieManager.validate(input)
            if (validation.valid) {
                // Same parser as validate(): JSON, Netscape and raw `a=b; c=d` all work.
                FapFolderCookieManager.saveCookies(input)
                FapFolderSessionCache.clear()
            }
            FapFolderCookieJar.ValidationResult(
                valid = validation.valid,
                cookieCount = validation.cookieCount,
                message = validation.message,
            )
        }

    private fun extractErrorMessage(body: String): String? {
        return Regex(""""message"\s*:\s*"([^"]+)"""").find(body)
            ?.groupValues?.getOrNull(1)
            ?: Regex("""class=["']alert-danger["'][^>]*>([^<]+)<""").find(body)
                ?.groupValues?.getOrNull(1)
                ?.trim()
    }
}
