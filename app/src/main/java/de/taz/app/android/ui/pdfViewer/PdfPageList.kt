package de.taz.app.android.ui.pdfViewer

import de.taz.app.android.api.models.Frame
import de.taz.app.android.api.models.PageType
import java.io.File

data class PdfPageList(
    var pdfFile: File,
    var frameList: List<Frame>,
    var title: String,
    var pagina: String,
    var pageType: PageType
)