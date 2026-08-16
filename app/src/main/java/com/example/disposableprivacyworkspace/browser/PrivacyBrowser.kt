package com.example.disposableprivacyworkspace.browser

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.util.concurrent.Executor

/**
 * Session-only browser. Tor mode uses AndroidX WebView's supported process proxy override
 * to send browser requests to the embedded Tor SOCKS listener. No direct fallback is added.
 */
class PrivacyBrowser(private val executor: Executor) {
    fun configureTorProxy(port: Int, onApplied: (Boolean) -> Unit) = configureProxy("socks://127.0.0.1:$port", onApplied)

    fun configureHttpProxy(port: Int, onApplied: (Boolean) -> Unit) = configureProxy("http://127.0.0.1:$port", onApplied)

    private fun configureProxy(proxyUrl: String, onApplied: (Boolean) -> Unit) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            onApplied(false)
            return
        }
        runCatching {
            val config = ProxyConfig.Builder()
                .removeImplicitRules()
                .addProxyRule(proxyUrl, ProxyConfig.MATCH_ALL_SCHEMES)
                .build()
            ProxyController.getInstance().setProxyOverride(config, executor) { onApplied(true) }
        }.onFailure { onApplied(false) }
    }

    fun clearProxy(onCleared: () -> Unit = {}) {
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                ProxyController.getInstance().clearProxyOverride(executor, onCleared)
            } else onCleared()
        }.onFailure { onCleared() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView) {
        CookieManager.getInstance().setAcceptCookie(true)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.setSupportMultipleWindows(false)
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
        }
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    fun clearSessionCookies() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
}
