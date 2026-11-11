package com.yenaly.han1meviewer.XHamster.data.remote

import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.XHamster.common.XHamsterConstants
import com.yenaly.han1meviewer.XHamster.data.model.XHamsterUser
import com.yenaly.han1meviewer.XHamster.data.parser.XHamsterParser
import com.yenaly.yenaly_libs.utils.applicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object XHamsterAuth {

    private const val KEY_USER = "xhamster_user_cache"

    private val sp get() = Preferences.preferenceSp

    val isLoggedIn: Boolean
        get() = cachedUser() != null && XHamsterNetwork.hasAuthCookies()

    fun cachedUser(): XHamsterUser? {
        val raw = runCatching { sp.getString(KEY_USER, null) }.getOrNull() ?: return null
        val parts = raw.split('\n')
        if (parts.size < 3) return null
        val id = parts[0].toLongOrNull() ?: return null
        return XHamsterUser(id = id, name = parts[1], profileUrl = parts[2])
    }

    private fun cache(user: XHamsterUser?) {
        sp.edit().apply {
            if (user == null) remove(KEY_USER)
            else putString(KEY_USER, "${user.id}\n${user.name}\n${user.profileUrl}")
        }.apply()
    }

    private fun message(resId: Int): String = applicationContext.getString(resId)

    suspend fun login(username: String, password: String): Result<XHamsterUser> =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = XHamsterNetwork.sharedOkHttpClient
                val base = XHamsterConstants.BASE_URL
                val loginPage = "$base/login?source=menuSignup"

                runCatching {
                    client.newCall(Request.Builder().url(loginPage).build()).execute().close()
                }

                val payload = buildJsonObject {
                    put("username", username)
                    put("password", password)
                    put("remember", 1)
                    put("redirectURL", "$base/")
                    put("pageType", JsonNull)
                    put("source", "menuSignup")
                    put("isSubscribedToUpdates", JsonNull)
                }.toString()

                val request = Request.Builder()
                    .url("$base/api/front/user/login")
                    .header("Accept", "*/*")
                    .header("Origin", XHamsterConstants.ORIGIN)
                    .header("Referer", loginPage)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("x-csrf-token", "1")
                    .post(payload.toRequestBody("text/plain;charset=UTF-8".toMediaType()))
                    .build()

                val body = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    response.body.string()
                }

                val extras = (Json.parseToJsonElement(body) as? JsonObject)?.get("extras") as? JsonObject
                    ?: throw IOException(message(R.string.xhamster_login_failed))
                val ok = (extras["result"] as? JsonPrimitive)?.booleanOrNull == true
                if (!ok) {
                    val error = extras["error"] as? JsonObject
                    val text = error?.values?.firstNotNullOfOrNull { value ->
                        (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                            ?.takeIf { it.isNotBlank() }
                    }
                    throw IOException(text ?: message(R.string.xhamster_login_failed))
                }

                val user = fetchCurrentUser().getOrNull()
                    ?: throw IOException(message(R.string.xhamster_login_session_failed))
                cache(user)
                user
            }
        }

    suspend fun fetchCurrentUser(): Result<XHamsterUser?> = withContext(Dispatchers.IO) {
        runCatching {
            val client = XHamsterNetwork.sharedOkHttpClient
            val html = client.newCall(Request.Builder().url("${XHamsterConstants.BASE_URL}/").build())
                .execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    response.body.string()
                }
            XHamsterParser.parseUser(html)
        }
    }

    suspend fun refresh(): XHamsterUser? {
        if (cachedUser() == null) return null
        val result = fetchCurrentUser()
        val user = result.getOrNull()
        if (result.isSuccess && user == null) {
            XHamsterNetwork.clearLoginCookies()
            cache(null)
            return null
        }
        if (user != null) cache(user)
        return cachedUser()
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${XHamsterConstants.BASE_URL}/logout?yes")
                .build()
            XHamsterNetwork.sharedOkHttpClient.newCall(request).execute().close()
        }
        XHamsterNetwork.clearLoginCookies()
        cache(null)
    }
}
