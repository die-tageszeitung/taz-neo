package de.taz.app.android.ui.pdfViewer.mupdf

fun interface OnPageChangeCallback {
    fun onPageSelected(position: Int)
}