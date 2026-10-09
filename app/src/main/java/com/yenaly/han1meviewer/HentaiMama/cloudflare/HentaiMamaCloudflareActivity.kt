package com.yenaly.han1meviewer.HentaiMama.cloudflare

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
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
        const val EXTRA_URL = "request_url"
        var onFinished: (() -> Unit)? = null
    }

    private val progressState = mutableIntStateOf(0)
    private val tipTextState = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            finish()
            return
        }

        tipTextState.value =
            getString(R.string.complete_cloudflare_verification_with_warning)

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
                                    html.contains("Just a moment")
                                if (!hasChallenge) {
                                    val cookies = cookieMgr.getCookie(url) ?: ""
                                    persistCookieIfPresent(url, cookies, cookieMgr)
                                }
                            }
                        }, 1000)
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

    private fun persistCookieIfPresent(
        url: String?,
        cookies: String,
        cookieMgr: CookieManager,
    ) {
        if (!cookies.contains("cf_clearance")) return
        val host = url?.toHttpUrlOrNull()?.host ?: return
        HentaiMamaCloudflareCookieManager.saveCloudflareCookie(host, cookies)
        cookieMgr.flush()
        finish()
        onFinished?.invoke()
        onFinished = null
    }

    override fun onDestroy() {
        super.onDestroy()
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
