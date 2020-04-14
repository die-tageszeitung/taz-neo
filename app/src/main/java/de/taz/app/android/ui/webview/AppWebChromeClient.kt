package de.taz.app.android.ui.webview

import android.webkit.WebChromeClient
import android.webkit.WebView

class AppWebChromeClient(
    private val onRenderedCallBack: (() -> Unit)? = null
): WebChromeClient() {
    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        if(newProgress == 100) {
            onRenderedCallBack?.let { it() }
        }
    }
}