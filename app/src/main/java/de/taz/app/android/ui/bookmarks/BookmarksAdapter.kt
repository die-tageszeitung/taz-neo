package de.taz.app.android.ui.bookmarks

import android.graphics.Canvas
import android.view.ViewGroup
import android.widget.RelativeLayout
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

        override fun onChildDrawOver(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder?,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            val foregroundView =
                viewHolder?.itemView?.findViewById<RelativeLayout>(R.id.fragment_bookmark_foreground)
            getDefaultUIUtil().onDrawOver(
                c,
                recyclerView,
                foregroundView,
                dX,
                dY,
                actionState,
                isCurrentlyActive
            )
        }

        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            val foregroundView =
                viewHolder.itemView.findViewById<RelativeLayout>(R.id.fragment_bookmark_foreground)

            getDefaultUIUtil().onDraw(
                c,
                recyclerView,
                foregroundView,
                dX,
                dY,
                actionState,
                isCurrentlyActive
            )
        }

    })

}
