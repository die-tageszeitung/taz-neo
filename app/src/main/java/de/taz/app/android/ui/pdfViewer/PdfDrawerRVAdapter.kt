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
import java.io.File


class PdfDrawerRVAdapter(private var items: List<PdfDrawerItemModel>, private var currentPos: Int) :
    RecyclerView.Adapter<PdfDrawerRVAdapter.NavigationItemViewHolder>() {

    private lateinit var context: Context

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
        return items.count()
    }

    override fun onBindViewHolder(holder: NavigationItemViewHolder, position: Int) {
        if (position == currentPos) {
            holder.itemView.fragment_drawer_pdf_title.setTextColor(
                ContextCompat.getColor(context, R.color.drawer_sections_item_highlighted)
            )
        } else {
            holder.itemView.fragment_drawer_pdf_title.setTextColor(
                ContextCompat.getColor(context, R.color.drawer_sections_item)
            )
        }
        // Set the title:
        holder.itemView.fragment_drawer_pdf_title.text = items[position].title

        // Set the image:
        holder.itemView.fragment_drawer_pdf_page.setImageBitmap(
            getPreviewImageFromPdfFile(
                file = items[position].pdfFile
            )
        )
    }

    private fun getPreviewImageFromPdfFile(file: File): Bitmap {
        val widthInDp = 128f
        val width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, widthInDp, context.resources.displayMetrics).toInt()
        return MuPDFThumbnail(file.path).thumbOfFirstPage(width)
    }
}