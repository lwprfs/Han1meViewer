package com.yenaly.han1meviewer.OnAir.data.remote

import android.util.Log
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetwork.baseUrl
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetwork.service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import retrofit2.Response

object OnAirAuthRepo {

    private const val TAG = "OnAirAuthRepo"

    data class CaptchaChallenge(
        val csrfToken: String,
        val honeypotName: String,
        val captchaImageUrl: String,
        val extraFields: Map<String, String> = emptyMap(),
    )

    sealed interface AuthResult {
        data object Success : AuthResult
        data class FormError(val message: String) : AuthResult
        data class NetworkError(val throwable: Throwable) : AuthResult
        data object LoginRequired : AuthResult
    }

    fun fetchLoginCaptcha() = flow {
        try {
            val response = service.getRaw(baseUrl.trimEnd('/') + "/login")
            val html = response.checkedHtml()
            emit(parseCaptchaChallenge(html, "login"))
        } catch (e: Exception) {
            Log.e(TAG, "fetchLoginCaptcha failed", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    fun fetchRegisterCaptcha() = flow {
        try {
            val response = service.getRaw(baseUrl.trimEnd('/') + "/register")
            val html = response.checkedHtml()
            emit(parseCaptchaChallenge(html, "register"))
        } catch (e: Exception) {
            Log.e(TAG, "fetchRegisterCaptcha failed", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    fun login(
        challenge: CaptchaChallenge,
        username: String,
        password: String,
        captchaAnswer: String,
    ) = flow {
        try {
            val body = FormBody.Builder()
                .add("csrf_token", challenge.csrfToken)
                .apply { if (challenge.honeypotName.isNotBlank()) add(challenge.honeypotName, "") }
                .add("username", username)
                .add("password", password)
                .add("captcha_answer", captchaAnswer)
                .build()

            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/login")
                .post(body)
                .build()

            OnAirNetwork.sharedOkHttpClient.newCall(request).execute().use { res ->
                val code = res.code
                val location = res.header("Location").orEmpty()
                val finalHtml = res.body?.string().orEmpty()

                Log.d(
                    TAG,
                    "login response: code=$code, location=$location, htmlLen=${finalHtml.length}",
                )

                val looksLikeLoginPage = isLoginForm(finalHtml)
                val isAuthenticatedPage = hasAuthenticatedMarker(finalHtml)

                if (code in 200..399 && isAuthenticatedPage) {
                    saveJarSnapshot()
                    emit(AuthResult.Success)
                    return@use
                }

                if (code in 200..399 && !looksLikeLoginPage) {
                    saveJarSnapshot()
                    emit(AuthResult.Success)
                    return@use
                }

                if (code == 401 || code == 403) {
                    emit(AuthResult.LoginRequired)
                    return@use
                }

                val error = parseFormError(finalHtml)
                emit(
                    AuthResult.FormError(
                        error ?: buildFallback("Login failed", code, location)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "login failed", e)
            emit(AuthResult.NetworkError(e))
        }
    }.flowOn(Dispatchers.IO)

    fun register(
        challenge: CaptchaChallenge,
        username: String,
        password: String,
        confirm: String,
        captchaAnswer: String,
        refCode: String = "",
    ) = flow {
        try {
            val body = FormBuilder()
                .add("csrf_token", challenge.csrfToken)
                .add("ref_code", refCode)
                .apply { if (challenge.honeypotName.isNotBlank()) add(challenge.honeypotName, "") }
                .add("username", username)
                .add("password", password)
                .add("confirm", confirm)
                .add("captcha_answer", captchaAnswer)
                .build()

            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/register")
                .post(body)
                .build()

            OnAirNetwork.sharedOkHttpClient.newCall(request).execute().use { res ->
                val code = res.code
                val location = res.header("Location").orEmpty()
                val finalHtml = res.body?.string().orEmpty()

                Log.d(
                    TAG,
                    "register response: code=$code, location=$location, htmlLen=${finalHtml.length}",
                )

                val looksLikeRegisterPage = isRegisterForm(finalHtml)
                val isAuthenticatedPage = hasAuthenticatedMarker(finalHtml)

                if (code in 200..399 && isAuthenticatedPage) {
                    saveJarSnapshot()
                    emit(AuthResult.Success)
                    return@use
                }

                if (code in 200..399 && !looksLikeRegisterPage) {
                    saveJarSnapshot()
                    emit(AuthResult.Success)
                    return@use
                }

                if (code == 401 || code == 403) {
                    emit(AuthResult.LoginRequired)
                    return@use
                }

                val error = parseFormError(finalHtml)
                emit(
                    AuthResult.FormError(
                        error ?: buildFallback("Registration failed", code, location)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "register failed", e)
            emit(AuthResult.NetworkError(e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun performLogout(): AuthResult {
        OnAirCookieManager.clearCookies()
        return AuthResult.Success
    }

    fun currentUsernameHint(): String? {
        val raw = OnAirCookieManager.cookiesRaw
        if (raw.isBlank()) return null
        val match = Regex("""(?:^|;\s*)remember_web_[^=]+=([^;]+)""").find(raw)
        if (match != null) {
            val decoded = runCatching {
                java.net.URLDecoder.decode(match.groupValues[1], "UTF-8")
            }.getOrNull()
            val m2 = decoded?.let { Regex(""""username":"([^"]+)"""").find(it) }
            if (m2 != null) return m2.groupValues[1]
        }
        return null
    }

    fun captchaImageUrl(): String = baseUrl.trimEnd('/') + "/captcha.png"

    private class FormBuilder {
        private val inner = FormBody.Builder()
        fun add(name: String, value: String) = apply { inner.add(name, value) }
        fun build(): FormBody = inner.build()
    }

    private fun Response<ResponseBody>.checkedHtml(): String {
        if (!isSuccessful) throw IllegalStateException("HTTP ${code()}: ${message()}")
        return body()?.string().orEmpty()
    }

    private fun saveJarSnapshot() {
        OnAirCookieManager.replaceWithJarSnapshot()
        Log.d(
            TAG,
            "saveJarSnapshot: names=${
                OnAirCookieManager.parseToCookies(
                    OnAirCookieManager.cookiesRaw, baseUrl
                ).map { it.name }
            }",
        )
    }

    private fun parseCaptchaChallenge(html: String, kind: String): CaptchaChallenge {
        val doc = Jsoup.parse(html, baseUrl)
        val csrfToken = doc.selectFirst("form.stack-form input[name=csrf_token]")
            ?.attr("value")
            ?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("input[name=csrf_token]")?.attr("value")
                ?.takeIf { it.isNotBlank() }
            ?: error("Missing csrf_token on /$kind")

        val honeypot = doc.selectFirst("input[name^=hp_]")?.attr("name")
            ?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("input[name=company], input[name=website], input[name=url]")
                ?.attr("name")
                ?.takeIf { it.isNotBlank() }
            ?: ""

        val refCode = doc.selectFirst("input[name=ref_code]")?.attr("value").orEmpty()
        val extra = if (kind == "register") mapOf("ref_code" to refCode) else emptyMap()
        return CaptchaChallenge(
            csrfToken = csrfToken,
            honeypotName = honeypot,
            captchaImageUrl = baseUrl.trimEnd('/') + "/captcha.png",
            extraFields = extra,
        )
    }

    private fun isLoginForm(html: String): Boolean {
        if (html.isBlank()) return false
        val hasPassword = html.contains("name=\"password\"", ignoreCase = true)
        val hasCsrf = html.contains("name=\"csrf_token\"", ignoreCase = true)
        if (!hasPassword || !hasCsrf) return false
        val titleLogin = html.contains(">Log in<", ignoreCase = true) ||
                html.contains(">Login<", ignoreCase = true)
        val actionLogin = html.contains("action=\"/login\"", ignoreCase = true)
        return titleLogin || actionLogin
    }

    private fun isRegisterForm(html: String): Boolean {
        if (html.isBlank()) return false
        val hasConfirm = html.contains("name=\"confirm\"", ignoreCase = true)
        val hasCsrf = html.contains("name=\"csrf_token\"", ignoreCase = true)
        if (!hasConfirm || !hasCsrf) return false
        val titleSignup = html.contains(">Sign up<", ignoreCase = true) ||
                html.contains(">Register<", ignoreCase = true)
        val actionRegister = html.contains("action=\"/register\"", ignoreCase = true)
        return titleSignup || actionRegister
    }

    private fun hasAuthenticatedMarker(html: String): Boolean {
        if (html.isBlank()) return false
        return html.contains("summary class=\"nav-username\"", ignoreCase = true) ||
                html.contains("class=\"nav-username\"", ignoreCase = true) ||
                html.contains("action=\"/logout\"", ignoreCase = true)
    }

    private fun buildFallback(prefix: String, code: Int, location: String): String {
        val suffix = if (location.isNotBlank()) " → $location" else ""
        return "$prefix (HTTP $code$suffix)"
    }

    private fun parseFormError(html: String): String? {
        if (html.isBlank()) return null
        val doc = Jsoup.parse(html, baseUrl)
        val selectors = listOf(
            ".form-error",
            "p.form-error",
            ".alert-danger",
            ".invalid-feedback",
            ".text-danger",
            ".help-block",
            ".error",
            ".error-message",
            ".msg-error",
            ".field-error",
            ".flash-error",
            "#error",
            "#form-error",
        )
        for (sel in selectors) {
            doc.select(sel).map { it.text().trim() }
                .firstOrNull { it.isNotBlank() }
                ?.let { return it }
        }
        val attrCandidates = doc.select("[data-error], [data-error-message]")
        for (el in attrCandidates) {
            val txt = el.attr("data-error").ifBlank { el.attr("data-error-message") }.trim()
            if (txt.isNotBlank()) return txt
        }
        return null
    }
}
