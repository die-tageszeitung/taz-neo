package de.taz.app.android.ui.bookmarks

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.coachMarks.BookmarksSwipeCoachMark
import de.taz.app.android.util.Log
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TYPE_HEADER = 0
private const val TYPE_ITEM = 1
private const val TYPE_ITEM_LAST_IN_ISSUE = 2


class BookmarkListAdapter(
    private val shareArticle: (Article) -> Unit,
    private val bookmarkArticle: (Article) -> Unit,
    private val debookmarkArticle: (Article) -> Unit,
    private val goToIssueInCoverFlow: (String) -> Unit,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var groupedBookmarks: List<BookmarkListItem> = emptyList()
    private val log by Log

    fun setData(bookmarks: List<BookmarkListItem>) {
        groupedBookmarks = bookmarks.toMutableList()
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        val viewType: Int
        val isHeader = groupedBookmarks[position] is BookmarkListItem.Header
        val isLastItemInIssue = (position == groupedBookmarks.size - 1) ||
                (groupedBookmarks[position] is BookmarkListItem.Item && groupedBookmarks[position + 1] is BookmarkListItem.Header)

        viewType = if (isHeader) {
            TYPE_HEADER
        } else if (isLastItemInIssue) {
            TYPE_ITEM_LAST_IN_ISSUE
        } else {
            TYPE_ITEM
        }

        return viewType
    }

    private fun restoreBookmark(item: BookmarkListItem.Item) {
        bookmarkArticle(item.bookmark)
    }

    private fun removeBookmark(item: BookmarkListItem.Item) {
        debookmarkArticle(item.bookmark)
    }

    fun removeBookmarkWithUndo(
        view: View,
        position: Int
    ) {
        val article = groupedBookmarks.getOrNull(position) as? BookmarkListItem.Item

        if (article == null) {
            val msg = "Something went wrong. Could not restore bookmark at position $position."
            log.error(msg)
            Sentry.captureMessage(msg)
            return
        }

        removeBookmark(article)
        // showing snack bar with undo option
        Snackbar.make(
            view,
            R.string.fragment_bookmarks_deleted,
            Snackbar.LENGTH_LONG
        ).apply {
            setAction(R.string.fragment_bookmarks_undo) {
                restoreBookmark(article)
            }
            setActionTextColor(
                ResourcesCompat.getColor(
                    view.context.resources,
                    R.color.fragment_bookmarks_delete_background,
                    null
                )
            )
            show()
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {

        return when (viewType) {
            TYPE_HEADER -> IssueOfBookmarkViewHolder(parent, goToIssueInCoverFlow)
            TYPE_ITEM -> BookmarkListViewHolder(parent, true, shareArticle, ::removeBookmarkWithUndo)
            TYPE_ITEM_LAST_IN_ISSUE -> BookmarkListViewHolder(parent, false, shareArticle, ::removeBookmarkWithUndo)
            else -> error("Unknown viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is IssueOfBookmarkViewHolder -> {
                // do some Header things
                val issueMoment = groupedBookmarks[position] as BookmarkListItem.Header
                holder.bind(issueMoment)
            }
            is BookmarkListViewHolder -> {
                val article = groupedBookmarks[position] as BookmarkListItem.Item
                holder.bind(article)
            }
        }
    }

    override fun getItemCount() = groupedBookmarks.size

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        0,
        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, swipeDir: Int) {
            if (viewHolder is BookmarkListViewHolder) {
                val position = viewHolder.bindingAdapterPosition
                removeBookmarkWithUndo(viewHolder.itemView, position)
                CoroutineScope(Dispatchers.Default).launch {
                    BookmarksSwipeCoachMark.setFunctionAlreadyDiscovered(viewHolder.itemView.context)
                }
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
            if (viewHolder is BookmarkListViewHolder) {
                val foregroundView =
                    viewHolder.itemView.findViewById<ConstraintLayout>(R.id.fragment_bookmark_foreground)
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
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            if (viewHolder is BookmarkListViewHolder) {
                val foregroundView =
                    viewHolder.itemView.findViewById<ConstraintLayout>(R.id.fragment_bookmark_foreground)
                getDefaultUIUtil().clearView(foregroundView)
            }
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
            if (viewHolder is BookmarkListViewHolder) {
                val foregroundView =
                    viewHolder.itemView.findViewById<ConstraintLayout>(R.id.fragment_bookmark_foreground)

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
        }

    })

}