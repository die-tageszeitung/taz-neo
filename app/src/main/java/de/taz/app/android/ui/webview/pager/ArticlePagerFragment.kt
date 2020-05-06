package de.taz.app.android.ui.webview.pager

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.base.ViewModelBaseMainFragment
import de.taz.app.android.monkey.moveContentBeneathStatusBar
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.bookmarks.BookmarksFragment
import de.taz.app.android.ui.webview.ArticleWebViewFragment
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_webview_pager.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val SHOW_BOOKMARKS = "showBookmarks"
const val ARTICLE_NAME = "articleName"

class ArticlePagerFragment : ViewModelBaseMainFragment(R.layout.fragment_webview_pager),
    BackFragment {

    val viewModel = ArticlePagerViewModel()

    val log by Log

    private var articlePagerAdapter: ArticlePagerAdapter? = null
    private var articleListObserver: Observer<List<ArticleStub>>? = null

    private var showBookmarks: Boolean = false
    private var articleName: String? = null
    private var hasBeenSwiped: Boolean = false

    companion object {
        fun createInstance(
            articleName: String,
            showBookmarks: Boolean = false
        ): ArticlePagerFragment {
            val fragment = ArticlePagerFragment()
            fragment.showBookmarks = showBookmarks
            fragment.articleName = articleName
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        savedInstanceState?.apply {
            showBookmarks = getBoolean(SHOW_BOOKMARKS)
            articleName = getString(ARTICLE_NAME)
        }
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.showBookmarks = showBookmarks
        viewModel.articleName = articleName

        webview_pager_viewpager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
            moveContentBeneathStatusBar()
        }

        articlePagerAdapter = articlePagerAdapter ?: ArticlePagerAdapter(this)
    }

    override fun onStart() {
        setupViewPager()
        articleListObserver = viewModel.articleListLiveData.observeDistinct(this) {
            setArticles(it, viewModel.articlePosition)
            loading_screen.visibility = View.GONE
        }

        viewModel.currentPosition?.let {
            webview_pager_viewpager.currentItem = it
        }

        super.onStart()
    }

    override fun onStop() {
        articleListObserver?.let {
            Transformations.distinctUntilChanged(viewModel.articleListLiveData).removeObserver(it)
        }
        webview_pager_viewpager.adapter = null
        super.onStop()
    }

    private fun setupViewPager() {
        webview_pager_viewpager?.apply {
            adapter = articlePagerAdapter
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 2
            registerOnPageChangeCallback(pageChangeListener)
        }
    }

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        var firstSwipe = true

        override fun onPageSelected(position: Int) {
            if (firstSwipe) {
                firstSwipe = false
            } else {
                hasBeenSwiped = true
                lifecycleScope.launch(Dispatchers.IO) {
                    viewModel.sectionNameList?.get(position)?.let {
                        getMainView()?.setActiveDrawerSection(it)
                    }
                }
            }

            viewModel.currentPosition = position

            lifecycleScope.launch {
                articlePagerAdapter?.getArticleStub(position)?.getNavButton()?.let {
                    showNavButton(it)
                }
            }
        }
    }

    private fun setArticles(articles: List<ArticleStub>, currentPosition: Int) {
        webview_pager_viewpager.apply {
            (adapter as ArticlePagerAdapter?)?.submitList(articles)
            setCurrentItem(currentPosition, false)
        }
    }

    private inner class ArticlePagerAdapter(
        fragment: Fragment
    ) : FragmentStateAdapter(fragment) {
        private var articleStubs = emptyList<ArticleStub>()

        override fun createFragment(position: Int): Fragment {
            val article = articleStubs[position]
            return ArticleWebViewFragment.createInstance(article)
        }

        override fun getItemCount(): Int = articleStubs.size

        fun submitList(newArticleStubs: List<ArticleStub>) {
            articleStubs = newArticleStubs
            notifyDataSetChanged()
        }

        fun getArticleStub(position: Int): ArticleStub {
            return articleStubs[position]
        }

    }

    override fun onBackPressed(): Boolean {
        if (viewModel.showBookmarks) {
            showMainFragment(BookmarksFragment())
        } else {
            if (hasBeenSwiped) {
                showSectionOrGoBack()
            } else {
                parentFragmentManager.popBackStack()
            }
        }
        return true
    }

    private fun showSectionOrGoBack() {
        viewModel.sectionNameList?.get(viewModel.currentPosition ?: 0)?.let {
            showInWebView(it)
        } ?: parentFragmentManager.popBackStack()
    }

    fun tryLoadArticle(articleFileName: String): Boolean {
        viewModel.articleList?.indexOfFirst { it.key == articleFileName }?.let { index ->
            if (index > 0) {
                webview_pager_viewpager.setCurrentItem(index, false)
                return true
            }
        }
        return false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(SHOW_BOOKMARKS, showBookmarks)
        outState.putString(ARTICLE_NAME, articleName)
        super.onSaveInstanceState(outState)
    }

}