package de.taz.app.android.ui.pdfViewer

import android.os.Bundle
import android.view.View
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import de.taz.app.android.ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.databinding.FragmentDrawerBodyPdfWithSectionsBinding
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
class DrawerBodyPdfWithSectionsFragment :
    ViewBindingFragment<FragmentDrawerBodyPdfWithSectionsBinding>() {

    private lateinit var storageService: StorageService

    private var adapter: PageWithArticlesAdapter? =
        PageWithArticlesAdapter(
            emptyList(),
            { position -> handlePageClick(position) },
            { article -> handleArticleClick(article) })

    private val pdfPagerViewModel: PdfPagerViewModel by activityViewModels()
    private val issueContentViewModel: IssueViewerViewModel by activityViewModels()


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        storageService = StorageService.getInstance(requireContext().applicationContext)

        viewBinding.navigationPageArticleRecyclerView.layoutManager = LinearLayoutManager(context)
        viewBinding.navigationPageArticleRecyclerView.adapter = adapter

        pdfPagerViewModel.currentPage.observe(viewLifecycleOwner) {
            pdfPagerViewModel.currentItem.value?.let { _ ->
                refreshCurrentPage()
            }
        }
        pdfPagerViewModel.issuePublication.observe(viewLifecycleOwner) { issue_publication ->
            val date = DateHelper.stringToDate(issue_publication.date)
            val dateString = date?.let { DateHelper.dateToMonthYearString(it) }

            viewBinding.fragmentDrawerBodyPdfWithSectionsTitle.text = getString(
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
        viewBinding.navigationPageArticleRecyclerView.adapter = adapter
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
                }).into(viewBinding.fragmentDrawerBodyPdfWithSectionsCurrentPageImage)

        viewBinding.fragmentDrawerBodyPdfWithSectionsCurrentPageTitle.text =
            pdfPagerViewModel.currentPage.value?.type?.ordinal?.let {
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
        viewBinding.pdfDrawerLoadingScreen.root.apply {
            animate()
                .alpha(0f)
                .withEndAction {
                    visibility = View.GONE
                }
                .duration = LOADING_SCREEN_FADE_OUT_TIME
        }
    }
}