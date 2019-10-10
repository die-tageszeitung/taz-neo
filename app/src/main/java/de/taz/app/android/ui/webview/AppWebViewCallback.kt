package de.taz.app.android.ui.webview

import android.view.MotionEvent


interface AppWebViewCallback {

    fun onScrollStarted()

    fun onScrollFinished()

    fun onSwipeRight(e1: MotionEvent, e2: MotionEvent)

    fun onSwipeLeft(e1: MotionEvent, e2: MotionEvent)

    fun onSwipeBottom(e1: MotionEvent, e2: MotionEvent)

    fun onSwipeTop(e1: MotionEvent, e2: MotionEvent)

    fun onDoubleTap(e: MotionEvent): Boolean
}
