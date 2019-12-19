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
import de.taz.app.android.ui.BackFragment
import kotlinx.coroutines.*

class ArticleWebViewFragment : WebViewFragment<Article>(), BackFragment {

    var article: Article? = null

    override val presenter = ArticleWebViewPresenter()

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
        // TODO needs to be livedata
        if (article?.bookmarked == true) {
            setIcon(R.id.bottom_navigation_action_bookmark, R.drawable.ic_bookmark_active)
        }
        activity?.lifecycleScope?.launch(Dispatchers.IO) {
            article?.getSection()?.let { section ->
                setHeaderForSection(section)
            }
        }
    }

    private fun setHeaderForSection(section: Section) {
        activity?.runOnUiThread {
            view?.findViewById<TextView>(R.id.section)?.text = section.title
            view?.findViewById<TextView>(R.id.article_num)?.text = getString(
                R.string.fragment_header_article,
                section.articleList.indexOf(article) + 1,
                section.articleList.size
            )
            view?.findViewById<TextView>(R.id.section)?.setOnClickListener {
                presenter.showSection()
            }
        }
    }

    override fun onBackPressed(): Boolean {
        return presenter.onBackPressed()
    }

}

