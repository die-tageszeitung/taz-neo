package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.EditText
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.whenCreated
import de.taz.app.android.R
import de.taz.app.android.api.models.*
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.databinding.FragmentWebviewArticleBinding
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.singletons.DEFAULT_COLUMN_GAP_PX
import de.taz.app.android.singletons.TazApiCssHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.HorizontalDirection
import de.taz.app.android.ui.login.fragments.ArticleLoginFragment
import de.taz.app.android.util.hideSoftInputKeyboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ArticleWebViewFragment :
    WebViewFragment<Article, WebViewViewModel<Article>, FragmentWebviewArticleBinding>(),
    MultiColumnLayoutReadyCallback {

    override val viewModel by viewModels<ArticleWebViewViewModel>()

    override val nestedScrollViewId: Int = R.id.nested_scroll_view

    private lateinit var articleFileName: String
    private lateinit var tazApiCssDataStore: TazApiCssDataStore
    private lateinit var tazApiCssHelper: TazApiCssHelper
    private lateinit var articleRepository: ArticleRepository
    private lateinit var tracker: Tracker

    private var isMultiColumnMode = false

    companion object {
        private const val ARTICLE_FILE_NAME = "ARTICLE_FILE_NAME"
        private const val PAGER_POSITION = "PAGER_POSITION"
        private const val PAGER_TOTAL = "PAGER_TOTAL"
        fun newInstance(
            articleFileName: String,
            pagerPosition: Int? = null,
            pagerTotal: Int? = null
        ): ArticleWebViewFragment {
            val args = Bundle()
            args.putString(ARTICLE_FILE_NAME, articleFileName)

            pagerPosition?.let { args.putInt(PAGER_POSITION, it) }
            pagerTotal?.let { args.putInt(PAGER_TOTAL, it) }

            return ArticleWebViewFragment().apply {
                arguments = args
            }
        }
    }

    override fun onPause() {
        hideTapIcons()
        super.onPause()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        articleRepository = ArticleRepository.getInstance(context.applicationContext)
        tazApiCssDataStore = TazApiCssDataStore.getInstance(context.applicationContext)
        tazApiCssHelper = TazApiCssHelper.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        articleFileName = requireArguments().getString(ARTICLE_FILE_NAME)!!
        lifecycleScope.launch {
            // FIXME (johannes): this is loading the full Article with all its FileEntries, Authors etc
            //  within for EACH article pager fragment. This DOES have a performance impact
            articleRepository.get(articleFileName)?.let {
                viewModel.displayableLiveData.postValue(
                    it
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            val article = viewModel.articleFlow.first()
            val sectionStub = viewModel.sectionStubFlow.first()
            val issueStub = viewModel.issueStubFlow.first()
            tracker.trackArticleScreen(issueStub.issueKey, sectionStub, article)
        }
    }

    override fun onDestroyView() {
        webView.showTapIcon = null
        super.onDestroyView()
    }

    override fun hideLoadingScreen() {
        lifecycleScope.launch(Dispatchers.Main) {
            viewModel.displayable?.let { article ->
                try {
                    val issueStub =
                        article.getIssueStub(requireContext().applicationContext)
                    if (issueStub?.issueKey?.status == IssueStatus.public && !article.isImprint() && !isMultiColumnMode) {

                        childFragmentManager.beginTransaction().replace(
                            R.id.fragment_article_bottom_fragment_placeholder,
                            ArticleLoginFragment.create(article.key)
                        ).commit()
                    }
                } catch (e: IllegalStateException) {
                    // there is no more Fragment.Context as requireContext() threw up.
                    // that means the Fragment is already being destroyed and we don't have to do anything
                }
            }
            super.hideLoadingScreen()
        }
    }

    override fun reloadAfterCssChange() {
        lifecycleScope.launch {
            whenCreated {
                if (!isRendered) {
                    return@whenCreated
                }

                webView.injectCss()
                val isMultiColumn = tazApiCssDataStore.multiColumnMode.get()
                if (isMultiColumn) {
                    // Unfortunately this is necessary so the web view gets it s correct scrollWidth and can calculate the proper width
                    webView.callTazApi(
                        "enableArticleColumnMode",
                        calculateColumnHeight(),
                        calculateColumnWidth(),
                        DEFAULT_COLUMN_GAP_PX
                    )
                }
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
                    webView.reload()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                tazApiCssDataStore.multiColumnMode.asFlow().collect {
                    if (isMultiColumnMode != it) {
                        isMultiColumnMode = it
                        if (it) {
                            setupMultiColumnMode()
                        } else {
                            disableMultiColumnMode()
                        }
                    }
                }
            }
        }
        hideKeyboardOnAllViewsExceptEditText(view)
    }

    override fun setHeader(displayable: Article) {
        // The article header is handled by the ArticlePagerFragment
        // to enable custom header behavior when swiping articles of different sections.
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

    override fun onPageRendered() {
        super.onPageRendered()
        lifecycleScope.launch {
            // setting multi column mode is only possible after page is rendered so the webView can compute its scroll width
            if (isMultiColumnMode) {
                setupMultiColumnMode()
            } else {
                restoreLastScrollPosition()
                addPaddingBottomIfNecessary()
                hideLoadingScreen()
            }
        }
    }

    private fun setupMultiColumnMode() {
        // Ignore the setup if the WebView has not rendered yet
        if (!isRendered) {
            return
        }

        viewBinding.nestedScrollView.scrollingEnabled = false
        webView.updateLayoutParams<MarginLayoutParams> {
            topMargin =
                resources.getDimensionPixelSize(R.dimen.fragment_webview_article_little_margin_top)
        }

        // show the navigation buttons when on border
        webView.showTapIcon = { border ->
            when (border) {
                HorizontalDirection.LEFT -> showLeftTapIcon()
                HorizontalDirection.RIGHT -> showRightTapIcon()
                HorizontalDirection.BOTH -> showBothTapIcons()
                HorizontalDirection.NONE -> hideTapIcons()
            }
        }
        webView.callTazApi(
            "enableArticleColumnMode",
            calculateColumnHeight(),
            calculateColumnWidth(),
            DEFAULT_COLUMN_GAP_PX
        )
    }

    override fun onMultiColumnLayoutReady() {
        lifecycleScope.launch {
            restoreLastHorizontalScrollPosition()
            hideLoadingScreen()
        }
    }

    private fun disableMultiColumnMode() {
        // Ignore if the WebView has not rendered yet
        if (!isRendered) {
            return
        }

        log.verbose("Change text settings: switch off multi column mode")
        viewBinding.nestedScrollView.scrollingEnabled = true
        hideTapIcons()
        webView.showTapIcon = null

        webView.updateLayoutParams<MarginLayoutParams> {
            topMargin =
                resources.getDimensionPixelSize(R.dimen.fragment_webview_article_margin_top)
        }

        webView.callTazApi("disableArticleColumnMode")
    }

    private fun showRightTapIcon() {
        viewBinding.apply {
            rightTapIcon.visibility = View.VISIBLE
            leftTapIcon.visibility = View.GONE
        }
    }
    private fun showLeftTapIcon() {
        viewBinding.apply {
            leftTapIcon.visibility = View.VISIBLE
            rightTapIcon.visibility = View.GONE
        }
    }
    private fun showBothTapIcons() {
        viewBinding.apply {
            leftTapIcon.visibility = View.VISIBLE
            rightTapIcon.visibility = View.VISIBLE
        }
    }
    private fun hideTapIcons() {
        viewBinding.apply {
            leftTapIcon.visibility = View.GONE
            rightTapIcon.visibility = View.GONE
        }
    }

    private fun calculateColumnHeight(): Float {
        val webViewMarginTop =
            resources.getDimensionPixelSize(R.dimen.fragment_webview_article_margin_top)
        return viewBinding.nestedScrollView.height.toFloat() / resources.displayMetrics.density - webViewMarginTop
    }


    private fun calculateColumnWidth(): Float {
        val isPortrait =
            resources.displayMetrics.widthPixels < resources.displayMetrics.heightPixels
        val amountOfColumns = if (isPortrait) {
            2
        } else {
            3
        }
        val webViewWidth =
            viewBinding.nestedScrollView.width.toFloat() / resources.displayMetrics.density
        return (webViewWidth - (amountOfColumns + 1) * DEFAULT_COLUMN_GAP_PX) / amountOfColumns
    }

}

