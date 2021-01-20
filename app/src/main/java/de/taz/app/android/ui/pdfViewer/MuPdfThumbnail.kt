package de.taz.app.android.ui.pdfViewer

import android.graphics.Bitmap
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.Point
import com.artifex.mupdf.viewer.MuPDFCore


class MuPDFThumbnail(filename: String?) : MuPDFCore(filename) {

    fun thumbOfFirstPage(w: Int): Bitmap {
        val pageSize = getPageSize(0)
        val mSourceScale = w / pageSize.x
        val size = Point(
            pageSize.x * mSourceScale,
            pageSize.y * mSourceScale
        )
        val bp = Bitmap.createBitmap(
            size.x.toInt(),
            size.y.toInt(),
            Bitmap.Config.ARGB_8888
        )
        drawPage(
            bp,
            0,
            size.x.toInt(),
            size.y.toInt(),
            0,
            0,
            size.x.toInt(),
            size.y.toInt(),
            Cookie()
        )
        return bp
    }
}