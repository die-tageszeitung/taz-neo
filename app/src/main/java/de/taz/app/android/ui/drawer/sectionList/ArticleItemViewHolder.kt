package de.taz.app.android.ui.drawer.sectionList

import android.text.Spannable
import android.text.SpannableString
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

    private var titleTextView: TextView? = null
    private var teaserTextView: TextView? = null
    private var authorAndReadMinutesTextView: TextView? = null
    private var articleImageView: ImageView? = null
    private var bookmarkIconImageView: ImageView? = null
    private val fileHelper = StorageService.getInstance(parent.context.applicationContext)

    init {
        titleTextView = itemView.findViewById(R.id.fragment_drawer_article_title)
        teaserTextView = itemView.findViewById(R.id.fragment_drawer_article_teaser)
        authorAndReadMinutesTextView = itemView.findViewById(R.id.fragment_drawer_article_author_and_read_minutes)
        articleImageView = itemView.findViewById(R.id.fragment_drawer_article_image)
        bookmarkIconImageView = itemView.findViewById(R.id.fragment_drawer_article_bookmark_icon)
    }

    fun bind(sectionDrawerItem: SectionDrawerItem.Item) {
        val article = sectionDrawerItem.article
        titleTextView?.text = article.title

        if (article.teaser.isNullOrBlank()) {
            teaserTextView?.visibility = View.GONE
        } else {
            teaserTextView?.text = article.teaser
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

        authorAndReadMinutesTextView?.setText(twoStyledSpannable, TextView.BufferType.SPANNABLE)

        if (article.imageList.isNotEmpty()) {
            fileHelper.getAbsolutePath(article.imageList.first())?.let {
                if (File(it).exists()) {
                    articleImageView?.let { imageView ->
                        Glide.with(itemView.context.applicationContext)
                            .load(it)
                            .apply(RequestOptions().override(imageView.width, imageView.height))
                            .into(imageView)
                    }
                }
            }
        } else {
            articleImageView?.setImageBitmap(null)
            articleImageView?.visibility = View.GONE
        }
        bookmarkIconImageView?.setOnClickListener {
            onBookmarkClick(article)
        }
        itemView.setOnClickListener {
            onArticleClick(article)
        }

        launch {
            getBookmarkStateFlow(article.key).collect {isBookmarked ->
                if (isBookmarked) {
                    bookmarkIconImageView?.setImageResource(R.drawable.ic_bookmark_filled)
                } else {
                    bookmarkIconImageView?.setImageResource(R.drawable.ic_bookmark)
                }
            }
        }
    }

    private fun constructAuthorsAndReadMinutesSpannable(authors: String, readMinutes: String): SpannableString {

        val authorsAndReadMinutesString = "$authors $readMinutes"
        val text = SpannableString(authorsAndReadMinutesString)

        text.setSpan(
            TextAppearanceSpan(itemView.context, R.style.TextAppearance_Bookmarks_Entry_Author),
            0,
            authors.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        text.setSpan(
            TextAppearanceSpan(itemView.context, R.style.TextAppearance_Bookmarks_Entry_ReadMinutes),
            authors.length+1,
            authorsAndReadMinutesString.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return text
    }
}