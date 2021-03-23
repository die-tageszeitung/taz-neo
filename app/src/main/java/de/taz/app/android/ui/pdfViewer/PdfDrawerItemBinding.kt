package de.taz.app.android.ui.pdfViewer

import android.content.Context
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import de.taz.app.android.R
import de.taz.app.android.api.models.PageType
import kotlinx.android.synthetic.main.view_pdf_drawer_item.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    fun bindView(itemView: PdfDrawerItem, itemData: PdfDrawerItemData) {
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
            // stretch panorama pages so they have same width as 2 pages with margin
            if (itemData.pageType == PageType.panorama) {
                val widthInDp = DRAWER_PAGE_WIDTH * 2 +
                        context.resources.getDimensionPixelSize(R.dimen.fragment_drawer_thumbnail_margin) / 2
                val width = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    widthInDp,
                    context.resources.displayMetrics
                ).toInt()
                val layoutParams = boundView?.view_drawer_pdf_page_card?.layoutParams
                layoutParams?.width = width
                boundView?.view_drawer_pdf_page_card?.layoutParams = layoutParams
            }

            val viewWidth = when (itemData.pageType) {
                PageType.panorama -> boundView?.resize(PdfDrawerWidth.FULL)
                else -> boundView?.resize(PdfDrawerWidth.HALF)
            } ?: Target.SIZE_ORIGINAL

            boundView?.let { view ->
                view.view_drawer_pdf_page?.let { imageView ->
                    Glide
                        .with(view)
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