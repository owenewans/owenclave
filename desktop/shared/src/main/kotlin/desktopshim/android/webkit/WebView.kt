package android.webkit

import android.content.Context

class WebSettings {
    var javaScriptEnabled: Boolean = false
}

class WebResourceRequest
class WebResourceError

open class WebViewClient {
    open fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {}
    open fun onPageFinished(view: WebView, url: String) {}
}

class WebView(val context: Context) {
    val settings: WebSettings = WebSettings()
    var webViewClient: WebViewClient? = null
    val title: String = ""

    fun loadUrl(url: String) {}
    fun destroy() {}
}

