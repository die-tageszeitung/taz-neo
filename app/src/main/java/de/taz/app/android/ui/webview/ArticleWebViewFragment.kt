package de.taz.app.android.ui.webview

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Section
import kotlinx.coroutines.*

class ArticleWebViewFragment : WebViewFragment<Article>() {

    var article: Article? = null

    companion object {
        fun createInstance(article: Article): WebViewFragment<Article> {
            val fragment = ArticleWebViewFragment()
            fragment.article = article
            return fragment
        }
    }

    override fun getWebViewDisplayable(): Article? {
        return article
    }

    override fun setWebViewDisplayable(displayable: Article?) {
        this.article = displayable
    }

    override val headerLayoutId: Int = R.layout.fragment_webview_header_article

    override val visibleItemIds = listOf(
        R.id.bottom_navigation_action_bookmark,
        R.id.bottom_navigation_action_help,
        R.id.bottom_navigation_action_share,
        R.id.bottom_navigation_action_size
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (article?.bookmarked == true) {
            setPermanentlyActive(R.id.bottom_navigation_action_bookmark)
            setIconActive(R.id.bottom_navigation_action_bookmark)
        }
    }

    override fun configureHeader(): Job? {
        return activity?.lifecycleScope?.launch(Dispatchers.IO) {
            article?.getSection()?.let { section ->
                setHeaderForSection(section)
            }
        }
    }

    private fun setHeaderForSection(section: Section) {
        activity?.apply {
            runOnUiThread {
                findViewById<TextView>(R.id.section)?.text = section.title
                findViewById<TextView>(R.id.article_num)?.text = getString(
                        R.string.fragment_header_article,
                        section.articleList.indexOf(article) + 1,
                        section.articleList.size
                )
            }
        }
    }

}

