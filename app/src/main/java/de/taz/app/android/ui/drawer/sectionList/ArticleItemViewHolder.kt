package de.taz.app.android.ui.drawer.sectionList

import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.LineHeightSpan
import android.text.style.TextAppearanceSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.singletons.StorageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.CoroutineContext


class ArticleItemViewHolder(
    parent: ViewGroup,
    private val onArticleClick: (Article) -> Unit,
    private val onBookmarkClick: (Article) -> Unit,
    private val getBookmarkStateFlow: (String) -> Flow<Boolean>
) :
    RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.fragment_drawer_article_item,
            parent,
            false
        )
    ), CoroutineScope {

    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Main

    private var titleTextView: TextView = itemView.findViewById(R.id.fragment_drawer_article_title)
    private var teaserTextView: TextView = itemView.findViewById(R.id.fragment_drawer_article_teaser)
    private var authorAndReadMinutesTextView: TextView = itemView.findViewById(R.id.fragment_drawer_article_author_and_read_minutes)
    private var articleImageView: ImageView = itemView.findViewById(R.id.fragment_drawer_article_image)
    private var bookmarkIconImageView: ImageView = itemView.findViewById(R.id.fragment_drawer_article_bookmark_icon)

    private val fileHelper = StorageService.getInstance(parent.context.applicationContext)

    fun bind(sectionDrawerItem: SectionDrawerItem.Item) {
        val article = sectionDrawerItem.article
        titleTextView.text = article.title

        if (article.teaser.isNullOrBlank()) {
            teaserTextView.visibility = View.GONE
        } else {
            teaserTextView.visibility = View.VISIBLE
            teaserTextView.text = article.teaser
        }

        // get the author(s) from the article
        val authorsString = if (article.authorList.isNotEmpty()) {
            val authorList = article.authorList.map { it.name }.distinct()
            authorList.joinToString(", ")
        } else {
            ""
        }
        val readMinutesString = if (article.readMinutes != null) {
            itemView.context.getString(
                R.string.read_minutes,
                article.readMinutes
            )
        } else {
            ""
        }
        val twoStyledSpannable = constructAuthorsAndReadMinutesSpannable(authorsString, readMinutesString)

        authorAndReadMinutesTextView.setText(twoStyledSpannable, TextView.BufferType.SPANNABLE)

        if (article.imageList.isNotEmpty()) {
            fileHelper.getAbsolutePath(article.imageList.first())?.let {
                if (File(it).exists()) {
                    articleImageView.visibility = View.VISIBLE
                    articleImageView.let { imageView ->
                        Glide.with(itemView.context.applicationContext)
                            .load(it)
                            .apply(RequestOptions().override(imageView.width, imageView.height))
                            .into(imageView)
                    }
                }
            }
        } else {
            articleImageView.setImageBitmap(null)
            articleImageView.visibility = View.GONE
        }
        bookmarkIconImageView.setOnClickListener {
            onBookmarkClick(article)
        }
        itemView.setOnClickListener {
            onArticleClick(article)
        }

        launch {
            getBookmarkStateFlow(article.key).collect {isBookmarked ->
                if (isBookmarked) {
                    bookmarkIconImageView.setImageResource(R.drawable.ic_bookmark_filled)
                } else {
                    bookmarkIconImageView.setImageResource(R.drawable.ic_bookmark)
                }
            }
        }
    }

    private fun constructAuthorsAndReadMinutesSpannable(authors: String, readMinutes: String): SpannableString {
        val authorsAndReadMinutesString: String
        val authorSpanStart: Int
        val authorSpanEnd: Int
        val readMinutesSpanStart: Int
        val readMinutesSpanEnd: Int

        when {
            readMinutes.isBlank() && authors.isBlank() -> {
                authorsAndReadMinutesString = ""
                authorSpanStart = 0
                authorSpanEnd = 0
                readMinutesSpanStart = 0
                readMinutesSpanEnd = 0
            }
            readMinutes.isBlank() -> {
                authorsAndReadMinutesString = authors
                authorSpanStart = 0
                authorSpanEnd = authors.length
                readMinutesSpanStart = 0
                readMinutesSpanEnd = 0
            }
            authors.isBlank() -> {
                authorsAndReadMinutesString = readMinutes
                authorSpanStart = 0
                authorSpanEnd = 0
                readMinutesSpanStart = 0
                readMinutesSpanEnd = readMinutes.length
            }
            else -> {
                authorsAndReadMinutesString = "$authors $readMinutes"
                authorSpanStart = 0
                authorSpanEnd = authors.length
                readMinutesSpanStart = authorSpanEnd + 1
                readMinutesSpanEnd = readMinutesSpanStart + readMinutes.length
            }
        }

        val text = SpannableString(authorsAndReadMinutesString)

        if (authorSpanStart < authorSpanEnd) {
            text.setSpan(
                TextAppearanceSpan(itemView.context, R.style.TextAppearance_Bookmarks_Entry_Author),
                0,
                authors.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        if (readMinutesSpanStart < readMinutesSpanEnd) {
            text.setSpan(
                TextAppearanceSpan(
                    itemView.context,
                    R.style.TextAppearance_Bookmarks_Entry_ReadMinutes
                ),
                readMinutesSpanStart,
                readMinutesSpanEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val newLineHeight =
                    itemView.context.applicationContext.resources.getDimensionPixelSize(
                        R.dimen.fragment_bookmarks_article_item_read_minutes_line_height
                    )
                text.setSpan(
                    LineHeightSpan.Standard(newLineHeight),
                    readMinutesSpanStart,
                    readMinutesSpanEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        return text
    }
}