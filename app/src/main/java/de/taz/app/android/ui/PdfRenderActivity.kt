package de.taz.app.android.ui

import de.taz.app.android.R
import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import de.taz.app.android.base.NightModeActivity
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.util.Log
import io.sentry.core.Sentry
import kotlinx.android.synthetic.main.activity_pdf_renderer.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class PdfRenderActivity : NightModeActivity(R.layout.activity_pdf_renderer) {

    val log by Log
    private var file: File? = null
    private var pageIndex = 0
    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.debug("hello from rendererActivity!!!!!!")

        val fileHelper = FileHelper.getInstance()
        try {
            val fileName = intent.extras?.getString(Intent.EXTRA_TEXT)
            CoroutineScope(Dispatchers.IO).launch {
                file = fileName?.let { fileHelper.getFile(it) }
            }
        } catch (e: NullPointerException) {
            val hint = "no FILENAME given as parameter, finishing PdfRendererActivity"
            log.error(hint)
            Sentry.captureMessage(hint)
            finish()
        }
        log.debug("file exists? ${file?.exists()}")

        setContentView(R.layout.activity_pdf_renderer)
        pageIndex = 0
    }

    override fun onStart() {
        super.onStart()
        try {
            openRenderer(applicationContext)
            showPage(pageIndex)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    public override fun onStop() {
        try {
            closeRenderer()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        super.onStop()
    }

    fun onPreviousDocClick() {
        showPage(currentPage!!.index - 1)
    }

    fun onNextDocClick() {
        showPage(currentPage!!.index + 1)
    }

    @Throws(IOException::class)
    private fun openRenderer(context: Context) {
        parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        // This is the PdfRenderer we use to render the PDF.
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

        // Make sure to close the current page before opening another one.
        if (null != currentPage) {
            currentPage!!.close()
        }

        // Use `openPage` to open a specific page in PDF.
        currentPage = pdfRenderer!!.openPage(index)

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


    val pageCount: Int
        get() = pdfRenderer!!.pageCount

    companion object {
        private const val FILENAME = "report.pdf"
    }
}