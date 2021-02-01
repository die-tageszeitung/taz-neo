package de.taz.app.android.ui.pdfViewer

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import com.artifex.mupdf.viewer.ReaderView

class MuPDFReaderView constructor(
    context: Context?
) : ReaderView(
    context
) {
    var onLeftBoarder = false
    var onRightBoarder = true

    val singleTapDetector =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                return true
            }
        })
    
    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        onLeftBoarder = displayedView.left >= 0
        onRightBoarder = displayedView.right <= width
        return super.onInterceptTouchEvent(ev)
    }

    /**
     * Calculates the click coordinates of a potentially zoomed in and scrolled part to the total
     * view. With that coordinates we can check if they are in any of the given frames.
     * If so, the according article will be shown.
     * returns Pair<Float, Float> as (x,y)
     */
    fun calculateClickCoordinates(clickedX: Float, clickedY: Float): Pair<Float, Float> {
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