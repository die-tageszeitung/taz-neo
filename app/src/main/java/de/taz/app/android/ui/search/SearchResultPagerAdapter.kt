package de.taz.app.android.ui.search

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.*
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.api.dto.SearchHitDto
import de.taz.app.android.ui.webview.*
import kotlinx.android.synthetic.main.fragment_webview_section.*

class SearchResultPagerAdapter(
    var searchResultList: List<SearchHitDto>
) :
    RecyclerView.Adapter<SearchResultPagerAdapter.SearchResultPagerViewHolder>() {

    class SearchResultPagerViewHolder(
        val view: AppWebView
    ) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SearchResultPagerViewHolder {
        val textView = AppWebView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        return SearchResultPagerViewHolder(textView)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onBindViewHolder(
        holder: SearchResultPagerViewHolder,
        position: Int
    ) {
        val searchResultItem = searchResultList[position]
        holder.view.apply {
            val webView = ArticleWebViewFragment.createInstance(searchResultItem.article!!.articleHtml.name)
            webViewClient = AppWebViewClient(this.context.applicationContext, webView)
            webChromeClient = AppWebChromeClient()

            settings.apply {
                allowFileAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                domStorageEnabled = true
                javaScriptEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                setSupportZoom(true)
                settings.builtInZoomControls = true
            }
            addJavascriptInterface(TazApiJS(webView), TAZ_API_JS)

            loadDataWithBaseURL(
                searchResultItem.baseUrl,
                searchResultItem.articleHtml!!,
                "text/html",
                "UTF-8",
                null
            )
            //contentService.downloadToCache(ResourceInfoKey(-1))
        }
    }
    override fun getItemCount() = searchResultList.size

}