package de.taz.app.android.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import com.artifex.mupdf.viewer.ReaderView
import de.taz.app.android.api.models.Frame
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.ui.issueViewer.IssueViewerActivity
import de.taz.app.android.ui.issueViewer.IssueViewerActivity.Companion.COME_FROM_PDF
import de.taz.app.android.ui.issueViewer.IssueViewerActivity.Companion.KEY_DISPLAYABLE
import de.taz.app.android.ui.issueViewer.IssueViewerActivity.Companion.KEY_ISSUE_KEY
import de.taz.app.android.util.Log


@SuppressLint("ViewConstructor")
class MuPDFReaderView constructor(context: Context?, frames: List<Frame>, iK: IssueKey) : ReaderView(
    context
) {
    private val log by Log
    var frameList: List<Frame> = emptyList()
    var issueKey: IssueKey

    init {
        frameList = frames
        this.issueKey = iK
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        showFrameIfPossible(displayedView, e.x, e.y)
        return false
    }

    private fun showFrameIfPossible(view: View, clickedX: Float, clickedY: Float) {

        val scaleX: Float = view.width / width.toFloat()
        val scaleY: Float = view.height / height.toFloat()

        val relClickedX = clickedX / scaleX
        val relClickedY = clickedY / scaleY

        val missedFromZoomX = view.left / scaleX
        val missedFromZoomY = view.top / scaleY

        val calculatedX = relClickedX - missedFromZoomX
        val calculatedY = relClickedY - missedFromZoomY

        x = calculatedX / width.toFloat()
        y = calculatedY / height.toFloat()

        log.debug("Clicked on x: $x, y:$y [scale: $scaleX]")
        val frame = frameList.firstOrNull { it.x1 <= x && x < it.x2 && it.y1 <= y && y < it.y2 }
        log.debug("found frame with link: ${frame?.link}")
        frame?.let {
            if (it.link?.startsWith("art") == true && it.link.endsWith(".html")) {
                Intent(context, IssueViewerActivity::class.java).apply {
                    putExtra(COME_FROM_PDF, true)
                    putExtra(KEY_ISSUE_KEY, issueKey)
                    putExtra(KEY_DISPLAYABLE, it.link)
                    context?.startActivity(this)
                }
            } else {
                // TODO: go to page getPositionOfPdf(it.link)
                log.warn("no article maybe advertisement or link to other page?")
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
            Bitmap.Config.ARGB_8888
        )
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
        // somehow not working: :/
        (view as ImageView).setImageBitmap(tempBitmap)
    }
}