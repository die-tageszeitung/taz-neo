package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.KNILE_REGULAR_RESOURCE_FILE_NAME
import de.taz.app.android.KNILE_SEMIBOLD_RESOURCE_FILE_NAME
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.api.models.*
import de.taz.app.android.databinding.FragmentWebviewArticleBinding
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.PageRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.FontHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.bookmarks.BookmarkViewerActivity
import de.taz.app.android.ui.drawer.DrawerAndLogoViewModel
import de.taz.app.android.ui.login.fragments.ArticleLoginFragment
import de.taz.app.android.util.hideSoftInputKeyboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArticleWebViewFragment : WebViewFragment<
        Article, WebViewViewModel<Article>, FragmentWebviewArticleBinding
        >() {

    override val viewModel by viewModels<ArticleWebViewViewModel>()
    private val drawerAndLogoViewModel: DrawerAndLogoViewModel by activityViewModels()

    override val nestedScrollViewId: Int = R.id.nested_scroll_view

    private lateinit var articleFileName: String
    private lateinit var articleRepository: ArticleRepository
    private lateinit var pageRepository: PageRepository
    private lateinit var issueRepository: IssueRepository
    private lateinit var fontHelper: FontHelper
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var storageService: StorageService
    private var isBookmarkViewerActivity = false
    private var currentAppBarOffset = 0
    private var isTabletLandscapeMode = false

    companion object {
        private const val ARTICLE_FILE_NAME = "ARTICLE_FILE_NAME"
        private const val PAGER_POSITION = "PAGER_POSITION"
        private const val PAGER_TOTAL = "PAGER_TOTAL"
        fun newInstance(articleFileName: String, pagerPosition: Int? = null, pagerTotal: Int? = null): ArticleWebViewFragment {
            val args = Bundle()
            args.putString(ARTICLE_FILE_NAME, articleFileName)

            pagerPosition?.let { args.putInt(PAGER_POSITION, it) }
            pagerTotal?.let { args.putInt(PAGER_TOTAL, it) }

            return ArticleWebViewFragment().apply {
                arguments = args
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        articleRepository = ArticleRepository.getInstance(requireContext().applicationContext)
        pageRepository = PageRepository.getInstance(requireContext().applicationContext)
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
        val sectionStub = displayable.getSectionStub(requireContext().applicationContext)
        // only the imprint should have no section
        if (sectionStub?.title == null) {
            setHeaderForImprint()
        } else if (BuildConfig.IS_LMD) {
            val firstPage = displayable.pageNameList.firstOrNull()
            if (firstPage !== null) {
                val pagina = pageRepository.getStub(firstPage)?.pagina
                setHeaderWithPage(pagina)
            } else {
                hideHeaderWithPage()
            }
        } else {
            val index = displayable.getIndexInSection(requireContext().applicationContext) ?: 0
            val count = articleRepository.getSectionArticleStubListByArticleName(
                displayable.key
            ).size
            setHeaderForSection(index, count, sectionStub)
        }

        issueStub?.apply {
            if (isWeekend) {
                applyWeekendTypefaces()
            }
        }
    }

    private fun setHeaderForImprint() {
        view?.findViewById<TextView>(R.id.section)?.text = getString(R.string.imprint)
    }

    private fun setHeaderForSection(index: Int, count: Int, sectionStub: SectionStub?) {
        lifecycleScope.launch(Dispatchers.Main) {
            view?.findViewById<TextView>(R.id.section)?.text = sectionStub?.title
            view?.findViewById<TextView>(R.id.article_num)?.text = getString(
                R.string.fragment_header_article_index_section_count, index, count
            )
            view?.findViewById<TextView>(R.id.section)?.setOnClickListener {
                goBackToSection(sectionStub)
            }
        }
    }

    private fun setHeaderWithPage(pagina: String?) {
        val sectionTextView = view?.findViewById<TextView>(R.id.section)
        val articleNumTextView = view?.findViewById<TextView>(R.id.article_num)
        sectionTextView?.visibility = View.GONE
        articleNumTextView?.text = getString(
            R.string.fragment_header_article_pagina, pagina
        )
    }

    private fun hideHeaderWithPage() {
        val sectionTextView = view?.findViewById<TextView>(R.id.section)
        val articleNumTextView = view?.findViewById<TextView>(R.id.article_num)
        sectionTextView?.visibility = View.GONE
        articleNumTextView?.visibility = View.GONE
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
                    if (issueStub?.issueKey?.status == IssueStatus.public && !article.isImprint()) {

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

        val isTabletMode = resources.getBoolean(R.bool.isTablet)
        val isLandscape =
            resources.displayMetrics.widthPixels > resources.displayMetrics.heightPixels
        isTabletLandscapeMode = isTabletMode && isLandscape

        // Map the offset of the app bar layout to the logo as it should
        // (but not on tablets in landscape)
        if (isTabletLandscapeMode) {
            drawerAndLogoViewModel.showLogo()
        } else {
            viewBinding.appBarLayout.apply {
                addOnOffsetChangedListener { appBarLayout, verticalOffset ->
                    currentAppBarOffset = verticalOffset
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        updateDrawerLogoByCurrentAppBarOffset()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isTabletLandscapeMode) {
            drawerAndLogoViewModel.showLogo()
        } else {
            updateDrawerLogoByCurrentAppBarOffset()
        }
    }

    private fun updateDrawerLogoByCurrentAppBarOffset() {
        val percentToHide =
            -currentAppBarOffset.toFloat() / viewBinding.appBarLayout.height.toFloat()
        drawerAndLogoViewModel.hideLogoByPercent(percentToHide.coerceIn(0f, 1f))
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
            fileEntryRepository.get(KNILE_SEMIBOLD_RESOURCE_FILE_NAME)
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
            fileEntryRepository.get(KNILE_REGULAR_RESOURCE_FILE_NAME)
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
        val position = arguments?.getInt(PAGER_POSITION, -1)?.takeIf { it >= 0 }?.toString() ?: "?"
        val total = arguments?.getInt(PAGER_TOTAL, -1)?.takeIf { it >= 0 }?.toString() ?: "?"

        viewBinding.headerCustom.apply {
            root.visibility = View.VISIBLE
            indexIndicator.text = activity?.getString(
                R.string.fragment_header_custom_index_indicator, position, total
            )
            sectionTitle.text = article.getSectionStub(requireContext().applicationContext)?.title
            publishedDate.text = activity?.getString(
                R.string.fragment_header_custom_published_date,
                determineDateString(article, issueStub)
            )
        }
        viewBinding.header.apply {
            root.visibility = View.GONE
        }
    }

    private fun determineDateString(article: Article, issueStub: IssueStub?): String {
        if (BuildConfig.IS_LMD) {
            return DateHelper.stringToLocalizedMonthAndYearString(article.issueDate) ?: ""
        } else {
            val fromDate = issueStub?.date?.let { DateHelper.stringToDate(it) }
            val toDate = issueStub?.validityDate?.let { DateHelper.stringToDate(it) }

            return if (fromDate != null && toDate != null) {
                DateHelper.dateToMediumRangeString(fromDate, toDate)
            } else {
                DateHelper.stringToMediumLocalizedString(article.issueDate) ?: ""
            }
        }
    }
}

