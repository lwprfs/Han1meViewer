package com.yenaly.han1meviewer.HentaiMama.cloudflare

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.preference.PreferenceManager
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.USER_AGENT
import com.yenaly.han1meviewer.ui.screen.web.CloudflareScreen
import com.yenaly.han1meviewer.ui.theme.HanimeTheme
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

class HentaiMamaCloudflareActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "HMCloudflareActivity"
        const val EXTRA_URL = "request_url"
        private const val MIN_DWELL_MS = 4_000L
        var onFinished: (() -> Unit)? = null
    }

    private val progressState = mutableIntStateOf(0)
    private val tipTextState = mutableStateOf("")

    private val activityStartMs = System.currentTimeMillis()
    private var persistedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            finish()
            return
        }

        tipTextState.value = getString(R.string.complete_cloudflare_verification_with_warning)

        val composeView = ComposeView(this)
        setContentView(composeView)
        composeView.setContent {
            HanimeTheme {
                CloudflareScreen(
                    progress = progressState.intValue,
                    tipText = tipTextState.value,
                    onClose = { finish() },
                    webViewFactory = { createWebView(url) },
                )
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(url: String): WebView {
        return WebView(this).apply {
            val wv = this
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                javaScriptCanOpenWindowsAutomatically = true
                userAgentString = USER_AGENT
                cacheMode = WebSettings.LOAD_NO_CACHE
            }

            val cookieMgr = CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(wv, true)
            }

            clearStaleCfClearance(url, cookieMgr)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean = false

                override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                    super.onPageFinished(view, loadedUrl)
                    view?.evaluateJavascript(
                        "document.querySelector('#challenge-form, #challenge-success-text, #challenge-error-text')"
                    ) { result ->
                        if (result == "null") {
                            val cookies = cookieMgr.getCookie(loadedUrl) ?: ""
                            persistCookieIfPresent(loadedUrl, cookies, cookieMgr)
                        }
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressState.intValue = newProgress
                    if (newProgress >= 90) {
                        view?.postDelayed({
                            view.evaluateJavascript(
                                "document.documentElement.outerHTML"
                            ) { html ->
                                val hasChallenge =
                                    html.contains("#challenge-form") ||
                                            html.contains("cf-challenge") ||
                                            html.contains("Just a moment") ||
                                            html.contains("challenge-platform")
                                if (!hasChallenge) {
                                    val cookies = cookieMgr.getCookie(url) ?: ""
                                    persistCookieIfPresent(url, cookies, cookieMgr)
                                }
                            }
                        }, 1500)
                    }
                }
            }

            val finalUrl = if (url.contains("?")) {
                "$url&_=${System.currentTimeMillis()}"
            } else {
                "$url?_=${System.currentTimeMillis()}"
            }
            loadUrl(finalUrl)
        }
    }

    private fun clearStaleCfClearance(url: String, cookieMgr: CookieManager) {
        val host = url.toHttpUrlOrNull()?.host ?: return
        val existing = cookieMgr.getCookie(host).orEmpty()
        if (!existing.contains("cf_clearance")) return

        Log.d(TAG, "Clearing stale cf_clearance from WebView for $host")
        cookieMgr.setCookie(host, "cf_clearance=; Max-Age=0; Path=/")
        cookieMgr.flush()
    }

    private fun persistCookieIfPresent(
        url: String?,
        cookies: String,
        cookieMgr: CookieManager,
    ) {
        if (persistedOnce) return
        if (!cookies.contains("cf_clearance")) return

        val elapsed = System.currentTimeMillis() - activityStartMs
        if (elapsed < MIN_DWELL_MS) {
            Log.d(TAG, "Ignoring early solution (${elapsed}ms < ${MIN_DWELL_MS}ms)")
            return
        }

        val host = url?.toHttpUrlOrNull()?.host ?: return
        val fresh = Regex("cf_clearance=([^;]+)").find(cookies)?.groupValues?.get(1)
        if (fresh.isNullOrBlank()) return

        val existing = HentaiMamaCloudflareCookieManager.getCloudflareCookie(host)
        if (existing != null && existing == fresh) {
            Log.d(TAG, "cf_clearance unchanged; waiting for a fresh value")
            return
        }

        persistedOnce = true
        Log.d(TAG, "Persisting new cf_clearance for $host")
        HentaiMamaCloudflareCookieManager.saveCloudflareCookie(host, cookies)
        cookieMgr.flush()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Invoking onFinished callback.")
        onFinished?.invoke()
        onFinished = null
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyAppLocale(newBase))
    }

    private fun applyAppLocale(context: Context): Context {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val lang = prefs.getString("app_language", "system") ?: "system"
        val newLocale = when (lang) {
            "zh-rCN" -> Locale.SIMPLIFIED_CHINESE
            "zh" -> Locale.TRADITIONAL_CHINESE
            "en" -> Locale.ENGLISH
            "ja" -> Locale.JAPANESE
            else -> Resources.getSystem().configuration.locales.get(0)
        }
        Locale.setDefault(newLocale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(newLocale)
        return context.createConfigurationContext(config)
    }
}
