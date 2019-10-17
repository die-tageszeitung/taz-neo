package de.taz.app.android.ui.drawer.bookmarks

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.R
import de.taz.app.android.api.models.ArticleBase
import de.taz.app.android.util.ToastHelper


class BookmarkListAdapter(private val activity: MainActivity, private var bookmarks: List<ArticleBase> = emptyList()) :
    RecyclerView.Adapter<BookmarkListAdapter.SectionListAdapterViewHolder>() {

    fun setData(bookmarks: List<ArticleBase>) {
        this.bookmarks = bookmarks
        notifyDataSetChanged()
    }

    class SectionListAdapterViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SectionListAdapterViewHolder {
        // create a new view
        val textView = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_drawer_menu_sections_item, parent, false) as TextView
        return SectionListAdapterViewHolder(
            textView
        )
    }

    override fun onBindViewHolder(holder: SectionListAdapterViewHolder, position: Int) {
        val articleBase = bookmarks[position]
        articleBase.let {
            holder.textView.text = articleBase.title
            holder.textView.setOnClickListener {
                // TODO activity.showArticle(articleBase)
                ToastHelper.getInstance().makeToast(articleBase.title ?: "untitled…")
            }
        }
    }

    override fun getItemCount() = bookmarks.size
}
