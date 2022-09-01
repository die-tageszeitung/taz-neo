package de.taz.app.android.ui.pdfViewer

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.taz.app.android.ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.webview.ImprintWebViewFragment
import de.taz.app.android.ui.webview.pager.ArticlePagerFragment
import kotlinx.coroutines.launch

/**
 * Fragment used in the drawer to display the currently selected page and the content/articles
 * of an issue.
 */
class DrawerBodyPdfWithSectionsFragment : Fragment() {

    private lateinit var currentPageImageView: ImageView
    private lateinit var currentPageTitleView: TextView
    private lateinit var issueTitleTextView: TextView
    private lateinit var loadingScreenConstraintLayout: ConstraintLayout

    private lateinit var navigationPageArticleRecyclerView: RecyclerView
    private lateinit var storageService: StorageService

    private var adapter: PageWithArticlesAdapter? =
        PageWithArticlesAdapter(
            emptyList(),
            { position -> handlePageClick(position) },
            { article -> handleArticleClick(article) })

    private val pdfPagerViewModel: PdfPagerViewModel by activityViewModels()
    private val issueContentViewModel: IssueViewerViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        storageService = StorageService.getInstance(requireContext().applicationContext)
        val view =
            inflater.inflate(R.layout.fragment_drawer_body_pdf_with_sections, container, false)

        currentPageImageView =
            view.findViewById(R.id.fragment_drawer_body_pdf_with_sections_current_page_image)
        currentPageTitleView =
            view.findViewById(R.id.fragment_drawer_body_pdf_with_sections_current_page_title)
        issueTitleTextView = view.findViewById(R.id.fragment_drawer_body_pdf_with_sections_title)
        loadingScreenConstraintLayout = view.findViewById(R.id.pdf_drawer_loading_screen)

        navigationPageArticleRecyclerView =
            view.findViewById(R.id.navigation_page_article_recycler_view)
        navigationPageArticleRecyclerView.layoutManager = LinearLayoutManager(context)
        navigationPageArticleRecyclerView.adapter = adapter

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pdfPagerViewModel.currentPage.observe(viewLifecycleOwner) {
            pdfPagerViewModel.currentItem.value?.let { _ ->
                refreshCurrentPage()
            }
        }
        pdfPagerViewModel.issuePublication.observe(viewLifecycleOwner) { issue_publication ->
            val date = DateHelper.stringToDate(issue_publication.date)
            val dateString = date?.let { DateHelper.dateToMonthYearString(it) }

            issueTitleTextView.text = getString(
                R.string.issue_title,
                dateString
            )
        }
        pdfPagerViewModel.pdfPageToC.observe(
            viewLifecycleOwner
        ) { pages ->
            pages?.let {
                updateToc(pages)
            }
        }
    }

    /**
     * Handle the event when an page on [position] is clicked.
     *
     * @param position Position of page in page stream.
     */
    private fun handlePageClick(position: Int) {
        activity?.findViewById<DrawerLayout>(R.id.pdf_drawer_layout)?.closeDrawers()
        pdfPagerViewModel.updateCurrentItem(position)
        popArticlePagerFragmentIfOpen()
    }

    /**
     * Handle the event when an article is clicked.
     *
     * @param article Article that was clicked.
     */
    private fun handleArticleClick(article: Article) {
        lifecycleScope.launch {
            pdfPagerViewModel.hideDrawerLogo.postValue(false)
            val fragment = if (article.isImprint() == true) ImprintWebViewFragment()
            else ArticlePagerFragment()

            requireActivity().supportFragmentManager.beginTransaction()
                .add(
                    R.id.activity_pdf_fragment_placeholder,
                    fragment,
                    ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE
                )
                .addToBackStack(null)
                .commit()

            pdfPagerViewModel.issueKey.value?.let {
                issueContentViewModel.setDisplayable(
                    IssueKey(it),
                    article.key
                )
            }
            activity?.findViewById<DrawerLayout>(R.id.pdf_drawer_layout)?.closeDrawers()
        }
    }

    /**
     * Update the list/table-of-content of an issue in the drawer.
     *
     * This will update the shown pages and their articles listed on each page.
     *
     * @param pages List of pages and articles on each page
     */
    private fun updateToc(pages: List<PageWithArticles>) {
        adapter = PageWithArticlesAdapter(
            pages,
            { position -> handlePageClick(position) },
            { article -> handleArticleClick(article) })
        navigationPageArticleRecyclerView.adapter = adapter
        hideLoadingScreen()
    }

    /**
     * Refreshes what page is shown as the current page in the drawer.
     */
    private fun refreshCurrentPage() {
//        TODO(peter) Adjust size for panorama pages
        Glide
            .with(this)
            .load(pdfPagerViewModel.currentPage.value
                ?.let {
                    storageService.getAbsolutePath(it.pagePdf)
                }).into(currentPageImageView)

        currentPageTitleView.text = pdfPagerViewModel.currentPage.value?.type?.ordinal?.let {
            resources.getQuantityString(
                R.plurals.pages,
                it,
                pdfPagerViewModel.currentPage.value?.pagina
            )
        }
    }

    private fun popArticlePagerFragmentIfOpen() {
        val articlePagerFragment =
            parentFragmentManager.findFragmentByTag(ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE)
        if (articlePagerFragment != null && articlePagerFragment.isVisible) {
            parentFragmentManager.popBackStack()
        }
    }

    private fun hideLoadingScreen() {
        activity?.runOnUiThread {
            loadingScreenConstraintLayout.apply {
                animate()
                    .alpha(0f)
                    .withEndAction {
                        this.visibility = View.GONE
                    }
                    .duration = LOADING_SCREEN_FADE_OUT_TIME
            }
        }
    }
}