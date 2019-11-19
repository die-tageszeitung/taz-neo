package de.taz.app.android.ui.webview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Section
import de.taz.app.android.ui.archive.endNavigation.ArchiveEndNavigationFragment
import kotlinx.coroutines.*

class ArticleWebViewFragment : WebViewFragment<Article>() {

    var article: Article? = null

    override val inactiveIconMap = mapOf(
        R.id.bottom_navigation_action_bookmark to R.drawable.ic_bookmark,
        R.id.bottom_navigation_action_share to R.drawable.ic_share,
        R.id.bottom_navigation_action_size to R.drawable.ic_text_size
    )

    override val activeIconMap = mapOf(
        R.id.bottom_navigation_action_bookmark to R.drawable.ic_bookmark_active,
        R.id.bottom_navigation_action_share to R.drawable.ic_share_active,
        R.id.bottom_navigation_action_size to R.drawable.ic_text_size_active
    )

    companion object {
        fun createInstance(article: Article): WebViewFragment<Article> {
            val fragment = ArticleWebViewFragment()
            fragment.article = article
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_webview_article, container, false)
    }

    override fun getWebViewDisplayable(): Article? {
        return article
    }

    override fun setWebViewDisplayable(displayable: Article?) {
        this.article = displayable
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (article?.bookmarked == true) {
            setPermanentlyActive(R.id.bottom_navigation_action_bookmark)
            setIconActive(R.id.bottom_navigation_action_bookmark)
        }
        activity?.lifecycleScope?.launch(Dispatchers.IO) {
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

