package com.yenaly.han1meviewer.FapFolder.data.remote

import android.util.Log
import com.yenaly.han1meviewer.FapFolder.common.FapFolderConstants
import com.yenaly.han1meviewer.FapFolder.data.local.FapFolderSessionCache
import com.yenaly.han1meviewer.FapFolder.data.parser.FapFolderSessionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup

/**
 * Account pages of fapfolder.club: sign up, account settings and privacy settings.
 * There is intentionally no "forgot password" flow (it does not work on that site).
 */
object FapFolderAccountRepo {

    private const val TAG = "FapFolderAccountRepo"

    /** Fallback when the sign-up page cannot be parsed (value taken from /signup). */
    private const val FALLBACK_TURNSTILE_SITEKEY = "0x4AAAAAAAxBb109PHtwK69p"

    const val PATH_SIGNUP = "signup"
    const val PATH_SETTINGS = "settings"
    const val PATH_SETTINGS_PRIVACY = "settings/privacy"
    const val ENDPOINT_SIGNUP = "includes/ajax/core/signup.php"
    const val ENDPOINT_SETTINGS = "includes/ajax/users/settings.php"

    data class Viewer(val username: String?, val avatarUrl: String?, val loggedIn: Boolean)

    data class AccountSettings(val email: String, val username: String)

    data class PrivacySettings(
        val newsletter: Boolean = false,
        val gender: String = VISIBILITY_PUBLIC,
        val birthdate: String = VISIBILITY_PUBLIC,
        val basic: String = VISIBILITY_PUBLIC,
        val other: String = VISIBILITY_PUBLIC,
        val photos: String = VISIBILITY_PUBLIC,
        val groups: String = VISIBILITY_PUBLIC,
    )

    sealed interface ActionResult {
        data class Success(val message: String) : ActionResult
        data class Error(val message: String) : ActionResult
    }

    sealed interface RegisterResult {
        /** Account created and the site already signed us in. */
        data object LoggedIn : RegisterResult

        /** Account created but we did not receive a session (e.g. email activation). */
        data class Created(val message: String) : RegisterResult
        data class Error(val message: String) : RegisterResult
    }

    const val VISIBILITY_PUBLIC = "public"
    const val VISIBILITY_FRIENDS = "friends"
    const val VISIBILITY_ME = "me"

    private fun url(path: String) = FapFolderConstants.BASE_URL + path.trimStart('/')

    private fun getPage(path: String): Pair<Int, String> {
        val request = Request.Builder().url(url(path)).get().build()
        FapFolderNetwork.sharedOkHttpClient.newCall(request).execute().use { resp ->
            return resp.code to resp.body.string().orEmpty()
        }
    }

    private fun postAjax(
        endpoint: String,
        form: FormBody,
        referer: String,
    ): Pair<Int, String> {
        val request = Request.Builder()
            .url(url(endpoint))
            .post(form)
            .header("x-requested-with", FapFolderConstants.AJAX_REQUESTED_WITH)
            .header("origin", FapFolderConstants.HEADER_ORIGIN)
            .header("referer", referer)
            .header("content-type", FapFolderConstants.CONTENT_TYPE_FORM)
            .header("accept", FapFolderConstants.ACCEPT_AJAX)
            .build()
        FapFolderNetwork.sharedOkHttpClient.newCall(request).execute().use { resp ->
            return resp.code to resp.body.string().orEmpty()
        }
    }

    private fun interpretAction(code: Int, body: String): ActionResult {
        if (FapFolderNetwork.isChallengeResponse(body)) {
            return ActionResult.Error("Cloudflare challenge. Wait a moment and try again.")
        }
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: return ActionResult.Error(
                if (code in 200..299) "Unexpected response from FapFolder." else "HTTP $code"
            )
        return when {
            json.optBoolean("error", false) ->
                ActionResult.Error(stripHtml(json.optString("message")).ifBlank { "Request failed." })
            json.optBoolean("success", false) ->
                ActionResult.Success(stripHtml(json.optString("message")).ifBlank { "Saved." })
            json.has("callback") && !json.isNull("callback") -> {
                // The site answers with a JS callback (reload / redirect) on success.
                // A redirect to the sign-in page means the session is gone.
                val cb = json.optString("callback")
                if (cb.contains("signin", ignoreCase = true)) {
                    ActionResult.Error("Session expired. Sign in again.")
                } else {
                    ActionResult.Success("Saved.")
                }
            }
            else -> ActionResult.Error("Unexpected response from FapFolder.")
        }
    }

    private fun stripHtml(raw: String?): String =
        if (raw.isNullOrBlank()) "" else Jsoup.parse(raw).text().trim()

    /** Best-effort viewer lookup used by the account page. */
    suspend fun fetchViewer(): Viewer = withContext(Dispatchers.IO) {
        FapFolderSessionCache.current()?.takeIf { it.loggedIn && it.viewer.username != null }
            ?.let { return@withContext Viewer(it.viewer.username, it.viewer.avatarUrl, true) }
        if (!FapFolderNetwork.hasAuthCookies()) return@withContext Viewer(null, null, false)
        try {
            val (code, body) = getPage(PATH_SETTINGS)
            if (code !in 200..299 || body.isBlank()) return@withContext Viewer(null, null, false)
            val session = FapFolderSessionParser.parseAndCache(body, url(PATH_SETTINGS))
            val username = session.viewer.username
                ?: Jsoup.parse(body).selectFirst("input[name=username]")?.attr("value")
                    ?.takeIf { it.isNotBlank() }
            Viewer(username, session.viewer.avatarUrl, session.loggedIn || username != null)
        } catch (e: Exception) {
            Log.e(TAG, "fetchViewer failed", e)
            Viewer(null, null, FapFolderNetwork.hasAuthCookies())
        }
    }

    suspend fun fetchAccountSettings(): Result<AccountSettings> = withContext(Dispatchers.IO) {
        runCatching {
            val (code, body) = getPage(PATH_SETTINGS)
            if (FapFolderNetwork.isChallengeResponse(body)) error("Cloudflare challenge.")
            if (code !in 200..299) error("HTTP $code")
            val doc = Jsoup.parse(body, url(PATH_SETTINGS))
            val form = doc.selectFirst("form[data-url*=edit=account]")
                ?: error("Sign in to edit your account.")
            AccountSettings(
                email = form.selectFirst("input[name=email]")?.attr("value").orEmpty(),
                username = form.selectFirst("input[name=username]")?.attr("value").orEmpty(),
            )
        }
    }

    suspend fun saveAccountSettings(
        email: String,
        username: String,
        currentPassword: String,
    ): ActionResult = withContext(Dispatchers.IO) {
        try {
            val form = FormBody.Builder()
                .add("email", email.trim())
                .add("username", username.trim())
                .add("password", currentPassword)
                .build()
            val (code, body) = postAjax(
                "$ENDPOINT_SETTINGS?edit=account", form, url(PATH_SETTINGS),
            )
            interpretAction(code, body)
        } catch (e: Exception) {
            Log.e(TAG, "saveAccountSettings failed", e)
            ActionResult.Error(e.message ?: "Could not reach FapFolder.")
        }
    }

    suspend fun fetchPrivacy(): Result<PrivacySettings> = withContext(Dispatchers.IO) {
        runCatching {
            val (code, body) = getPage(PATH_SETTINGS_PRIVACY)
            if (FapFolderNetwork.isChallengeResponse(body)) error("Cloudflare challenge.")
            if (code !in 200..299) error("HTTP $code")
            val doc = Jsoup.parse(body, url(PATH_SETTINGS_PRIVACY))
            val form = doc.selectFirst("form[data-url*=edit=privacy]")
                ?: error("Sign in to edit your privacy settings.")

            fun sel(name: String): String =
                form.selectFirst("select[name=$name] option[selected]")?.attr("value")
                    ?: form.selectFirst("select[name=$name] option")?.attr("value")
                    ?: VISIBILITY_PUBLIC

            PrivacySettings(
                newsletter = form.selectFirst("input[name=user_privacy_newsletter]")
                    ?.hasAttr("checked") == true,
                gender = sel("user_privacy_gender"),
                birthdate = sel("user_privacy_birthdate"),
                basic = sel("user_privacy_basic"),
                other = sel("user_privacy_other"),
                photos = sel("user_privacy_photos"),
                groups = sel("user_privacy_groups"),
            )
        }
    }

    suspend fun savePrivacy(p: PrivacySettings): ActionResult = withContext(Dispatchers.IO) {
        try {
            val builder = FormBody.Builder()
            // A browser only submits a checked checkbox, so mirror that.
            if (p.newsletter) builder.add("user_privacy_newsletter", "on")
            val form = builder
                .add("user_privacy_gender", p.gender)
                .add("user_privacy_birthdate", p.birthdate)
                .add("user_privacy_basic", p.basic)
                .add("user_privacy_other", p.other)
                .add("user_privacy_photos", p.photos)
                .add("user_privacy_groups", p.groups)
                .build()
            val (code, body) = postAjax(
                "$ENDPOINT_SETTINGS?edit=privacy", form, url(PATH_SETTINGS_PRIVACY),
            )
            interpretAction(code, body)
        } catch (e: Exception) {
            Log.e(TAG, "savePrivacy failed", e)
            ActionResult.Error(e.message ?: "Could not reach FapFolder.")
        }
    }

    /** Cloudflare Turnstile site key of the sign-up form. */
    suspend fun fetchSignupSitekey(): String = withContext(Dispatchers.IO) {
        runCatching {
            val (code, body) = getPage(PATH_SIGNUP)
            if (code !in 200..299) return@runCatching null
            Jsoup.parse(body).selectFirst("[data-sitekey]")?.attr("data-sitekey")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: FALLBACK_TURNSTILE_SITEKEY
    }

    suspend fun register(
        username: String,
        email: String,
        email2: String,
        password: String,
        captchaToken: String,
    ): RegisterResult = withContext(Dispatchers.IO) {
        try {
            // Prime cookies / session like a browser visiting the sign-up page.
            runCatching { getPage(PATH_SIGNUP) }

            val form = FormBody.Builder()
                .add("username", username.trim())
                .add("email", email.trim())
                .add("email2", email2.trim())
                .add("password", password)
                .add("field1", "") // honeypot, must stay empty
                .add("privacy_agree", "on")
                .add("cf-turnstile-response", captchaToken)
                .add("g-recaptcha-response", captchaToken)
                .build()
            val (code, body) = postAjax(
                ENDPOINT_SIGNUP, form, url(PATH_SIGNUP),
            )
            Log.d(TAG, "register: code=$code bodyLen=${body.length}")

            if (FapFolderNetwork.isChallengeResponse(body)) {
                return@withContext RegisterResult.Error(
                    "Cloudflare challenge. Wait a moment and try again."
                )
            }
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: return@withContext RegisterResult.Error(
                    if (code in 200..299) "Unexpected response from FapFolder." else "HTTP $code"
                )
            if (json.optBoolean("error", false)) {
                return@withContext RegisterResult.Error(
                    stripHtml(json.optString("message")).ifBlank { "Sign up failed." }
                )
            }
            if (FapFolderNetwork.hasAuthCookies()) {
                FapFolderNetwork.persistCookies()
                FapFolderSessionCache.clear()
                RegisterResult.LoggedIn
            } else {
                RegisterResult.Created(
                    stripHtml(json.optString("message")).ifBlank {
                        "Account created. If the site asks you to activate it by email, " +
                                "do that first, then sign in."
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "register failed", e)
            RegisterResult.Error(e.message ?: "Could not reach FapFolder.")
        }
    }
}
