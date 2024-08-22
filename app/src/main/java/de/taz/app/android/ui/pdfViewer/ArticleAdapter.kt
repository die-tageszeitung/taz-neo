package de.taz.app.android.ui.pdfViewer

import android.text.Spannable
import android.text.SpannableString
import android.text.style.TextAppearanceSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

private const val TYPE_ARTICLE_BETWEEN = 0
private const val TYPE_ARTICLE_LAST = 1

/**
 * Create ViewHolders for item views that will bind data of an article.
 *
 * @property articles The articles that should be displayed in the item views.
 * @property onArticleClick Callback that should be triggered, when user clicks on an article
 */
class ArticleAdapter(
    var articles: List<Article>,
    private val onArticleClick: (article: Article) -> Unit,
    private val onArticleBookmarkClick: (article: Article) -> Unit,
    private val articleBookmarkStateFlowCreator: (article: Article) -> Flow<Boolean>,
) : RecyclerView.Adapter<ArticleAdapter.ArticleHolder>() {

    inner class ArticleHolder(
        view: View,
        private val onArticleClick: (article: Article) -> Unit,
        private val showDivider: Boolean
    ) : RecyclerView.ViewHolder(view), CoroutineScope {

        override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Main


        private val articleTitle: TextView = itemView.findViewById(R.id.article_title)
        private val articleTeaser: TextView = itemView.findViewById(R.id.article_teaser)
        private var articleAuthorAndReadMinutes: TextView =
            itemView.findViewById(R.id.article_author_and_read_minutes)
        private val articleIsBookmarked: ImageView =
            itemView.findViewById(R.id.article_is_bookmarked)
        private val articleDivider: View = itemView.findViewById(R.id.article_divider)

        /**
         * Bind data that should be displayed in the item view.
         *
         * @param article Article to be displayed.
         */
        fun bind(article: Article) {

            articleTitle.text = article.title
            if (!article.teaser.isNullOrBlank()) {
                articleTeaser.apply{
                    visibility = View.VISIBLE
                    text = article.teaser
                }
            } else {
                articleTeaser.visibility = View.GONE
            }

            val authorsString = if (article.authorList.isNotEmpty()) {
                itemView.context.getString(
                    R.string.author_list,
                    article.authorList.map { it.name }.distinct().joinToString(", ")
                )
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
            val twoStyledSpannable =
                constructAuthorsAndReadMinutesSpannable(authorsString, readMinutesString)

            articleAuthorAndReadMinutes.setText(twoStyledSpannable, TextView.BufferType.SPANNABLE)

            if (showDivider) {
                articleDivider.visibility = View.VISIBLE
            }

            itemView.setOnClickListener {
                onArticleClick(article)
            }

            if (article.isImprint()){
                articleIsBookmarked.visibility = View.GONE
            } else {
                articleIsBookmarked.visibility = View.VISIBLE
                articleIsBookmarked.setOnClickListener {
                    onArticleBookmarkClick(article)
                }
            }

            val articleBookmarkFlow = articleBookmarkStateFlowCreator(article)
            launch {
                articleBookmarkFlow.collect { isBookmarked ->
                    if (isBookmarked) {
                        articleIsBookmarked.setImageResource(R.drawable.ic_bookmark_filled)
                    } else {
                        articleIsBookmarked.setImageResource(R.drawable.ic_bookmark)
                    }
                }
            }
        }

        private fun constructAuthorsAndReadMinutesSpannable(
            authors: String,
            readMinutes: String
        ): SpannableString {
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
                    TextAppearanceSpan(
                        itemView.context,
                        R.style.TextAppearance_App_Drawer_Lmd_Meta_Author
                    ),
                    0,
                    authors.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            if (readMinutesSpanStart < readMinutesSpanEnd) {
                text.setSpan(
                    TextAppearanceSpan(
                        itemView.context,
                        R.style.TextAppearance_App_Drawer_Lmd_Meta_ReadMinutes
                    ),
                    readMinutesSpanStart,
                    readMinutesSpanEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            return text
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_pdf_page_toc, parent, false)
        return ArticleHolder(view, onArticleClick, showDivider = viewType == TYPE_ARTICLE_BETWEEN)
    }

    override fun getItemCount() = articles.size

    override fun onBindViewHolder(holder: ArticleHolder, position: Int) {
        holder.coroutineContext.cancelChildren()
        val article = articles[position]
        holder.bind(article)
    }

    override fun onViewRecycled(holder: ArticleHolder) {
        super.onViewRecycled(holder)
        holder.coroutineContext.cancelChildren()
    }

    override fun getItemViewType(position: Int): Int {
        if (position == itemCount - 1) {
            return TYPE_ARTICLE_LAST
        }
        return TYPE_ARTICLE_BETWEEN
    }
}