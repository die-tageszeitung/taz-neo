package de.taz.app.android.ui.pdfViewer

import android.content.Context
import android.graphics.Bitmap
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.api.models.PageType
import kotlinx.android.synthetic.main.fragment_pdf_drawer_item.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.absoluteValue

const val DRAWER_PAGE_WIDTH = 116f

class PdfDrawerRecyclerViewAdapter(private var itemList: List<PdfPageList>) :
    RecyclerView.Adapter<PdfDrawerRecyclerViewAdapter.NavigationItemViewHolder>() {

    private lateinit var context: Context

    var activePosition = RecyclerView.NO_POSITION
        set(value) {
            val oldValue = field
            field = value
            if (value >= 0 && itemList.size > value) {
                notifyItemChanged(value)
            }
            if (oldValue >= 0 && itemList.size > value) {
                notifyItemChanged(oldValue)
            }
        }

    class NavigationItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NavigationItemViewHolder {
        context = parent.context
        val navItem = LayoutInflater.from(parent.context).inflate(
            R.layout.fragment_pdf_drawer_item,
            parent,
            false
        )
        return NavigationItemViewHolder(navItem)
    }

    override fun getItemCount(): Int {
        return itemList.count()
    }

    override fun onBindViewHolder(holder: NavigationItemViewHolder, position: Int) {
        if (position == activePosition) {
            holder.itemView.fragment_drawer_pdf_title.setTextColor(
                ContextCompat.getColor(context, R.color.drawer_sections_item_highlighted)
            )
        } else {
            holder.itemView.fragment_drawer_pdf_title.setTextColor(
                ContextCompat.getColor(context, R.color.drawer_sections_item)
            )
        }
        // Set the title:
        holder.itemView.fragment_drawer_pdf_title.text = itemList[position].title

        // Set the image:
        CoroutineScope(Dispatchers.Default).launch {
            val img = getPreviewImageFromPdfFile(
                file = itemList[position].pdfFile,
                isPanorama = itemList[position].pageType == PageType.panorama
            )
            withContext(Dispatchers.Main) {
                holder.itemView.fragment_drawer_pdf_page.setImageBitmap(
                    img
                )
                // stretch panorama pages so they have same width as 2 pages with margin
                if (itemList[position].pageType == PageType.panorama) {
                    val widthInDp = DRAWER_PAGE_WIDTH * 2 +
                            context.resources.getDimensionPixelSize(R.dimen.fragment_drawer_thumbnail_margin)/2
                    val width = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        widthInDp,
                        context.resources.displayMetrics
                    ).toInt()
                    val layoutParams = holder.itemView.fragment_drawer_pdf_page_card.layoutParams
                    layoutParams.width = width

                    holder.itemView.fragment_drawer_pdf_page_card.layoutParams = layoutParams
                }
            }
        }
    }

    private fun getPreviewImageFromPdfFile(file: File, isPanorama: Boolean): Bitmap {
        val widthInDp = if (isPanorama) {
            DRAWER_PAGE_WIDTH * 2
        } else {
            DRAWER_PAGE_WIDTH
        }
        val width = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            widthInDp,
            context.resources.displayMetrics
        ).toInt()
        return MuPDFThumbnail(file.path).thumbnail(width)
    }
}