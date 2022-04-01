package de.taz.app.android.ui.search

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebSettings
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.api.dto.SearchHitDto
import de.taz.app.android.ui.webview.*

class SearchResultPagerAdapter(
    var searchResultList: List<SearchHitDto>
) :  RecyclerView.Adapter<SearchResultPagerAdapter.SearchResultPagerViewHolder>() {

    class SearchResultPagerViewHolder(
        val view: AppWebView
    ) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SearchResultPagerViewHolder {
        val webView = AppWebView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        return SearchResultPagerViewHolder(webView)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onBindViewHolder(
        holder: SearchResultPagerViewHolder,
        position: Int
    ) {
        val searchResultItem = searchResultList[position]
        holder.view.apply {
            val webView =
                ArticleWebViewFragment.createInstance(searchResultItem.article!!.articleHtml.name)
            webViewClient = AppWebViewClient(this.context.applicationContext, webView)
            webChromeClient = AppWebChromeClient()

            settings.apply {
                allowFileAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                domStorageEnabled = true
                javaScriptEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            addJavascriptInterface(TazApiJS(webView), TAZ_API_JS)

            val html = searchResultItem.articleHtml.toString()
            val baseUrl = searchResultItem.baseUrl
            loadDataWithBaseURL(baseUrl, addBaseUrlToImgSrc(html, baseUrl), "text/html", "utf-8", null)
        }
    }

    override fun getItemCount() = searchResultList.size


    /**
     * Search for eg "src="Media.10977995.norm.jpg"" and add the given baseUrl
     */
    private fun addBaseUrlToImgSrc(html: String, baseUrl: String): String {
        return html.replace(Regex("""src=["'](.+?\.(?:png|jpg|jpeg))["']""", RegexOption.IGNORE_CASE),"src=\"$baseUrl/$1\"")
    }
}