package de.taz.app.android.ui.pdfViewer

import de.taz.app.android.api.models.Frame
import java.io.File

data class PdfPageListModel(
    var pdfFile: File,
    var frameList: List<Frame>,
    var title: String,
    var pagina: String
)