package de.taz.app.android.ui.pdfViewer

import android.content.Context
import androidx.core.content.ContextCompat
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.target.Target
import de.taz.app.android.R
import de.taz.app.android.api.models.PageType
import kotlinx.android.synthetic.main.view_pdf_drawer_item.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

data class PdfDrawerItemData(
    val title: String,
    val pageType: PageType,
    val position: Int,
    val activePosition: Int,
    val pdfFile: File
)

class PdfDrawerItemBinding(
    private val context: Context,

) {
    private var boundView: PdfDrawerItem? = null
    fun bindView(
        itemView: PdfDrawerItem,
        itemData: PdfDrawerItemData,
        glideRequestManager: RequestManager
    ) {
        boundView = itemView
        if (itemData.position == itemData.activePosition) {
            boundView?.view_drawer_pdf_title?.setTextColor(
                ContextCompat.getColor(context, R.color.drawer_sections_item_highlighted)
            )
        } else {
            boundView?.view_drawer_pdf_title?.setTextColor(
                ContextCompat.getColor(context, R.color.drawer_sections_item)
            )
        }
        // Set the title:
        boundView?.view_drawer_pdf_title?.text = itemData.title

        // Set the image:
        CoroutineScope(Dispatchers.Main).launch {
            val viewWidth = when (itemData.pageType) {
                PageType.panorama -> boundView?.resize(PdfDrawerWidth.FULL)
                else -> boundView?.resize(PdfDrawerWidth.HALF)
            } ?: Target.SIZE_ORIGINAL

            boundView?.let { view ->
                view.view_drawer_pdf_page?.let { imageView ->
                    glideRequestManager
                        .load(itemData.pdfFile.absolutePath)
                        .override(viewWidth, Target.SIZE_ORIGINAL)
                        .into(imageView)

                }
            }
        }
    }

    fun unbindView() {
        boundView = null
    }
}