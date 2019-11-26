package de.taz.app.android.ui.bookmarks

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.api.models.Article


class BookmarksAdapter(val bookmarksPresenter: BookmarksContract.Presenter) :
    RecyclerView.Adapter<BookmarksViewHolder>() {

    private var bookmarks : List<Article> = emptyList()

    fun setData(bookmarks: List<Article>) {
        this.bookmarks = bookmarks
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BookmarksViewHolder {
        return BookmarksViewHolder(bookmarksPresenter, parent)
    }

    override fun onBindViewHolder(holder: BookmarksViewHolder, position: Int) {
        val article = bookmarks[position]
        holder.bind(article)
    }

    override fun getItemCount() = bookmarks.size
}
