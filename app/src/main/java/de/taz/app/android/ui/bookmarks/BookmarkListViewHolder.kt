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
import de.taz.app.android.TazApplication
import de.taz.app.android.api.models.Article
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.StorageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private var bookmarkAuthor: TextView? = null
    private var bookmarkDate: TextView? = null
    private var bookmarkImage: ImageView? = null
    private var bookmarkShare: ImageView
    private var bookmarkDelete: ImageView
    private val fileHelper = StorageService.getInstance(parent.context.applicationContext)
    private val bookmarksAdapter = BookmarkListAdapter(bookmarksFragment, bookmarksFragment.applicationScope)
    private val issueRepository = IssueRepository.getInstance(parent.context.applicationContext)
    private var bookmarks: MutableList<Article> = emptyList<Article>().toMutableList()

    init {
        bookmarkBox = itemView.findViewById(R.id.fragment_bookmark)
        bookmarkTitle = itemView.findViewById(R.id.fragment_bookmark_title)
        bookmarkAuthor = itemView.findViewById(R.id.fragment_bookmark_author)
        bookmarkDate = itemView.findViewById(R.id.fragment_bookmark_date)
        bookmarkImage = itemView.findViewById(R.id.fragment_bookmark_image)
        bookmarkShare = itemView.findViewById(R.id.fragment_bookmark_share)
        bookmarkDelete = itemView.findViewById(R.id.fragment_bookmark_delete)
    }

    fun bind(article: Article) {
        article.let {

            CoroutineScope(Dispatchers.Main).launch {
                bookmarkDate?.text = setDateText(article)
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
            }

            bookmarkTitle?.text = article.title
            // get the author(s) from the article
            val authorList = article.authorList.map { it.name }.distinct()
            val authorString = authorList.toString()
                .replace("[", "")
                .replace("]", "")
            bookmarkAuthor?.text = authorString
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
                bookmarksAdapter.removeBookmarkWithUndo(
                    this,
                    bindingAdapterPosition,
                    bookmarks
                )
            }
        }
    }

    fun setBookmarks(bookmarks: MutableList<Article>) {
        this.bookmarks = bookmarks
    }

    private suspend fun setDateText(article: Article): String? {
        val issueStub = issueRepository.getIssueStubForArticle(article.articleHtml.name)
        return if (issueStub?.isWeekend == true && !issueStub.validityDate.isNullOrBlank()) {
            val formattedDate = DateHelper.stringToDate(issueStub.date)
            DateHelper.dateToWeekNotation(formattedDate, issueStub.validityDate)
        } else {
            DateHelper.dateToLowerCaseString(article.issueDate)
        }

    }
}