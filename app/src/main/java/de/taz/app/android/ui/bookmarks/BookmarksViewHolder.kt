package de.taz.app.android.ui.bookmarks

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.util.DateHelper
import de.taz.app.android.util.FileHelper

class BookmarksViewHolder(
    private val bookmarksPresenter: BookmarksContract.Presenter,
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
    private val fileHelper = FileHelper.getInstance()
    private val dateHelper: DateHelper = DateHelper.getInstance()

    init {
        bookmarkBox = itemView.findViewById(R.id.fragment_bookmark)
        bookmarkTitle = itemView.findViewById(R.id.fragment_bookmark_title)
        bookmarkDate = itemView.findViewById(R.id.fragment_bookmark_date)
        bookmarkImage = itemView.findViewById(R.id.fragment_bookmark_image)
        bookmarkShare = itemView.findViewById(R.id.fragment_bookmark_share)
        bookmarkDelete = itemView.findViewById(R.id.fragment_bookmark_delete)
    }

    fun bind(article: Article) {
        article.let {
            bookmarkDate?.text = dateHelper.dateToLowerCaseString(article.date)

            if (article.imageList.isNotEmpty()) {
                val imgFile = fileHelper.getFile("taz/${article.date}/${article.imageList.first().name}")
                if (imgFile.exists()) {
                    val myBitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                    bookmarkImage?.setImageBitmap(myBitmap)
                }
            }

            bookmarkTitle?.text = article.title
            bookmarkBox?.setOnClickListener {
                bookmarksPresenter.openArticle(article)
            }

            bookmarkShare.setOnClickListener {
                bookmarksPresenter.shareArticle(article)
            }

            bookmarkDelete.setOnClickListener {
                bookmarksPresenter.debookmarkArticle(article)
            }
        }
    }
}