package de.taz.app.android.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.fragment.app.Fragment
import de.taz.app.android.R
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.activity_pdf_renderer.*
import kotlinx.android.synthetic.main.fragment_pdf_viewer.*
import java.io.File
import java.io.IOException

class PdfFragment: Fragment(R.layout.fragment_pdf_viewer) {
    val log by Log
    var file: File? = null
    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null

    companion object {
        fun createInstance(
            file: File?
        ): PdfFragment {
            val fragment = PdfFragment()
            fragment.file = file
            return fragment
        }
    }

    override fun onStart() {
        log.debug("onStart!!! file? ${file?.exists()} context? ${context?.applicationContext}")
        super.onStart()
        context?.applicationContext?.let {
            try {
                openRenderer(it)
                showPage()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    override fun onStop() {
        log.debug("onStop!!!")
        try {
            closeRenderer()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        super.onStop()
    }

    @Throws(IOException::class)
    private fun openRenderer(context: Context) {
        parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        // This is the PdfRenderer we use to render the PDF.
        log.debug("pardel: $parcelFileDescriptor")
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

    private fun showPage() {

        // Make sure to close the current page before opening another one.
        if (null != currentPage) {
            currentPage!!.close()
        }

        // Use `openPage` to open a specific page in PDF.
        currentPage = pdfRenderer!!.openPage(0)

        // Important: the destination bitmap must be ARGB (not RGB).
        val bitmap = Bitmap.createBitmap(
            currentPage!!.width,
            currentPage!!.height,
            Bitmap.Config.ARGB_8888
        )
        // Here, we render the page onto the Bitmap.
        // To render a portion of the page, use the second and third parameter. Pass nulls to get
        // the default result.
        // Pass either RENDER_MODE_FOR_DISPLAY or RENDER_MODE_FOR_PRINT for the last parameter.
        currentPage!!.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        // We are ready to show the Bitmap to user.
        pdf_image.setImageBitmap(bitmap)
    }
}
