package de.taz.app.android.ui.webview

import android.os.Bundle
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.WEEKEND_TYPEFACE_RESOURCE_FILE_NAME
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.singletons.FontHelper
import de.taz.app.android.ui.login.fragments.ArticleLoginFragment
import de.taz.app.android.ui.webview.pager.DisplayableScrollposition
import de.taz.app.android.ui.webview.pager.IssueContentViewModel
import kotlinx.coroutines.*

class ArticleWebViewFragment :
    WebViewFragment<ArticleStub, WebViewViewModel<ArticleStub>>(R.layout.fragment_webview_article) {

    override val viewModel by lazy {
        ViewModelProvider(this, SavedStateViewModelFactory(
            this.requireActivity().application, this)
        ).get(ArticleWebViewViewModel::class.java)
    }

    private val issueContentViewModel by lazy {
        ViewModelProvider(this.requireActivity(), SavedStateViewModelFactory(
            this.requireActivity().application, this.requireActivity())
        ).get(IssueContentViewModel::class.java)
    }

    override val nestedScrollViewId: Int = R.id.nested_scroll_view

    private lateinit var articleFileName: String

    companion object {
        private const val ARTICLE_FILE_NAME = "ARTICLE_FILE_NAME"
        fun createInstance(articleFileName: String): ArticleWebViewFragment {
            val args = Bundle()
            args.putString(ARTICLE_FILE_NAME, articleFileName)
            return ArticleWebViewFragment().apply {
                arguments = args
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        articleFileName = requireArguments().getString(ARTICLE_FILE_NAME)!!
        log.debug("Creating an ArticleWebView for $articleFileName")
        lifecycleScope.launch(Dispatchers.IO) {
            // Because of lazy initialization the first call to viewModel needs to be on Main thread - TODO: Fix this
            withContext(Dispatchers.Main) { viewModel }
            viewModel.displayableLiveData.postValue(
                ArticleRepository.getInstance().getStubOrThrow(articleFileName)
            )
        }
    }

    override fun onPageRendered() {
        super.onPageRendered()
        val scrollView = view?.findViewById<NestedScrollView>(nestedScrollViewId)
        issueContentViewModel.lastScrollPositionOnDisplayable?.let {
            if (it.displayableKey == articleFileName) {
                scrollView?.scrollY = it.scrollPosition
            }
        }
        scrollView?.setOnScrollChangeListener { _: NestedScrollView?, _: Int, scrollY: Int, _: Int, _: Int ->
            issueContentViewModel.lastScrollPositionOnDisplayable =
                DisplayableScrollposition(articleFileName, scrollY)
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

