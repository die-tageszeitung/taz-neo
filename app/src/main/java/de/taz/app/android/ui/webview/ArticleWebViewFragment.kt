package de.taz.app.android.ui.webview

import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.WEEKEND_TYPEFACE_RESOURCE_FILE_NAME
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.singletons.FontHelper
import de.taz.app.android.ui.login.fragments.ArticleLoginFragment
import kotlinx.coroutines.*

class ArticleWebViewFragment :
    WebViewFragment<ArticleStub, WebViewViewModel<ArticleStub>>(R.layout.fragment_webview_article) {

    override val viewModel: ArticleWebViewViewModel by lazy {
        ViewModelProvider(this).get(ArticleWebViewViewModel::class.java)
    }

    override val nestedScrollViewId: Int = R.id.nested_scroll_view

    companion object {
        fun createInstance(article: ArticleStub): ArticleWebViewFragment {
            val fragment = ArticleWebViewFragment()
            fragment.displayable = article
            return fragment
        }
    }

    override fun setHeader(displayable: ArticleStub) {
        lifecycleScope.launch(Dispatchers.IO) {
            val index = displayable.getIndexInSection() ?: 0
            val count = ArticleRepository.getInstance(
                context?.applicationContext
            ).getSectionArticleStubListByArticleName(
                displayable.key
            ).size

            // only the imprint should have no section
            val sectionStub = displayable.getSectionStub(context?.applicationContext)
            setHeaderForSection(index, count, sectionStub)

            val issueOperations = displayable.getIssueOperations(context?.applicationContext)
            issueOperations?.apply {
                if (isWeekend) {
                    FontHelper.getInstance(context?.applicationContext)
                        .getTypeFace(WEEKEND_TYPEFACE_RESOURCE_FILE_NAME)?.let { typeface ->
                            withContext(Dispatchers.Main) {
                                view?.findViewById<TextView>(R.id.section)?.typeface = typeface
                                view?.findViewById<TextView>(R.id.article_num)?.typeface = typeface
                            }
                        }
                }
            }
        }
    }

    private fun setHeaderForSection(index: Int, count: Int, sectionStub: SectionStub?) {
        val title = sectionStub?.title ?: getString(R.string.imprint)
        activity?.runOnUiThread {
            view?.findViewById<TextView>(R.id.section)?.text = title
            view?.findViewById<TextView>(R.id.article_num)?.text = getString(
                R.string.fragment_header_article, index, count
            )
            view?.findViewById<TextView>(R.id.section)?.setOnClickListener {
                goBackToSection(sectionStub)
            }
        }
    }

    private fun goBackToSection(sectionStub: SectionStub?) {
        sectionStub?.let {
            showInWebView(it.key)
        }
    }

    override fun hideLoadingScreen() {
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.displayable?.let { article ->
                if (article.getIssueStub(context?.applicationContext)?.status == IssueStatus.public) {
                    withContext(Dispatchers.Main) {
                        try {
                            childFragmentManager.beginTransaction().replace(
                                R.id.fragment_article_bottom_fragment_placeholder,
                                ArticleLoginFragment.create(article.key)
                            ).commit()
                        } catch (e: IllegalStateException) {
                            // do nothing already hidden
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    super.hideLoadingScreen()
                }
            }
        }
    }
}

