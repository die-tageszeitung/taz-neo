package de.taz.app.android.ui.pdfViewer

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import de.taz.app.android.ui.pdfViewer.mupdf.ReaderView
import de.taz.app.android.util.Log
import kotlin.math.abs

enum class ViewBorder {
    LEFT,
    RIGHT,
    BOTH,
    NONE
}

class MuPDFReaderView constructor(context: Context) : ReaderView(context) {

    var clickCoordinatesListener: ((Pair<Float, Float>) -> Unit)? = null
    var onBorderListener: ((ViewBorder) -> Unit)? = null
    var onScaleListener: ((Boolean) -> (Unit))? = null
    var onSwipeListener: ((SwipeEvent) -> Unit)? = null

    var forceFlingToSwipe = false

    private var currentBorder = ViewBorder.NONE

    val log by Log

    companion object {
        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        onScaleListener?.invoke(true)
        return super.onScaleBegin(detector)
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        onScaleListener?.invoke(false)
        checkWhichBordersAreVisible()
        onBorderListener?.invoke(currentBorder)
        super.onScaleEnd(detector)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        checkWhichBordersAreVisible()
        onBorderListener?.invoke(currentBorder)
        return super.onInterceptTouchEvent(ev)
    }

    /**
    * Override ReaderView fling handling.
    * This will prevent any fling scrolling animation from ReaderView to show.
    * If we are flinging in the direction of a visible border we are triggering a swipe event.
    */
    override fun onFling(
        e1: MotionEvent,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        var result = false
        val diffY = e2.y - e1.y
        val diffX = e2.x - e1.x
        if (abs(diffX) > abs(diffY)) {
            if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (diffX > 0) {
                    if (currentBorder == ViewBorder.LEFT || forceFlingToSwipe) {
                        onSwipeListener?.invoke(SwipeEvent.RIGHT)
                    }
                } else {
                    if (currentBorder == ViewBorder.RIGHT || forceFlingToSwipe) {
                        onSwipeListener?.invoke(SwipeEvent.LEFT)
                    }
                }
                result = true
            }
        }

        return result
    }


    private fun checkWhichBordersAreVisible() {
        displayedView?.let {
            currentBorder = when {
                it.left >= 0 && it.right <= width -> ViewBorder.BOTH
                it.left >= 0 -> ViewBorder.LEFT
                it.right <= width -> ViewBorder.RIGHT
                else -> ViewBorder.NONE
            }
        }
    }

    override fun onDocDoubleTap(docRelX: Float, docRelY: Float) {
        val newScale = when {
            scale == 1f -> 2f
            scale > 2f -> 1f
            else -> 4f
        }
        zoomTo(newScale, docRelX, docRelY)
    }

    override fun onDocTap(docRelX: Float, docRelY: Float) {
        displayedView?.let {
            // The click coordinates are relative to the rendered PDF size.
            // For handling frames we have to use size independent ratios from 0..1
            val docRatioX = docRelX / it.width
            val docRatioY = docRelY / it.height
            clickCoordinatesListener?.invoke(docRatioX to docRatioY)
        }
    }
}