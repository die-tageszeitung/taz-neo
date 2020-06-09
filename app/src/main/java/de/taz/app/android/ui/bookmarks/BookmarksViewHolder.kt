package de.taz.app.android.ui.bookmarks

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
import de.taz.app.android.singletons.FileHelper

class BookmarksViewHolder(
    private val bookmarksFragment: BookmarksFragment,
    parent: ViewGroup
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
    private val fileHelper = FileHelper.getInstance(parent.context.applicationContext)
    private val dateHelper: DateHelper = DateHelper.getInstance(parent.context.applicationContext)
    private val bookmarksAdapter = BookmarksAdapter(bookmarksFragment)
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
            bookmarkDate?.text = dateHelper.dateToLowerCaseString(article.issueDate)

            if (article.imageList.isNotEmpty()) {
                fileHelper.getFile(article.imageList.first()).apply {
                    if (exists()) {

                        val bitmapOptions = BitmapFactory.Options()
                        bitmapOptions.inSampleSize =
                            (4 / itemView.resources.displayMetrics.density).toInt()

                        val myBitmap = BitmapFactory.decodeFile(absolutePath, bitmapOptions)
                        bookmarkImage?.apply {
                            setImageBitmap(myBitmap)
                            visibility = View.VISIBLE
                        }
                    }
                }
            }

            bookmarkTitle?.text = article.title
            bookmarkBox?.setOnClickListener {
                bookmarksFragment.showInWebView(article.key, bookmarksArticle = true)
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