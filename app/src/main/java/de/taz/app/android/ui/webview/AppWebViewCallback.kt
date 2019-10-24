package de.taz.app.android.ui.webview

import android.view.MotionEvent
import kotlinx.coroutines.Job


interface AppWebViewCallback {

    fun onScrollStarted()

    fun onScrollFinished()

    fun onSwipeRight(): Job?

    fun onSwipeLeft(): Job?

    fun onSwipeBottom()

    fun onSwipeTop()

    fun onDoubleTap(e: MotionEvent): Boolean
}
