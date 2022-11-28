package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import de.taz.app.android.R
import de.taz.app.android.WEEKEND_TYPEFACE_BOLD_RESOURCE_FILE_NAME
import de.taz.app.android.WEEKEND_TYPEFACE_REGULAR_RESOURCE_FILE_NAME
import de.taz.app.android.api.models.*
import de.taz.app.android.databinding.FragmentWebviewArticleBinding
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.FontHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.bookmarks.BookmarkViewerActivity
import de.taz.app.android.ui.login.fragments.ArticleLoginFragment
import de.taz.app.android.util.hideSoftInputKeyboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class ArticleWebViewFragment : WebViewFragment<
        Article, WebViewViewModel<Article>, FragmentWebviewArticleBinding
        >() {

    override val viewModel by viewModels<ArticleWebViewViewModel>()

    override val nestedScrollViewId: Int = R.id.nested_scroll_view

    private lateinit var articleFileName: String
    private lateinit var articleRepository: ArticleRepository
    private lateinit var issueRepository: IssueRepository
    private lateinit var fontHelper: FontHelper
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var storageService: StorageService
    private var isBookmarkViewerActivity = false

    companion object {
        private const val ARTICLE_FILE_NAME = "ARTICLE_FILE_NAME"
        fun newInstance(articleFileName: String): ArticleWebViewFragment {
            val args = Bundle()
            args.putString(ARTICLE_FILE_NAME, articleFileName)
            return ArticleWebViewFragment().apply {
                arguments = args
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        articleRepository = ArticleRepository.getInstance(requireContext().applicationContext)
        issueRepository = IssueRepository.getInstance(requireContext().applicationContext)
        fontHelper = FontHelper.getInstance(requireContext().applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(requireContext().applicationContext)
        storageService = StorageService.getInstance(requireContext().applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        articleFileName = requireArguments().getString(ARTICLE_FILE_NAME)!!
        lifecycleScope.launch {
            withContext(Dispatchers.Main) { viewModel }
            articleRepository.get(articleFileName)?.let {
                viewModel.displayableLiveData.postValue(
                    it
                )
            }
        }
        isBookmarkViewerActivity = activity is BookmarkViewerActivity
    }

    override fun setHeader(displayable: Article) {
        lifecycleScope.launch {
            val issueStub = issueRepository.getIssueStubForArticle(displayable.key)
            if (isBookmarkViewerActivity) {
                setBookmarkHeader(displayable, issueStub)
            } else {
                setRegularHeader(displayable, issueStub)
            }
        }
    }

    private suspend fun setRegularHeader(displayable: Article, issueStub: IssueStub?) {
        val index = displayable.getIndexInSection(requireContext().applicationContext) ?: 0
        val count = ArticleRepository.getInstance(
            requireContext().applicationContext
        ).getSectionArticleStubListByArticleName(
            displayable.key
        ).size

        // only the imprint should have no section
        val sectionStub = displayable.getSectionStub(requireContext().applicationContext)
        setHeaderForSection(index, count, sectionStub)

        issueStub?.apply {
            if (isWeekend) {
                applyWeekendTypefaces()
            }
        }
    }

    private fun setHeaderForSection(index: Int, count: Int, sectionStub: SectionStub?) {
        val title = sectionStub?.title ?: getString(R.string.imprint)
        lifecycleScope.launch(Dispatchers.Main) {
            view?.findViewById<TextView>(R.id.section)?.text = title
            view?.findViewById<TextView>(R.id.article_num)?.text = getString(
                R.string.fragment_header_article, index, count
            )
            view?.findViewById<TextView>(R.id.section)?.setOnClickListener {
                goBackToSection(sectionStub)
            }
        }
    }

    private fun goBackToSection(sectionStub: SectionStub?) = lifecycleScope.launch {
        sectionStub?.let {
            issueRepository.getIssueStubForSection(sectionStub.sectionFileName)?.let { issueStub ->
                lifecycleScope.launch {
                    issueViewerViewModel.setDisplayable(
                        issueStub.issueKey,
                        sectionStub.sectionFileName
                    )
                }
            }
        }
    }

    override fun hideLoadingScreen() {
        lifecycleScope.launch(Dispatchers.Main) {
            viewModel.displayable?.let { article ->
                try {
                    val issueStub =
                        article.getIssueStub(requireContext().applicationContext)
                    if (issueStub?.issueKey?.status == IssueStatus.public) {

                        childFragmentManager.beginTransaction().replace(
                            R.id.fragment_article_bottom_fragment_placeholder,
                            ArticleLoginFragment.create(article.key)
                        ).commit()
                    }
                } catch (e: IllegalStateException) {
                    // do nothing already hidden
                }
            }
            super.hideLoadingScreen()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hideKeyboardOnAllViewsExceptEditText(view)
    }

    /**
     * This function adds hideKeyboard as touchListener on non EditTExt views recursively.
     * Inspired from SO: https://stackoverflow.com/a/11656129
     */
    @SuppressLint("ClickableViewAccessibility")
    fun hideKeyboardOnAllViewsExceptEditText(view: View) {

        // Set up touch listener for non-text box views to hide keyboard.
        if (view !is EditText) {
            view.setOnTouchListener { _, _ ->
                hideSoftInputKeyboard()
                false
            }
        }

        // If a layout container, iterate over children and seed recursion.
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val innerView = view.getChildAt(i)
                hideKeyboardOnAllViewsExceptEditText(innerView)
            }
        }
    }

    private suspend fun applyWeekendTypefaces() {
        val weekendTypefaceFileEntry =
            fileEntryRepository.get(WEEKEND_TYPEFACE_BOLD_RESOURCE_FILE_NAME)
        val weekendTypefaceFile = weekendTypefaceFileEntry?.let(storageService::getFile)
        weekendTypefaceFile?.let {
            fontHelper
                .getTypeFace(it)?.let { typeface ->
                    withContext(Dispatchers.Main) {
                        view?.findViewById<TextView>(R.id.section)?.typeface = typeface
                    }
                }
        }
        val weekendTypefaceFileEntryRegular =
            fileEntryRepository.get(WEEKEND_TYPEFACE_REGULAR_RESOURCE_FILE_NAME)
        val weekendTypefaceFileRegular =
            weekendTypefaceFileEntryRegular?.let(storageService::getFile)
        weekendTypefaceFileRegular?.let {
            fontHelper
                .getTypeFace(it)?.let { typeface ->
                    withContext(Dispatchers.Main) {
                        view?.findViewById<TextView>(R.id.article_num)?.typeface = typeface
                    }
                }
        }
    }

    private suspend fun setBookmarkHeader(article: Article, issueStub: IssueStub?) {
        // hide the logo on bookmarks. CAREFUL: the drawer is still accessible
        viewBinding.collapsingToolbarLayout.findViewById<MaterialToolbar>(R.id.header)
            ?.let {
                it.visibility = View.GONE
            }
        viewBinding.root.findViewById<ImageView>(R.id.drawer_logo)?.visibility = View.GONE

        val total = articleRepository.getBookmarkedArticleStubs().size
        val index = articleRepository.getBookmarkedArticleStubs().indexOf(ArticleStub(article))

        viewBinding.collapsingToolbarLayout.findViewById<MaterialToolbar>(R.id.header_custom)
            ?.apply {
                visibility = View.VISIBLE
                findViewById<TextView>(R.id.index_indicator).text = activity?.getString(
                    R.string.fragment_header_custom_index_indicator, index + 1, total
                )
                findViewById<TextView>(R.id.section_title).text =
                    article.getSectionStub(requireContext().applicationContext)?.title
                findViewById<TextView>(R.id.published_date).text = activity?.getString(
                    R.string.fragment_header_custom_published_date, determineDateString(article, issueStub)
                )
            }
    }

    private fun determineDateString(article: Article, issueStub: IssueStub?): String {
        val fromDate = issueStub?.date?.let { DateHelper.stringToDate(it) }
        val toDate = issueStub?.validityDate?.let { DateHelper.stringToDate(it) }

        val formattedDate = if (fromDate != null && toDate != null) {
            DateHelper.dateToMediumRangeString(fromDate, toDate)
        } else {
            DateHelper.stringToMediumLocalizedString(article.issueDate)
        }
        return formattedDate ?: ""
    }
}

