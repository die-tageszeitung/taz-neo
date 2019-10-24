package de.taz.app.android.ui.webview

import android.view.GestureDetector
import android.view.MotionEvent
import de.taz.app.android.util.Log
import kotlin.math.abs


class WebViewGestureListener(
    private val appWebViewCallback: AppWebViewCallback
) :
    GestureDetector.SimpleOnGestureListener() {

    private val log by Log

    private val swipeThreshold = 100
    private val swipeVelocityThreshold = 100

    override fun onDoubleTap(e: MotionEvent): Boolean {
        return appWebViewCallback.onDoubleTap(e)
    }


    override fun onFling(
        e1: MotionEvent,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        log.debug("e1: $e1, e2: $e2, velocityX: $velocityX, velocityY: $velocityY")
        var result = false

        try {
            val diffY = e2.y - e1.y
            val diffX = e2.x - e1.x
            if (abs(diffX) > abs(diffY)) {
                if (abs(diffX) > swipeThreshold && abs(velocityX) > swipeVelocityThreshold) {
                    if (diffX > 0) {
                        appWebViewCallback.onSwipeRight()
                    } else {
                        appWebViewCallback.onSwipeLeft()
                    }
                }
                result = true
            } else if (abs(diffY) > swipeThreshold && abs(velocityY) > swipeVelocityThreshold) {
                if (diffY > 0) {
                    appWebViewCallback.onSwipeBottom()
                } else {
                    appWebViewCallback.onSwipeTop()
                }
                result = true
            }

        } catch (exception: Exception) {
            exception.printStackTrace()
        }

        return result
    }
}