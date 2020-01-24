package de.taz.app.android.ui.webview

import android.view.MotionEvent
import kotlinx.coroutines.Job


interface AppWebViewCallback {

    fun onScrollStarted() = Unit

    fun onScrollFinished() = Unit

    fun onDoubleTap(e: MotionEvent): Boolean = false
}
