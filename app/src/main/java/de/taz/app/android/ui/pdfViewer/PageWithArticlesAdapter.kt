
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.EmptySignature
import com.bumptech.glide.signature.ObjectKey
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.pdfViewer.ArticleAdapter
import de.taz.app.android.ui.pdfViewer.PageWithArticles
import de.taz.app.android.ui.pdfViewer.PageWithArticlesListItem
import kotlinx.coroutines.flow.Flow

const val TYPE_PAGE = 0
private const val TYPE_IMPRINT = 1

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
    val pages: List<PageWithArticlesListItem>,
    private val onPageCLick: (pageName: String) -> Unit,
    private val onArticleClick: (pagePosition: Int, article: ArticleOperations) -> Unit,
    private val onArticleBookmarkClick: (article: ArticleOperations) -> Unit,
    private val onAudioEnqueueClick: (article: ArticleOperations, isEnqueued: Boolean?) -> Unit,
    private val articleBookmarkStateFlowCreator: (article: ArticleOperations) -> Flow<Boolean>,
) :
    RecyclerView.Adapter<ViewHolder>() {

    var activePosition = 0

    private class PageWithArticlesHolder(
        view: View,
        private val onPageCLick: (pageName: String) -> Unit,
        private val onArticleClick: (pagePosition: Int, article: ArticleOperations) -> Unit,
        private val onArticleBookmarkClick: (article: ArticleOperations) -> Unit,
        private val onAudioEnqueueClick: (article: ArticleOperations, isEnqueued: Boolean) -> Unit,
        private val articleBookmarkStateFlowCreator: (article: ArticleOperations) -> Flow<Boolean>,
        private val showDivider: Boolean
    ) :
        ViewHolder(view) {

        private val layout: ConstraintLayout = itemView.findViewById(R.id.list_item_pdf_page_with_content)
        private val sectionGroup: Group = itemView.findViewById(R.id.page_title_group)
        private val sectionTitle: TextView = itemView.findViewById(R.id.preview_page_title)
        private val sectionTitleWrapper: ConstraintLayout = itemView.findViewById(R.id.preview_page_title_wrapper)
        private val pagePreviewImage: ImageView = itemView.findViewById(R.id.preview_page_image)
        private val pagePreviewPagina: TextView = itemView.findViewById(R.id.preview_page_pagina)
        private val pageTocRecyclerView: RecyclerView =
            itemView.findViewById(R.id.toc_recycler_view)
        private val pageDivider: View = itemView.findViewById(R.id.page_divider)

        /**
         * Bind data that should be displayed in the item view.
         *
         * @param page Page with articles (TOC) to be displayed.
         */
        fun bind(
            page: PageWithArticles,
            sameSectionTitle: Boolean,
            position: Int,
            activePosition: Int
        ) {
            val storageService = StorageService.getInstance(itemView.context)
            val signature = page.pagePdf.dateDownload
                ?.let { ObjectKey(it.time) }
                ?: EmptySignature.obtain()
            Glide
                .with(itemView)
                .load(storageService.getAbsolutePath(page.pagePdf))
                .signature(signature)
                .into(pagePreviewImage)

            pagePreviewImage.setOnClickListener {
                onPageCLick(page.pagePdf.name)
            }
            if (sameSectionTitle || BuildConfig.IS_LMD) {
                sectionGroup.visibility = View.GONE
            } else {
                sectionTitle.text = page.title
                sectionTitleWrapper.setOnClickListener {
                    onPageCLick(page.pagePdf.name)
                }
            }
            if (page.pagina == null) {
                pagePreviewPagina.visibility = View.GONE
            } else {
                pagePreviewPagina.text = itemView.context.getString(
                    R.string.fragment_header_article_pagina,
                    page.pagina.split('-')[0]
                )
            }

            pageTocRecyclerView.layoutManager = LinearLayoutManager(itemView.context)
            pageTocRecyclerView.adapter = page.articles?.distinct()?.let {
                ArticleAdapter(
                    it,
                    { article -> onArticleClick(absoluteAdapterPosition, article) },
                    { article -> onArticleBookmarkClick(article) },
                    { article, isEnqueued -> onAudioEnqueueClick(article, isEnqueued) },
                    articleBookmarkStateFlowCreator
                )
            }

            if (showDivider) {
                pageDivider.visibility = View.VISIBLE
            }

            if (position == activePosition) {
                layout.setBackgroundColor(
                    ContextCompat.getColor(
                        itemView.context,
                        R.color.pdf_drawer_sections_item_pdf_page_with_content_highlighted
                    )
                )
            } else {
                layout.setBackgroundColor(
                    ContextCompat.getColor(
                        itemView.context,
                        R.color.navigation_pdf_drawer_list_background
                    )
                )
            }
        }
    }

    private class ImprintHolder(
        view: View,
        private val onArticleClick: (pagePosition: Int, article: ArticleOperations) -> Unit,
    ) : ViewHolder(view) {

        private val imprintTitle: TextView = itemView.findViewById(R.id.imprint_title)

        fun bind(imprint: ArticleOperations) {
            imprintTitle.text = imprint.title
            itemView.setOnClickListener {
                onArticleClick(absoluteAdapterPosition, imprint)
            }
        }
    }


    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        return when (viewType) {
            TYPE_PAGE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_pdf_page_with_content, parent, false)
                PageWithArticlesHolder(
                    view,
                    onPageCLick,
                    onArticleClick,
                    onArticleBookmarkClick,
                    onAudioEnqueueClick,
                    articleBookmarkStateFlowCreator,
                    showDivider = true
                )
            }

            TYPE_IMPRINT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_pdf_page_imprint, parent, false)
                ImprintHolder(view, onArticleClick)
            }

            else -> error("Unknown viewType type: $viewType")
        }
    }

    override fun getItemCount() = pages.size

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        val listItem = pages[position]
        val samePageTitle = isSameTitleAsOnBeforePage(position)
        when (holder.itemViewType) {
            TYPE_PAGE -> (holder as PageWithArticlesHolder).bind(
                (listItem as PageWithArticlesListItem.Page).page,
                samePageTitle,
                position,
                activePosition
            )

            TYPE_IMPRINT -> (holder as ImprintHolder).bind((listItem as PageWithArticlesListItem.Imprint).imprint)
            else -> error("Unknown ViewHolder type: ${holder::class.java.simpleName}")
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (pages[position]) {
            is PageWithArticlesListItem.Page -> TYPE_PAGE
            is PageWithArticlesListItem.Imprint -> TYPE_IMPRINT
        }
    }
    private fun isSameTitleAsOnBeforePage(position: Int): Boolean {
        return if (position == 0) {
            false
        } else {
            val thisTitle = (pages[position] as? PageWithArticlesListItem.Page)?.page?.title
            val beforeTitle = (pages[position - 1] as? PageWithArticlesListItem.Page)?.page?.title
            thisTitle == beforeTitle
        }
    }
}