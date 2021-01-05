package de.taz.app.android.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.artifex.mupdf.viewer.ReaderView
import de.taz.app.android.api.models.Frame
import de.taz.app.android.ui.bookmarks.BookmarkViewerActivity
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_pdf_render.view.*


class MuPDFReaderView constructor(context: Context?, frames: List<Frame>) : ReaderView(context) {
    private val log by Log
    var frameList: List<Frame> = emptyList()

    init {
        frameList = frames
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        log.debug("clicked on ${e.x} and ${e.y}")
        log.debug("width: ${displayedView.width} and height: ${displayedView.height}")
        showFrameIfPossible(displayedView, e.x, e.y)
        return true
    }

    private fun showFrameIfPossible(view: View, bigX: Float, bigY: Float) {
        x = bigX.div(view.width)
        y = bigY.div(view.height)
        log.debug("Clicked on x: $x   y:$y")
        val frame = frameList.firstOrNull { it.x1 <= x && x < it.x2 && it.y1 <= y && y < it.y2 }
        log.debug("found frame with link: ${frame?.link}")
        frame?.let {
            if (it.link?.startsWith("art") == true && it.link.endsWith(".html")) {
                Intent(context, BookmarkViewerActivity::class.java).apply {
                    putExtra(BookmarkViewerActivity.KEY_SHOWN_ARTICLE, it.link)
                    context?.startActivity(this)
                }
            } else {
                // TODO: go to page getPositionOfPdf(it.link)
            }
        } ?: run {
            // remove this line later on it just helps to show clickable frames
            showPossibleFrames(view)
        }
    }

    /**
     * This is just a helper function to show the possible frames.
     * (as they are not correct at the moment)
     * Only works on no zoomed pages on tap.
     */
    private fun showPossibleFrames(view: View) {
        val w = view.width
        val h = view.height
        val myPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        myPaint.color = Color.RED
        myPaint.strokeWidth = 2f
        myPaint.style = Paint.Style.STROKE
        val tempBitmap = Bitmap.createBitmap(
            w,
            h,
            Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(tempBitmap)
        frameList.forEach {
            log.verbose("possible frame: $it")
            tempCanvas.drawRect(
                it.x1 * w,
                it.y1 * h,
                it.x2 * w,
                it.y2 * h,
                myPaint
            )
        }
        // somehow not working: :(
        outside_imageview?.setImageBitmap(tempBitmap)
    }
}
