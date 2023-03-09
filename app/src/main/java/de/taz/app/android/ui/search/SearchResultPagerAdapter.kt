package de.taz.app.android.ui.search

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.api.models.SearchHit
import de.taz.app.android.databinding.FragmentWebviewArticleBinding
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.ui.webview.*

class SearchResultPagerAdapter(
    private val fragment: Fragment,
    private val total: Int,
    private var searchResultList: List<SearchHit>
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
        holder.viewBinding.collapsingToolbarLayout.findViewById<MaterialToolbar>(R.id.header)?.let {
            it.visibility = View.GONE
        }

        holder.viewBinding.collapsingToolbarLayout.findViewById<MaterialToolbar>(R.id.header_custom)?.apply {
            visibility = View.VISIBLE
            findViewById<TextView>(R.id.index_indicator).text = fragment.requireActivity().getString(
                R.string.fragment_header_custom_index_indicator,
                (position + 1).toString(),
                total.toString()
            )
            findViewById<TextView>(R.id.section_title).text = searchResultItem.sectionTitle ?: ""
            findViewById<TextView>(R.id.published_date).text =  fragment.requireActivity().getString(
                R.string.fragment_header_custom_published_date,
                if (BuildConfig.IS_LMD){
                    DateHelper.stringToLocalizedMonthAndYearString(searchResultItem.date)
                } else {
                    DateHelper.stringToMediumLocalizedString(searchResultItem.date)
                }
            )
        }
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