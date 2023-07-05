package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.*
import de.taz.app.android.databinding.FragmentWebviewArticleBinding
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.login.fragments.ArticleLoginFragment
import de.taz.app.android.util.hideSoftInputKeyboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ArticleWebViewFragment : WebViewFragment<
        Article, WebViewViewModel<Article>, FragmentWebviewArticleBinding
        >() {

    override val viewModel by viewModels<ArticleWebViewViewModel>()

    override val nestedScrollViewId: Int = R.id.nested_scroll_view

    private lateinit var articleFileName: String
    private lateinit var articleRepository: ArticleRepository
    private lateinit var tracker: Tracker

    companion object {
        private const val ARTICLE_FILE_NAME = "ARTICLE_FILE_NAME"
        private const val PAGER_POSITION = "PAGER_POSITION"
        private const val PAGER_TOTAL = "PAGER_TOTAL"
        fun newInstance(
            articleFileName: String,
            pagerPosition: Int? = null,
            pagerTotal: Int? = null
        ): ArticleWebViewFragment {
            val args = Bundle()
            args.putString(ARTICLE_FILE_NAME, articleFileName)

            pagerPosition?.let { args.putInt(PAGER_POSITION, it) }
            pagerTotal?.let { args.putInt(PAGER_TOTAL, it) }

            return ArticleWebViewFragment().apply {
                arguments = args
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        articleRepository = ArticleRepository.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        articleFileName = requireArguments().getString(ARTICLE_FILE_NAME)!!
        lifecycleScope.launch {
            // FIXME (johannes): this is loading the full Article with all its FileEntries, Authors etc
            //  within for EACH article pager fragment. This DOES have a performance impact
            articleRepository.get(articleFileName)?.let {
                viewModel.displayableLiveData.postValue(
                    it
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            val article = viewModel.articleFlow.first()
            val sectionStub = viewModel.sectionStubFlow.first()
            val issueStub = viewModel.issueStubFlow.first()
            tracker.trackArticleScreen(issueStub.issueKey, sectionStub, article)
        }
    }

    override fun hideLoadingScreen() {
        lifecycleScope.launch(Dispatchers.Main) {
            viewModel.displayable?.let { article ->
                try {
                    val issueStub =
                        article.getIssueStub(requireContext().applicationContext)
                    if (issueStub?.issueKey?.status == IssueStatus.public && !article.isImprint()) {

                        childFragmentManager.beginTransaction().replace(
                            R.id.fragment_article_bottom_fragment_placeholder,
                            ArticleLoginFragment.create(article.key)
                        ).commit()
                    }
                } catch (e: IllegalStateException) {
                    // do nothing already hidden
                }
            }
            super.hideLoadingScreen()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hideKeyboardOnAllViewsExceptEditText(view)
    }

    override fun setHeader(displayable: Article) {
        // The article header is handled by the ArticlePagerFragment
        // to enable custom header behavior when swiping articles of different sections.
    }

    /**
     * This function adds hideKeyboard as touchListener on non EditTExt views recursively.
     * Inspired from SO: https://stackoverflow.com/a/11656129
     */
    @SuppressLint("ClickableViewAccessibility")
    fun hideKeyboardOnAllViewsExceptEditText(view: View) {
        // Set up touch listener for non-text box views to hide keyboard.
        if (view !is EditText) {
            view.setOnTouchListener { _, _ ->
                hideSoftInputKeyboard()
                false
            }
        }

        // If a layout container, iterate over children and seed recursion.
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val innerView = view.getChildAt(i)
                hideKeyboardOnAllViewsExceptEditText(innerView)
            }
        }
    }
}

