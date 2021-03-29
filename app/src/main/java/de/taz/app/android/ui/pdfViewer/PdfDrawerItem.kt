package de.taz.app.android.ui.pdfViewer

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import de.taz.app.android.R


enum class PdfDrawerWidth {
    HALF, FULL
}

@SuppressLint("ClickableViewAccessibility")
class PdfDrawerItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        LayoutInflater.from(context).inflate(R.layout.view_pdf_drawer_item, this, true)
    }

    fun resize(width: PdfDrawerWidth): Int? {
        val parent = parent as View?
        return parent?.let {
            val widthDp = when (width) {
                PdfDrawerWidth.HALF -> it.width / 2
                PdfDrawerWidth.FULL -> it.width
            }
            layoutParams.width = widthDp
            widthDp
        }
    }
}