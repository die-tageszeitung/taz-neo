package de.taz.app.android.ui.bookmarks

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.webview.pager.ArticleBottomActionBarNavigationHelper
import java.io.File

class BookmarkListViewHolder(
    parent: ViewGroup,
    private val isSeparatorLineDotted: Boolean,
    private val shareArticle: (Article) -> Unit,
    private val removeBookmarkWithUndo: (View, Int) -> Unit
) :
    RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.fragment_bookmarks_article_item,
            parent,
            false
        )
    ) {

    private var bookmarkBox: ConstraintLayout? = null
    private var bookmarkTitle: TextView? = null
    private var bookmarkAuthor: TextView? = null
    private var bookmarkTeaser: TextView? = null
    private var bookmarkReadMinutes: TextView? = null
    private var bookmarkImage: ImageView? = null
    private var bookmarkShare: ImageView
    private var bookmarkDelete: ImageView
    private var bookmarkSeparatorLineDotted: ImageView
    private var bookmarkSeparatorLineSolid: View
    private val fileHelper = StorageService.getInstance(parent.context.applicationContext)

    init {
        bookmarkBox = itemView.findViewById(R.id.fragment_bookmark)
        bookmarkTitle = itemView.findViewById(R.id.fragment_bookmark_title)
        bookmarkAuthor = itemView.findViewById(R.id.fragment_bookmark_author)
        bookmarkTeaser = itemView.findViewById(R.id.fragment_bookmark_teaser)
        bookmarkReadMinutes = itemView.findViewById(R.id.fragment_bookmark_read_minutes)
        bookmarkImage = itemView.findViewById(R.id.fragment_bookmark_image)
        bookmarkShare = itemView.findViewById(R.id.fragment_bookmark_share_touch_area)
        bookmarkDelete = itemView.findViewById(R.id.fragment_bookmark_delete_touch_area)
        bookmarkSeparatorLineDotted = itemView.findViewById(R.id.fragment_bookmark_separator_line_dotted)
        bookmarkSeparatorLineSolid = itemView.findViewById(R.id.fragment_bookmark_separator_line_solid)

        if (!isSeparatorLineDotted) {
            bookmarkSeparatorLineDotted.visibility = View.GONE
            bookmarkSeparatorLineSolid.visibility = View.VISIBLE
        } else {
            bookmarkSeparatorLineDotted.visibility = View.VISIBLE
            bookmarkSeparatorLineSolid.visibility = View.GONE
        }
    }

    fun bind(item: BookmarkListItem.Item) {
        item.bookmark.let { article ->
            bookmarkTeaser?.text = article.teaser

            if (article.readMinutes != null) {
                bookmarkReadMinutes?.text = itemView.context.getString(
                    R.string.read_minutes,
                    article.readMinutes
                )
            } else {
                bookmarkReadMinutes?.text = ""
            }

            if (article.imageList.isNotEmpty()) {
                fileHelper.getAbsolutePath(article.imageList.first())?.let {
                    if (File(it).exists()) {

                        val bitmapOptions = BitmapFactory.Options()
                        bitmapOptions.inSampleSize =
                            (4 / itemView.resources.displayMetrics.density).toInt()

                        val myBitmap = BitmapFactory.decodeFile(it, bitmapOptions)
                        bookmarkImage?.setImageBitmap(myBitmap)
                    }
                }
            } else {
                bookmarkImage?.setImageBitmap(null)
            }

            bookmarkTitle?.text = article.title

            // get the author(s) from the article
            val authorList = article.authorList.map { it.name }.distinct()
            if (authorList.isNotEmpty()) {
                bookmarkAuthor?.apply {
                    visibility = View.VISIBLE
                    text = authorList.joinToString(", ")
                }
            } else {
                // Dont show an empty author textview to prevent some paddings on the readminutes
                bookmarkAuthor?.visibility = View.GONE
            }

            bookmarkBox?.setOnClickListener {
                val intent = BookmarkViewerActivity.newIntent(itemView.context, article.key)
                itemView.context.startActivity(intent)
            }


            if (ArticleBottomActionBarNavigationHelper.shouldShareIconBeVisible(article)) {
                bookmarkShare.isVisible = true
                bookmarkShare.setOnClickListener {
                    shareArticle(article)
                }
            } else {
                bookmarkShare.isVisible = false
            }

            bookmarkDelete.setOnClickListener {
                removeBookmarkWithUndo(
                    this.itemView,
                    bindingAdapterPosition
                )
            }
        }
    }
}