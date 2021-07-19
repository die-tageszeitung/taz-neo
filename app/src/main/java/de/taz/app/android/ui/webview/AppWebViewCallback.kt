package de.taz.app.android.ui.webview

import android.view.MotionEvent


interface AppWebViewCallback {

    fun onScrollStarted() = Unit

    fun onScrollFinished() = Unit

    fun onDoubleTap(e: MotionEvent): Boolean = false
}
