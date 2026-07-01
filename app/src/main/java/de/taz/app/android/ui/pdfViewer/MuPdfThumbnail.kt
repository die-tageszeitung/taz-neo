package de.taz.app.android.ui.pdfViewer
import android.graphics.Bitmap
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.Point
import de.taz.app.android.ui.pdfViewer.mupdf.MuPDFCore
import java.io.File
import androidx.core.graphics.createBitmap

class MuPDFThumbnail(filename: String) : MuPDFCore(File(filename).readBytes(), filename) {

    fun thumbnail(w: Int): Bitmap {
        val pageSize = getPageSize(0)
        val mSourceScale = w / pageSize.x
        val size = Point(
            pageSize.x * mSourceScale,
            pageSize.y * mSourceScale
        )
        val bp = createBitmap(size.x.toInt(), size.y.toInt())
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