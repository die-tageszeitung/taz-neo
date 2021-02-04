package de.taz.app.android.ui.pdfViewer

import android.content.Context
import android.view.MotionEvent
import com.artifex.mupdf.viewer.ReaderView

enum class ViewBorder {
    LEFT,
    RIGHT,
    BOTH,
    NONE
}

class MuPDFReaderView constructor(
    context: Context?
) : ReaderView(
    context
) {
    var clickCoordinatesListener: ((Pair<Float, Float>) -> Unit)? = null
    var onBorderListener: ((ViewBorder) -> Unit)? = null

    override fun onSingleTapUp(event: MotionEvent): Boolean {
        clickCoordinatesListener?.invoke(calculateClickCoordinates(event.x, event.y))
        return true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        val border = when {
            displayedView.left >= 0 && displayedView.right <= width -> ViewBorder.BOTH
            displayedView.left >= 0 -> ViewBorder.LEFT
            displayedView.right <= width -> ViewBorder.RIGHT
            else -> ViewBorder.NONE
        }
        onBorderListener?.invoke(border)
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