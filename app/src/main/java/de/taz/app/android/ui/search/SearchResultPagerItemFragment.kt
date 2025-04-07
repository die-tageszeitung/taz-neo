package de.taz.app.android.ui.search

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.DELAY_FOR_VIEW_HEIGHT_CALCULATION
import de.taz.app.android.R
import de.taz.app.android.api.models.SearchHit
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.databinding.FragmentWebviewArticleBinding
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.login.LoginBottomSheetFragment
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetFragment
import de.taz.app.android.ui.webview.AppWebChromeClient
import de.taz.app.android.ui.webview.AppWebView
import de.taz.app.android.ui.webview.AppWebViewClient
import de.taz.app.android.ui.webview.AppWebViewClientCallBack
import de.taz.app.android.ui.webview.SearchTazApiJS
import de.taz.app.android.ui.webview.TAZ_API_JS
import de.taz.app.android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val NO_POSITION = -1
private const val ARGUMENT_PAGER_POSITION = "pager_position"

/**
 * Fragment used to show search result articles in the pager.
 * It is required as we need a childFragmentManager to use the [de.taz.app.android.ui.login.fragments.LoginFragment].
 */
class SearchResultPagerItemFragment : ViewBindingFragment<FragmentWebviewArticleBinding>() {
    companion object {
        fun newInstance(position: Int) = SearchResultPagerItemFragment().apply {
            arguments = bundleOf(ARGUMENT_PAGER_POSITION to position)
        }
    }

    private val log by Log

    private lateinit var authHelper: AuthHelper
    private lateinit var tazApiCssDataStore: TazApiCssDataStore

    val viewModel by activityViewModels<SearchResultViewModel>()
    private var position: Int = NO_POSITION
    private val webView: AppWebView
        get() = viewBinding.webView
    private lateinit var searchResult: SearchHit

    override fun onAttach(context: Context) {
        super.onAttach(context)
        authHelper = AuthHelper.getInstance(context.applicationContext)
        tazApiCssDataStore = TazApiCssDataStore.getInstance(context.applicationContext)
    }

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
            SentryWrapper.captureMessage(hint)
            return
        }
        this.searchResult = searchResult

        tazApiCssDataStore.fontSize
            .asLiveData()
            .distinctUntilChanged()
            .observe(viewLifecycleOwner) {
                reloadAfterCssChange()
            }


        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
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
        // It might happen that the WebView returns that it is rendered,
        // but we lost the view already (ie by paging very quickly).
        // That is why we check for not null:
        if (view != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewBinding.loadingScreen.visibility = View.GONE

                delay(DELAY_FOR_VIEW_HEIGHT_CALCULATION)
                setupBottomScrollLogin()
            }
        }
    }

    private fun setupBottomScrollLogin() {
        val isPublic = searchResult.articleFileName.contains("public")
        if (isPublic) {
            ensurePublicArticlesCanBeScrolled()

            viewBinding.webView.scrollListener = object: AppWebView.WebViewScrollListener {
                override fun onScroll(
                    scrollX: Int,
                    scrollY: Int,
                    oldScrollX: Int,
                    oldScrollY: Int
                ) {
                    if (oldScrollY < scrollY) {
                        val isScrolledToBottom = viewBinding.webView.bottom <= (viewBinding.webView.height + scrollY)
                        if (isScrolledToBottom) {
                            onScrolledToBottom()
                        }
                    }
                }
            }

            viewBinding.webView.overScrollListener = object : AppWebView.WebViewOverScrollListener {
                override fun onOverScroll(
                    scrollX: Int,
                    scrollY: Int,
                    clampedX: Boolean,
                    clampedY: Boolean
                ) {
                    if (scrollY > 0 && clampedY) {
                        onScrolledToBottom()
                    }
                }
            }
        }
    }

    private fun ensurePublicArticlesCanBeScrolled() {
        // Ensure the scrolling content is at least 1px higher then the scroll view
        val deviceHeight = resources.displayMetrics.heightPixels/resources.displayMetrics.density
        viewBinding.webView.evaluateJavascript("document.documentElement.style.minHeight=\"${deviceHeight+1}px\"") {}
    }

    private fun onScrolledToBottom() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (authHelper.isElapsed()) {
                SubscriptionElapsedBottomSheetFragment
                    .showSingleInstance(parentFragmentManager)
            } else {
                LoginBottomSheetFragment
                    .showSingleInstance(
                        parentFragmentManager,
                        articleName = searchResult.articleFileName
                    )
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

    private fun reloadAfterCssChange() {
        lifecycleScope.launch {
            webView.injectCss()
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
                webView.reload()
            }
        }
    }

}