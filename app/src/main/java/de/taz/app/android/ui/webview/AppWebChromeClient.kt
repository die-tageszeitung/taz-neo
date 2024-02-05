package de.taz.app.android.ui.webview

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import de.taz.app.android.util.Log

class AppWebChromeClient(
    private val onRenderedCallBack: (() -> Unit)? = null
): WebChromeClient() {
    private val log by Log

    private var prevUrl: String? = null
    private var prevProgress: Int = 0

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        // Ensure that the onRenderedCallBack is only triggered once.
        // There seems to be some weird behavior on how often this onProgressChanged is called:
        // see: https://stackoverflow.com/a/32705123/2347168
        if (newProgress == 100 && newProgress != prevProgress && prevUrl != view.url) {
            prevProgress = newProgress
            prevUrl = view.url
            onRenderedCallBack?.let { it() }
        }
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        log.verbose("[${consoleMessage.messageLevel()}] ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}: ${consoleMessage.message()}")
        return true
    }
}