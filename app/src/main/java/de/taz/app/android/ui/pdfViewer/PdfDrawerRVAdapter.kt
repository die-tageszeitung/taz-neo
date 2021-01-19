package de.taz.app.android.ui.pdfViewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
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
        // To highlight the selected item, show different background color
        if (position == currentPos) {
            holder.itemView.setBackgroundColor(
                ContextCompat.getColor(
                    context,
                    R.color.colorPrimaryDark
                )
            )
        } else {
            holder.itemView.setBackgroundColor(
                ContextCompat.getColor(
                    context,
                    android.R.color.transparent
                )
            )
        }
        holder.itemView.fragment_drawer_pdf_page.setColorFilter(
            R.color.white,
            PorterDuff.Mode.SRC_ATOP
        )

        holder.itemView.fragment_drawer_pdf_title.text = items[position].title
        // Set the image:
        CoroutineScope(Dispatchers.Main).launch {
            holder.itemView.fragment_drawer_pdf_page.setImageBitmap(
                getPreviewImageFromPdfFile(
                    file = items[position].pdfFile
                )
            )
        }
    }

    private fun getPreviewImageFromPdfFile(file: File): Bitmap {
        val parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val bitmap = Bitmap.createBitmap(
            300,
            400,
            Bitmap.Config.ARGB_8888
        )

        // create a new renderer
        val renderer = PdfRenderer(parcelFileDescriptor)

        val page = renderer.openPage(0)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        // do stuff with the bitmap
        page.close()
        renderer.close()
        return bitmap
    }
}