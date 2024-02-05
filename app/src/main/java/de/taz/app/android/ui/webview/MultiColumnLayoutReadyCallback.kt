package de.taz.app.android.ui.webview

/**
 * Callback to be informed when the WebView is done calculating the specs of the multi column layout
 * and has set all the sizes and classes on the DOM elements.
 */
interface MultiColumnLayoutReadyCallback {
    fun onMultiColumnLayoutReady()
}