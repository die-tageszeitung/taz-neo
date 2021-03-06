package de.taz.app.android.ui.bookmarks

import android.content.Intent
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.StorageService
import java.io.File

class BookmarkListViewHolder(
    private val bookmarksFragment: BookmarkListFragment,
    val parent: ViewGroup
) :
    RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.fragment_bookmarks_recyclerview,
            parent,
            false
        )
    ) {

    private var bookmarkBox: ConstraintLayout? = null
    private var bookmarkTitle: TextView? = null
    private var bookmarkDate: TextView? = null
    private var bookmarkImage: ImageView? = null
    private var bookmarkShare: ImageView
    private var bookmarkDelete: ImageView
    private val fileHelper = StorageService.getInstance(parent.context.applicationContext)
    private val bookmarksAdapter = BookmarkListAdapter(bookmarksFragment)
    private var bookmarks: MutableList<Article> = emptyList<Article>().toMutableList()

    init {
        bookmarkBox = itemView.findViewById(R.id.fragment_bookmark)
        bookmarkTitle = itemView.findViewById(R.id.fragment_bookmark_title)
        bookmarkDate = itemView.findViewById(R.id.fragment_bookmark_date)
        bookmarkImage = itemView.findViewById(R.id.fragment_bookmark_image)
        bookmarkShare = itemView.findViewById(R.id.fragment_bookmark_share)
        bookmarkDelete = itemView.findViewById(R.id.fragment_bookmark_delete)
    }

    fun bind(article: Article) {
        bookmarkImage?.visibility = View.GONE
        article.let {
            bookmarkDate?.text = DateHelper.dateToLowerCaseString(article.issueDate)

            if (article.imageList.isNotEmpty()) {
                fileHelper.getAbsolutePath(article.imageList.first())?.let {
                    if (File(it).exists()) {

                        val bitmapOptions = BitmapFactory.Options()
                        bitmapOptions.inSampleSize =
                            (4 / itemView.resources.displayMetrics.density).toInt()

                        val myBitmap = BitmapFactory.decodeFile(it, bitmapOptions)
                        bookmarkImage?.apply {
                            setImageBitmap(myBitmap)
                            visibility = View.VISIBLE
                        }
                    }
                }
            }

            bookmarkTitle?.text = article.title
            bookmarkBox?.setOnClickListener {
                Intent(parent.context, BookmarkViewerActivity::class.java).apply {
                    putExtra(BookmarkViewerActivity.KEY_SHOWN_ARTICLE, article.key)
                    parent.context.startActivity(this)
                }
            }

            bookmarkShare.setOnClickListener {
                bookmarksFragment.shareArticle(article.key)
            }

            bookmarkDelete.setOnClickListener {
                bookmarksAdapter.removeBookmarkWithUndo(this, adapterPosition, bookmarks)
            }
        }
    }

    fun setBookmarks(bookmarks: MutableList<Article>) {
        this.bookmarks = bookmarks
    }
}