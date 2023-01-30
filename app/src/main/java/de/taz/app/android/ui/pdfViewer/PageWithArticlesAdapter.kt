import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.pdfViewer.ArticleAdapter
import de.taz.app.android.ui.pdfViewer.PageWithArticles
import kotlinx.coroutines.flow.Flow

/**
 * Creates ViewHolders for item views that will bind data for the preview of a page and the table of content of this
 * page.
 *
 * @property pages The pages that should be displayed in the item views.
 * @property onPageCLick Callback that should be triggered, when user clicks on page preview.
 * @property onArticleClick Callback that should be triggered, when user clicks on article in the
 * table of content.
 */
class PageWithArticlesAdapter(
    var pages: List<PageWithArticles>,
    private val onPageCLick: (position: Int) -> Unit,
    private val onArticleClick: (pagePosition: Int, article: Article) -> Unit,
    private val onArticleBookmarkClick: (article: Article) -> Unit,
    private val articleBookmarkStateFlowCreator: (article: Article) -> Flow<Boolean>,
) :
    RecyclerView.Adapter<PageWithArticlesAdapter.PageWithArticlesHolder>() {

    inner class PageWithArticlesHolder(
        view: View,
        val onPageCLick: (position: Int) -> Unit,
        val onArticleClick: (pagePosition: Int, article: Article) -> Unit,
        val onArticleBookmarkClick: (article: Article) -> Unit,
    ) :
        RecyclerView.ViewHolder(view) {

        private lateinit var page: PageWithArticles

        private val pagePreviewImage: ImageView = itemView.findViewById(R.id.preview_page_image)
        private val pageTocRecyclerView: RecyclerView =
            itemView.findViewById(R.id.toc_recycler_view)

        /**
         * Bind data that should be displayed in the item view.
         *
         * @param page Page with articles (TOC) to be displayed.
         */
        fun bind(page: PageWithArticles) {
            this.page = page
            val storageService = StorageService.getInstance(itemView.context)
            Glide
                .with(itemView)
                .load(page
                    ?.let {
                        storageService.getAbsolutePath(it.pagePdf)
                    }).into(pagePreviewImage)
            pagePreviewImage.setOnClickListener {
                onPageCLick(absoluteAdapterPosition)
            }
            pageTocRecyclerView.layoutManager = LinearLayoutManager(itemView.context)
            pageTocRecyclerView.adapter = this.page.articles?.let {
                ArticleAdapter(
                    it,
                    { article -> onArticleClick(absoluteAdapterPosition, article) },
                    { article -> onArticleBookmarkClick(article) },
                    articleBookmarkStateFlowCreator
                )
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): PageWithArticlesHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_pdf_page_with_content, parent, false)
        return PageWithArticlesHolder(view, onPageCLick, onArticleClick, onArticleBookmarkClick)
    }

    override fun getItemCount() = pages.size

    override fun onBindViewHolder(
        holder: PageWithArticlesHolder,
        position: Int
    ) {
        val pdfPage = pages[position]
        holder.bind(pdfPage)
    }

}