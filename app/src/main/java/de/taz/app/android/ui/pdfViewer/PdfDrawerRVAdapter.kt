package de.taz.app.android.ui.pdfViewer

import android.content.Context
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import kotlinx.android.synthetic.main.fragment_pdf_drawer_item.view.*

class PdfDrawerRVAdapter(private var items: ArrayList<PdfDrawerItemModel>, private var currentPos: Int) :
    RecyclerView.Adapter<PdfDrawerRVAdapter.NavigationItemViewHolder>() {

    private lateinit var context: Context

    class NavigationItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NavigationItemViewHolder {
        context = parent.context
        val navItem = LayoutInflater.from(parent.context).inflate(R.layout.fragment_pdf_drawer_item, parent, false)
        return NavigationItemViewHolder(navItem)
    }

    override fun getItemCount(): Int {
        return items.count()
    }

    override fun onBindViewHolder(holder: NavigationItemViewHolder, position: Int) {
        // To highlight the selected item, show different background color
        if (position == currentPos) {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.colorPrimaryDark))
        } else {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
        }
        holder.itemView.fragment_drawer_pdf_page.setColorFilter(R.color.white, PorterDuff.Mode.SRC_ATOP)
        //val font = ResourcesCompat.getFont(context, R.font.mycustomfont)
        //holder.itemView.navigation_text.typeface = font
        //holder.itemView.navigation_text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20.toFloat())

        holder.itemView.fragment_drawer_pdf_title.text = items[position].title

        holder.itemView.fragment_drawer_pdf_page.setImageResource(items[position].page)
    }

}