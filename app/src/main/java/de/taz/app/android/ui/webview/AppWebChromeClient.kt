package de.taz.app.android.ui.webview

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import de.taz.app.android.util.Log

class AppWebChromeClient(
    private val onRenderedCallBack: (() -> Unit)? = null
): WebChromeClient() {
    private val log by Log
    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        if(newProgress == 100) {
            onRenderedCallBack?.let { it() }
        }
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        log.verbose("[${consoleMessage.messageLevel()}] ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}: ${consoleMessage.message()}")
        return true
    }
}