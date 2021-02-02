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
import kotlinx.android.synthetic.main.fragment_pdf_drawer_item.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

const val DRAWER_PAGE_WIDTH = 116f

class PdfDrawerRecyclerViewAdapter(private var itemListModel: List<PdfPageListModel>) :
    RecyclerView.Adapter<PdfDrawerRecyclerViewAdapter.NavigationItemViewHolder>() {

    private lateinit var context: Context

    var activePosition = RecyclerView.NO_POSITION
        set(value) {
            val oldValue = field
            field = value
            if (value >= 0 && itemListModel.size > value) {
                notifyItemChanged(value)
            }
            if (oldValue >= 0 && itemListModel.size > value) {
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
        return itemListModel.count()
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
        holder.itemView.fragment_drawer_pdf_title.text = itemListModel[position].title

        // Set the image:
        CoroutineScope(Dispatchers.Default).launch {
            val img = getPreviewImageFromPdfFile(
                file = itemListModel[position].pdfFile,
                isPortrait = itemListModel[position].pagina.contains("-")
            )
            withContext(Dispatchers.Main){
                holder.itemView.fragment_drawer_pdf_page.setImageBitmap(
                    img
                )
            }
        }
    }

    private fun getPreviewImageFromPdfFile(file: File, isPortrait: Boolean): Bitmap {
        val widthInDp = if (isPortrait) {
            DRAWER_PAGE_WIDTH*2
        } else {
            DRAWER_PAGE_WIDTH
        }
        val width = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            widthInDp,
            context.resources.displayMetrics
        ).toInt()
        return MuPDFThumbnail(file.path).thumbOfFirstPage(width)
    }
}