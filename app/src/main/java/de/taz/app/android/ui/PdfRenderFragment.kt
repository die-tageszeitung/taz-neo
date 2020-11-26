package de.taz.app.android.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.fragment.app.Fragment
import de.taz.app.android.R
import de.taz.app.android.api.models.Frame
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_pdf_render.*
import java.io.File
import java.io.IOException

class PdfRenderFragment : Fragment(R.layout.fragment_pdf_render) {

    val log by Log
    var pdfPage: File? = null
    var frameList: List<Frame> = emptyList()
    private var pageIndex = 0
    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null

    // Math stuff
    var startX = 0f
    var startY = 0f

    companion object {
        fun createInstance(
            pdfPageWithFrameList: Pair<File, List<Frame>?>
        ): PdfRenderFragment {
            val fragment = PdfRenderFragment()
            fragment.pdfPage = pdfPageWithFrameList.first
            fragment.frameList = pdfPageWithFrameList.second ?: emptyList()
            return fragment
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onStart() {
        super.onStart()
        pdf_photo_view?.setOnPhotoTapListener { _, x, y ->
            showFrameIfPossible(x, y)
        }
        try {
            openRenderer()
            showPage(pageIndex)
        } catch (e: IOException) {
            log.error("Something went wrong opening pdf renderer: ${e.localizedMessage}")
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
        val bitmap = Bitmap.createBitmap(
            currentPage!!.width,
            currentPage!!.height,
            Bitmap.Config.ARGB_8888
        )
        currentPage!!.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        // We are ready to show the Bitmap to user
        pdf_photo_view?.setImageBitmap(bitmap)
    }

    private fun showFrameIfPossible(x: Float, y: Float) {
        log.debug("Clicked on x: $x   y:$y")
        frameList.forEach { log.verbose("possible frame: $it") }
        val frame = frameList.firstOrNull { it.x1 <= x && x < it.x2 && it.y1 <= y && y < it.y2 }
        log.debug("found frame with link: ${frame?.link}")
    }
}
