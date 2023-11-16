package de.taz.app.android.ui.pdfViewer.mupdf

import de.taz.app.android.api.models.Page

fun interface OnCoordinatesClickedListener {
    fun onClick(page: Page, ratioX: Float, ratioY: Float, absoluteX: Float, absoluteY: Float)
}