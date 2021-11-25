package de.taz.app.android.ui.pdfViewer

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.RequestManager
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.PageType

class PdfDrawerRecyclerViewAdapter(
    private val itemList: List<Page>,
    private val glideRequestManager: RequestManager
):
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

    inner class NavigationItemViewHolder(
        private val view: PdfDrawerItem
    ) : RecyclerView.ViewHolder(view) {
        private var binder: PdfDrawerItemBinding? = null

        fun bind(position: Int) {
            binder = PdfDrawerItemBinding(
                context
            )
            binder?.bindView(
                view,
                PdfDrawerItemData(
                    title = itemList[position].title ?: "",
                    pageType = itemList[position].type ?: PageType.left,
                    position = position,
                    activePosition = activePosition,
                    pdfFile = itemList[position].pagePdf
                ),
                glideRequestManager
            )
        }

        fun unbind() {
            binder?.unbindView()
            binder = null
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NavigationItemViewHolder {
        context = parent.context
        val navItem = PdfDrawerItem(context)
        return NavigationItemViewHolder(navItem)
    }

    override fun getItemCount(): Int {
        return itemList.count()
    }

    override fun onBindViewHolder(holder: NavigationItemViewHolder, position: Int) {
        holder.unbind()
        holder.bind(position)
    }
}