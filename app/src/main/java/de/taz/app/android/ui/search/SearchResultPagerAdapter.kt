package de.taz.app.android.ui.search

import android.annotation.SuppressLint
import android.os.Build
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.api.dto.SearchHitDto
import de.taz.app.android.databinding.FragmentWebviewArticleBinding
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.ui.webview.*
import kotlinx.android.synthetic.main.fragment_webview_article.view.*
import kotlinx.android.synthetic.main.fragment_webview_header_article.view.*

class SearchResultPagerAdapter(
    private val fragment: Fragment,
    private val total: Int,
    var searchResultList: List<SearchHitDto>
) : RecyclerView.Adapter<SearchResultPagerViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SearchResultPagerViewHolder {
        val binding = FragmentWebviewArticleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SearchResultPagerViewHolder(binding)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onBindViewHolder(
        holder: SearchResultPagerViewHolder,
        position: Int
    ) {
        val searchResultItem = searchResultList[position]
        holder.viewBinding.webView.apply {
            val callBack = object : AppWebViewClientCallBack {
                override fun onLinkClicked(displayableKey: String) {
                    TODO("Not yet implemented")
                }

                override fun onPageFinishedLoading() {
                    holder.viewBinding.loadingScreen.root.visibility = View.GONE
                }
            }
            webViewClient = AppWebViewClient(this.context.applicationContext, callBack)
            webChromeClient = AppWebChromeClient()

            settings.apply {
                allowFileAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                domStorageEnabled = true
                javaScriptEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            addJavascriptInterface(SearchTazApiJS(fragment), TAZ_API_JS)

            val html = searchResultItem.articleHtml.toString()
            val baseUrl = searchResultItem.baseUrl
            loadDataWithBaseURL(
                baseUrl,
                addBaseUrlToImgSrc(html, baseUrl),
                "text/html",
                "utf-8",
                null
            )
        }

        val headerTextWithHtml = fragment.requireActivity().getString(
            R.string.fragment_header_search_result,
            position + 1,
            total,
            searchResultItem.sectionTitle ?: "",
            DateHelper.stringToMediumLocalizedString(searchResultItem.date)
        )
        val headerText  = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(headerTextWithHtml, Html.FROM_HTML_MODE_COMPACT)
        } else {
            Html.fromHtml(headerTextWithHtml)
        }

        holder.viewBinding.collapsingToolbarLayout.header.article_num.text = headerText
    }

    override fun getItemCount() = searchResultList.size


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