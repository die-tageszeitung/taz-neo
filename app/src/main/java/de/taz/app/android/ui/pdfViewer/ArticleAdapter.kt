package de.taz.app.android.ui.pdfViewer

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
    ) : RecyclerView.ViewHolder(view), CoroutineScope {

        override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Main


        private val articleTitle: TextView = itemView.findViewById(R.id.article_title)
        private val articleTeaser: TextView = itemView.findViewById(R.id.article_teaser)
        private val articleAuthors: TextView = itemView.findViewById(R.id.article_authors)
        private val articleIsBookmarked: ImageView =
            itemView.findViewById(R.id.article_is_bookmarked)

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
            if (article.authorList.isNotEmpty()) {
                articleAuthors.visibility = View.VISIBLE
                articleAuthors.text =
                    itemView.context.getString(
                        R.string.author_list,
                        article.authorList.map { it.name }.distinct().joinToString(", ")
                    )
            } else {
                articleAuthors.visibility = View.GONE
            }

            itemView.setOnClickListener {
                onArticleClick(article)
            }

            articleIsBookmarked.setOnClickListener {
                onArticleBookmarkClick(article)
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

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_pdf_page_toc, parent, false)
        return ArticleHolder(view, onArticleClick)
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
}