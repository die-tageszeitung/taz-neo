package de.taz.app.android.ui.bookmarks

import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.persistence.repository.ArticleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class BookmarksAdapter(
    private val bookmarksPresenter: BookmarksContract.Presenter
) :
    RecyclerView.Adapter<BookmarksViewHolder>() {

    private var bookmarks: List<Article> = emptyList()

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

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        0,
        ItemTouchHelper.LEFT
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, swipeDir: Int) {
            val position = viewHolder.adapterPosition
            CoroutineScope(Dispatchers.IO).launch {
                ArticleRepository.getInstance().debookmarkArticle(bookmarks[position])
            }
        }

    })

}
