package de.taz.app.android.ui.drawer.bookmarks

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.R
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.util.ToastHelper


class BookmarkListAdapter(private val activity: MainActivity, private var bookmarks: List<ArticleStub> = emptyList()) :
    RecyclerView.Adapter<BookmarkListAdapter.SectionListAdapterViewHolder>() {

    fun setData(bookmarks: List<ArticleStub>) {
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
            .inflate(R.layout.fragment_drawer_sections_item, parent, false) as TextView
        return SectionListAdapterViewHolder(
            textView
        )
    }

    override fun onBindViewHolder(holder: SectionListAdapterViewHolder, position: Int) {
        val articleStub = bookmarks[position]
        articleStub.let {
            holder.textView.text = articleStub.title
            holder.textView.setOnClickListener {
                // TODO activity.showArticle(articleStub)
                ToastHelper.getInstance().makeToast(articleStub.title ?: "untitled…")
            }
        }
    }

    override fun getItemCount() = bookmarks.size
}
