package de.taz.app.android.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.fragment.app.Fragment
import de.taz.app.android.R
import de.taz.app.android.api.models.Frame
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.ui.bookmarks.BookmarkViewerActivity
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_pdf_render.*
import java.io.File
import java.io.IOException

const val QUALITY_FACTOR = 2

class PdfRenderFragment : Fragment(R.layout.fragment_pdf_render) {

    val log by Log
    var pdfPage: File? = null
    var frameList: List<Frame> = emptyList()
    var issueKey: IssueKey? = null
    private var pageIndex = 0
    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var bitmap: Bitmap? = null

    companion object {
        fun createInstance(
                pdfPageWithFrameList: Pair<File, List<Frame>?>,
                issueKey: IssueKey
        ): PdfRenderFragment {
            val fragment = PdfRenderFragment()
            fragment.pdfPage = pdfPageWithFrameList.first
            fragment.frameList = pdfPageWithFrameList.second ?: emptyList()
            fragment.issueKey = issueKey
            return fragment
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            openRenderer()
            showPage(pageIndex)
        } catch (e: IOException) {
            log.error("Something went wrong opening pdf renderer: ${e.localizedMessage}")
        }

        pdf_photo_view?.setOnPhotoTapListener { _, x, y ->
            showFrameIfPossible(x, y)
        }
        pdf_photo_view?.setOnMatrixChangeListener {
            outside_imageview?.setImageBitmap(null)
        }
    }

    override fun onStop() {
        try {
            closeRenderer()
        } catch (e: IOException) {
            log.error("Something went wrong closing pdf renderer: ${e.localizedMessage}")
        }
        super.onStop()
    }

    private fun openRenderer() {
        parcelFileDescriptor =
            ParcelFileDescriptor.open(pdfPage, ParcelFileDescriptor.MODE_READ_ONLY)

        // This is the PdfRenderer we use to render the PDF
        if (parcelFileDescriptor != null) {
            pdfRenderer = PdfRenderer(parcelFileDescriptor!!)
        }
    }

    private fun closeRenderer() {
        if (null != currentPage) {
            currentPage!!.close()
        }
        pdfRenderer!!.close()
        parcelFileDescriptor!!.close()
    }

    private fun showPage(index: Int) {
        if (pdfRenderer!!.pageCount <= index) {
            return
        }

        // Make sure to close the current page before opening another one
        if (null != currentPage) {
            currentPage!!.close()
        }

        currentPage = pdfRenderer!!.openPage(index)

        // Important: the destination bitmap must be ARGB (not RGB)
        bitmap = Bitmap.createBitmap(
                currentPage!!.width * QUALITY_FACTOR,
                currentPage!!.height * QUALITY_FACTOR,
                Bitmap.Config.ARGB_8888
        )
        bitmap?.let {
            currentPage!!.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }

        // We are ready to show the Bitmap to user
        pdf_photo_view?.setImageBitmap(bitmap)
    }

    private fun showFrameIfPossible(x: Float, y: Float) {
        log.debug("Clicked on x: $x   y:$y")

        val frame = frameList.firstOrNull { it.x1 <= x && x < it.x2 && it.y1 <= y && y < it.y2 }
        log.debug("found frame with link: ${frame?.link}")
        frame?.let {
            if (it.link?.endsWith(".html") == true) {
                Intent(context, BookmarkViewerActivity::class.java).apply {
                    putExtra(BookmarkViewerActivity.KEY_SHOWN_ARTICLE, it.link)
                    context?.startActivity(this)
                }
            } else {
                // TODO: go to page getPositionOfPdf(it.link)
            }
        } ?: run {
            // remove this line later on it just helps to show clickable frames
            showPossibleFrames()
        }
    }

    /**
     * This is just a helper function to show the possible frames.
     * (as they are not correct at the moment)
     * Only works on no zoomed pages on tap.
     */
    private fun showPossibleFrames() {
        val w = currentPage!!.width
        val h = currentPage!!.height
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
        outside_imageview?.setImageBitmap(tempBitmap)
    }
}
