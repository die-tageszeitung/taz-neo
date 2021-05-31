package de.taz.app.android.ui.pdfViewer

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull

enum class ViewBorder {
    LEFT,
    RIGHT,
    BOTH,
    NONE
}

class MuPDFReaderView constructor(
    context: Context?
) : com.artifex.mupdfdemo.ReaderView(
    context
), GestureDetector.OnDoubleTapListener {
    var clickCoordinatesListener: ((Pair<Float, Float>) -> Unit)? = null
    var onBorderListener: ((ViewBorder) -> Unit)? = null
    var onScaleOutListener: ((Boolean) -> Unit)? = null
    var onScaleListener: ((Boolean) -> (Unit))? = null
    val log by Log

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        runIfNotNull(clickCoordinatesListener, displayedView) { listener, _ ->
            listener.invoke(calculateClickCoordinates(e.x, e.y))
        }
        return true
    }

    override fun onDoubleTapEvent(p0: MotionEvent?): Boolean {
        return false
    }

    override fun onDoubleTap(ev: MotionEvent): Boolean {
        val newScale = if (mScale == 1f) {
            2f
        } else if (mScale > 2f) {
            1f
        } else {
            4f
        }
        // Work out the focus point relative to the view top left
        val viewFocusX = ev.x - (displayedView.left + mXScroll)
        val viewFocusY = ev.y - (displayedView.top + mYScroll)
        // Scroll to maintain the focus point
        mXScroll += (viewFocusX - viewFocusX * (newScale / mScale)).toInt()
        mYScroll += (viewFocusY - viewFocusY * (newScale / mScale)).toInt()
        mScale = newScale
        requestLayout()
        if (newScale == 1f) {
            onScaleOutListener?.invoke(true)
        }
        return true
    }

    override fun onScale(detector: ScaleGestureDetector?): Boolean {
        log.verbose("scaling: ${detector?.scaleFactor}. total scale: ${displayedView.width / width.toFloat()} displayedVies.scale: ${displayedView.scaleX}")
        val pinchOut = detector?.scaleFactor!! < 0.9
        onScaleOutListener?.invoke(pinchOut)
        return super.onScale(detector)
    }

    override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
        onScaleListener?.invoke(true)
        return super.onScaleBegin(detector)
    }

    override fun onScaleEnd(detector: ScaleGestureDetector?) {
        onScaleListener?.invoke(false)
        super.onScaleEnd(detector)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        displayedView?.let {
            val border = when {
                it.left >= 0 && it.right <= width -> ViewBorder.BOTH
                it.left >= 0 -> ViewBorder.LEFT
                it.right <= width -> ViewBorder.RIGHT
                else -> ViewBorder.NONE
            }
            onBorderListener?.invoke(border)
        }
        return super.onInterceptTouchEvent(ev)
    }

    /**
     * Calculates the click coordinates of a potentially zoomed in and scrolled part to the total
     * view. With that coordinates we can check if they are in any of the given frames.
     * If so, the according article will be shown.
     * returns Pair<Float, Float> as (x,y)
     */
    private fun calculateClickCoordinates(clickedX: Float, clickedY: Float): Pair<Float, Float> {
        // Cet the scale factor from dividing total image by viewed part (eg. 2.0):
        val scaleX: Float = displayedView.width / width.toFloat()
        val scaleY: Float = displayedView.height / height.toFloat()

        // Calculate the relatively clicked coordinates by dividing the scale factor (e.g. 200):
        val relClickedX = clickedX / scaleX
        val relClickedY = clickedY / scaleY

        // Get the missed part of the total image (eg. zoomed & scrolled in the middle: -600):
        val missedFromZoomX = displayedView.left / scaleX
        val missedFromZoomY = displayedView.top / scaleY

        // Sum up to get the real click coordinates of the total image (e.g. 200 - (-600) = 800):
        val calculatedX = relClickedX - missedFromZoomX
        val calculatedY = relClickedY - missedFromZoomY

        // Calculate the ratio (e.g. 800 / 1080 = 0.7407):
        x = calculatedX / width.toFloat()
        y = calculatedY / height.toFloat()

        return x to y
    }
}