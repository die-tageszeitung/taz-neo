package de.taz.app.android.ui.webview

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.whenCreated
import com.google.android.material.appbar.AppBarLayout
import de.taz.app.android.DELAY_FOR_VIEW_HEIGHT_CALCULATION
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.databinding.FragmentWebviewArticleBinding
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.DEFAULT_COLUMN_GAP_PX
import de.taz.app.android.singletons.TazApiCssHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.login.LoginBottomSheetFragment
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ArticleWebViewFragment :
    WebViewFragment<ArticleOperations, WebViewViewModel<ArticleOperations>, FragmentWebviewArticleBinding>(),
    MultiColumnLayoutReadyCallback {

    /**
     * Helper functions used to control the collapsible elements while scrolling programmatically.
     * Should be implemented by every Pager containing ArticleWebViewFragments like
     * ArticlePagerFragment and BookmarkPagerFragment
     */
    interface CollapsibleLayoutProvider {
        fun getAppBarLayout(): AppBarLayout
        fun getBottomNavigationLayout(): View
    }

    override val viewModel by viewModels<ArticleWebViewViewModel>()
    private val tapIconsViewModel: TapIconsViewModel by activityViewModels()

    private lateinit var articleFileName: String
    private lateinit var tazApiCssDataStore: TazApiCssDataStore
    private lateinit var tazApiCssHelper: TazApiCssHelper
    private lateinit var articleRepository: ArticleRepository
    private lateinit var tracker: Tracker
    private lateinit var authHelper: AuthHelper

    private var isMultiColumnMode = false

    override val webView: AppWebView
        get() = viewBinding.webView

    override val nestedScrollView: NestedScrollView
        get() = viewBinding.nestedScrollView

    override val loadingScreen: View
        get() = viewBinding.loadingScreen

    override val appBarLayout: AppBarLayout?
        get() = (parentFragment as? CollapsibleLayoutProvider)?.getAppBarLayout()

    override val bottomNavigationLayout: View?
        get() = (parentFragment as? CollapsibleLayoutProvider)?.getBottomNavigationLayout()

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

    override fun onAttach(context: Context) {
        super.onAttach(context)
        articleRepository = ArticleRepository.getInstance(context.applicationContext)
        tazApiCssDataStore = TazApiCssDataStore.getInstance(context.applicationContext)
        tazApiCssHelper = TazApiCssHelper.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
        authHelper = AuthHelper.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        articleFileName = requireArguments().getString(ARTICLE_FILE_NAME)!!
        lifecycleScope.launch {
            articleRepository.getStub(articleFileName)?.let {
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
        viewBinding.webViewBorderPager.showTapIconsListener = null
        super.onDestroyView()
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
    }

    override fun setHeader(displayable: ArticleOperations) {
        // The article header is handled by the ArticlePagerFragment
        // to enable custom header behavior when swiping articles of different sections.
    }

    override fun onPageRendered() {
        super.onPageRendered()
        viewLifecycleOwner.lifecycleScope.launch {
            // setting multi column mode is only possible after page is rendered so the webView can compute its scroll width
            if (isMultiColumnMode) {
                setupMultiColumnMode()
            } else {
                restoreLastScrollPosition()
                hideLoadingScreen()

                delay(DELAY_FOR_VIEW_HEIGHT_CALCULATION)
                ensurePublicArticlesCanBeScrolled()
            }
        }
    }

    private fun setupMultiColumnMode() {
        // Ignore the setup if the WebView has not rendered yet
        if (!isRendered) {
            return
        }

        viewBinding.apply {
            nestedScrollView.scrollingEnabled = false
            webViewBorderPager.pagingEnabled = true
        }

        webView.updateLayoutParams<MarginLayoutParams> {
            topMargin =
                resources.getDimensionPixelSize(R.dimen.fragment_webview_article_little_margin_top)
        }

        viewBinding.webViewBorderPager.showTapIconsListener = {
            when (it) {
                true  -> tapIconsViewModel.showTapIcons()
                false -> tapIconsViewModel.hideTapIcons()
            }
        }

        webView.callTazApi(
            "enableArticleColumnMode",
            calculateColumnHeight(),
            calculateColumnWidth(),
            DEFAULT_COLUMN_GAP_PX
        )
    }

    override fun onMultiColumnLayoutReady(contentWidth: Int?) {
        lifecycleScope.launch {
            restoreLastHorizontalScrollPosition()
            hideLoadingScreen()
        }
        super.onMultiColumnLayoutReady(contentWidth)
    }

    private fun disableMultiColumnMode() {
        // Ignore if the WebView has not rendered yet
        if (!isRendered) {
            return
        }

        log.verbose("Change text settings: switch off multi column mode")
        viewBinding.apply {
            nestedScrollView.scrollingEnabled = true
            webViewBorderPager.apply {
                pagingEnabled = false
                showTapIconsListener = null
            }
        }
        tapIconsViewModel.hideTapIcons()

        webView.updateLayoutParams<MarginLayoutParams> {
            topMargin =
                resources.getDimensionPixelSize(R.dimen.fragment_webview_article_margin_top)
        }

        webView.callTazApi("disableArticleColumnMode")
    }

    private fun calculateColumnHeight(): Float {
        val webViewMarginTop =
            resources.getDimensionPixelSize(R.dimen.fragment_webview_article_margin_top)

        return viewBinding.container.height.toFloat() / resources.displayMetrics.density - webViewMarginTop
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
            viewBinding.container.width.toFloat() / resources.displayMetrics.density
        return (webViewWidth - (amountOfColumns + 1) * DEFAULT_COLUMN_GAP_PX) / amountOfColumns
    }


    private suspend fun ensurePublicArticlesCanBeScrolled() {
        val article = viewModel.articleFlow.first()
        val issueStub = viewModel.issueStubFlow.first()
        val isPublic = issueStub.status == IssueStatus.public

        if (isPublic && !article.isImprint()) {
            // Ensure the scrolling content is at least 1px higher then the scroll view
            val delta = viewBinding.nestedScrollView.height - viewBinding.webViewBorderPager.height
            if (delta >= 0) {
                viewBinding.webViewBorderPager.apply {
                    updatePadding(
                        bottom = paddingBottom + delta + 1
                    )
                }
            }

        }
    }

    override fun onScrolledToBottom() {
        viewLifecycleOwner.lifecycleScope.launch {
            val article = viewModel.articleFlow.first()
            val issueStub = viewModel.issueStubFlow.first()
            val isPublic = issueStub.status == IssueStatus.public

            if (isPublic && !article.isImprint() && isResumed) {
                viewLifecycleOwner.lifecycleScope.launch {
                    if (authHelper.isElapsed()) {
                        SubscriptionElapsedBottomSheetFragment
                            .showSingleInstance(parentFragmentManager)
                    } else {
                        LoginBottomSheetFragment
                            .showSingleInstance(
                                parentFragmentManager,
                                articleName = articleFileName
                            )
                    }
                }
            }
        }
    }

}