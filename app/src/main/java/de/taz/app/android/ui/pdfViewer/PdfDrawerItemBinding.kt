package de.taz.app.android.ui.pdfViewer

import android.content.Context
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.target.Target
import de.taz.app.android.R
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.PageType
import de.taz.app.android.singletons.StorageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class PdfDrawerItemData(
    val title: String,
    val pageType: PageType,
    val position: Int,
    val activePosition: Int,
    val pdfFile: FileEntry
)

class PdfDrawerItemBinding(
    private val context: Context,

    ) {
    private val storageService: StorageService =
        StorageService.getInstance(context.applicationContext)

    private var boundView: PdfDrawerItem? = null
    fun bindView(
        itemView: PdfDrawerItem,
        itemData: PdfDrawerItemData,
        glideRequestManager: RequestManager
    ) {
        boundView = itemView
        val viewDrawerPdfTitle = itemView.findViewById<TextView>(R.id.view_drawer_pdf_title)

        if (itemData.position == itemData.activePosition) {
            viewDrawerPdfTitle.setTextColor(
                ContextCompat.getColor(context, R.color.pdf_drawer_sections_item_highlighted)
            )
        } else {
            viewDrawerPdfTitle.setTextColor(
                ContextCompat.getColor(context, R.color.pdf_drawer_sections_item)
            )
        }
        // Set the title:
        viewDrawerPdfTitle.text = itemData.title

        // Set the image:
        CoroutineScope(Dispatchers.Main).launch {
            val viewWidth = when (itemData.pageType) {
                PageType.panorama -> boundView?.resize(PdfDrawerWidth.FULL)
                else -> boundView?.resize(PdfDrawerWidth.HALF)
            } ?: Target.SIZE_ORIGINAL

            val file = storageService.getFile(itemData.pdfFile)

            boundView?.findViewById<ImageView>(R.id.view_drawer_pdf_page)?.let { imageView ->
                glideRequestManager
                    .load(file?.absolutePath)
                    .override(viewWidth, Target.SIZE_ORIGINAL)
                    .into(imageView)
            }
        }
    }

    fun unbindView() {
        boundView = null
    }
}