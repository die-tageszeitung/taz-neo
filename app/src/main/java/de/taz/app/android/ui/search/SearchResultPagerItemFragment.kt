package de.taz.app.android.ui.search

import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import androidx.core.view.get
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.DELAY_FOR_VIEW_HEIGHT_CALCULATION
import de.taz.app.android.R
import de.taz.app.android.api.models.SearchHit
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.databinding.FragmentWebviewArticleBinding
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.login.fragments.ArticleLoginBottomSheetFragment
import de.taz.app.android.ui.webview.AppWebChromeClient
import de.taz.app.android.ui.webview.AppWebViewClient
import de.taz.app.android.ui.webview.AppWebViewClientCallBack
import de.taz.app.android.ui.webview.SearchTazApiJS
import de.taz.app.android.ui.webview.TAZ_API_JS
import de.taz.app.android.util.Log
import io.sentry.Sentry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val NO_POSITION = -1
private const val ARGUMENT_PAGER_POSITION = "pager_position"
private const val LOGIN_FRAGMENT_SHOW_DELAY_MS = 400L

/**
 * Fragment used to show search result articles in the pager.
 * It is required as we need a childFragmentManager to use the [LoginFragment].
 */
class SearchResultPagerItemFragment() : ViewBindingFragment<FragmentWebviewArticleBinding>() {
    companion object {
        fun newInstance(position: Int) = SearchResultPagerItemFragment().apply {
            arguments = bundleOf(ARGUMENT_PAGER_POSITION to position)
        }
    }

    private val log by Log

    val viewModel by activityViewModels<SearchResultViewModel>()
    private var position: Int = NO_POSITION
    private lateinit var searchResult: SearchHit

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        position = requireArguments().getInt(ARGUMENT_PAGER_POSITION, NO_POSITION)
        require(position >= 0) { "SearchResultPagerItemFragment must be given a positive position argument" }

        val searchResult = viewModel.getSearchHit(position)
        if (searchResult == null) {
            val toastHelper = ToastHelper.getInstance(requireActivity().applicationContext)
            toastHelper.showToast(R.string.toast_unknown_error)
            val hint = "Could not load search result item at position $position"
            log.error(hint)
            Sentry.captureMessage(hint)
            return
        }
        this.searchResult = searchResult

        setupWebView()
    }

    private fun setupWebView() {
        viewBinding.webView.apply {
            val callBack = object : AppWebViewClientCallBack {
                override fun onLinkClicked(displayableKey: String) {
                    log.warn("onLinkClicked not implemented yet. Ignored click for $displayableKey")
                }
                override fun onPageFinishedLoading() {}
            }
            webViewClient = AppWebViewClient(this.context.applicationContext, callBack)
            webChromeClient = AppWebChromeClient(::onWebViewRendered)

            settings.apply {
                allowFileAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                domStorageEnabled = true
                javaScriptEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            addJavascriptInterface(SearchTazApiJS(this@SearchResultPagerItemFragment), TAZ_API_JS)

            val html = searchResult.articleHtml.toString()
            val baseUrl = searchResult.baseUrl
            loadDataWithBaseURL(
                baseUrl,
                addBaseUrlToImgSrc(html, baseUrl),
                "text/html",
                "utf-8",
                null
            )
        }
    }

    private fun onWebViewRendered() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewBinding.loadingScreen.visibility = View.GONE
            delay(DELAY_FOR_VIEW_HEIGHT_CALCULATION)
            addPaddingIfNecessaryForScrolling()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setScrollChangeListenerForArticleLoginBottomSheetFragment()
            }
        }
    }

    private fun addPaddingIfNecessaryForScrolling() {
        val webViewWrapper = viewBinding.webViewBorderPager
        val webViewWrapperHeight = webViewWrapper.height
        val screenHeight = resources.displayMetrics.heightPixels
        val difference = screenHeight - webViewWrapperHeight
        val oldPadding = webViewWrapper.paddingBottom
        if (difference > 0) {
            webViewWrapper.updatePadding(bottom = difference + oldPadding)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun setScrollChangeListenerForArticleLoginBottomSheetFragment() {
        val isPublic = searchResult.articleFileName.contains("public")

        val scrollView = viewBinding.nestedScrollView
        val lastChildOfScrollView = scrollView[scrollView.childCount - 1]

        if (isPublic) {
            scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                val bottomDetector = lastChildOfScrollView.bottom - (scrollView.height + scrollY)
                val isScrolledToBottom: Boolean = (bottomDetector == 0)

                // Show the Login BottomSheet if the scrollable Content (WebView) is at the bottom,
                // and only if this pager item is currently shown (the Fragment is resumed)
                if (isScrolledToBottom && isResumed) {
                    parentFragmentManager.apply {
                        if (findFragmentByTag(ArticleLoginBottomSheetFragment.TAG) == null) {
                            ArticleLoginBottomSheetFragment.newInstance(searchResult.articleFileName)
                                .show(this, ArticleLoginBottomSheetFragment.TAG)
                        }
                    }
                }
            }
        }
    }

    /**
     * Search for eg "src="Media.10977995.norm.jpg"" and add the given baseUrl
     */
    private fun addBaseUrlToImgSrc(html: String, baseUrl: String): String {
        return html.replace(
            Regex(
                """src=["'](.+?\.(?:png|jpg|jpeg))["']""",
                RegexOption.IGNORE_CASE
            ), "src=\"$baseUrl/$1\""
        )
    }
}