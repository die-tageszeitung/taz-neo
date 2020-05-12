package de.taz.app.android.ui.bookmarks

import android.graphics.Canvas
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
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

    private var bookmarks: MutableList<Article> = emptyList<Article>().toMutableList()

    fun setData(bookmarks: MutableList<Article>) {
        this.bookmarks = bookmarks
        notifyDataSetChanged()
    }

    fun restoreBookmark(article: Article, position: Int) {
        bookmarks.add(position, article)
        CoroutineScope(Dispatchers.IO).launch {
            ArticleRepository.getInstance().bookmarkArticle(article)
        }
        notifyItemInserted(position)
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
                val removedBookmark = bookmarks[position]
                ArticleRepository.getInstance().debookmarkArticle(bookmarks[position])
                notifyItemRemoved(position)

                // showing snack bar with Undo option
                val undoBar = Snackbar
                    .make(
                        viewHolder.itemView,
                        R.string.fragment_bookmarks_deleted,
                        Snackbar.LENGTH_LONG
                    )
                undoBar.setAction(R.string.fragment_bookmarks_undo) {
                    restoreBookmark(removedBookmark, position)
                }
                undoBar.setActionTextColor(
                    ResourcesCompat.getColor(
                        viewHolder.itemView.resources,
                        R.color.deleteRed,
                        null
                    )
                )
                undoBar.show()
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

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            val foregroundView =
                viewHolder.itemView.findViewById<RelativeLayout>(R.id.fragment_bookmark_foreground)
            getDefaultUIUtil().clearView(foregroundView)
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
