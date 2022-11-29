package de.taz.app.android.ui.pdfViewer.mupdf

import android.content.Context
import androidx.appcompat.widget.AppCompatImageView

// Make our ImageViews opaque to optimize redraw
internal class OpaqueImageView(context: Context) : AppCompatImageView(context) {
    override fun isOpaque(): Boolean {
        return true
    }
}