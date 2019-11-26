package de.taz.app.android.ui.webview

import android.view.MotionEvent
import kotlinx.coroutines.Job


interface AppWebViewCallback {

    fun onScrollStarted()

    fun onScrollFinished()

    fun onDoubleTap(e: MotionEvent): Boolean
}
