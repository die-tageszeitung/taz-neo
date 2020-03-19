package de.taz.app.android.ui.webview.pager

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
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
import de.taz.app.android.util.StableIdProvider
import de.taz.app.android.util.StableIdViewModel
import kotlinx.android.synthetic.main.fragment_webview_pager.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArticlePagerFragment : ViewModelBaseMainFragment(R.layout.fragment_webview_pager), BackFragment {

    val viewModel = ArticlePagerViewModel()

    val log by Log

    private var stableIdProvider: StableIdProvider? = null
    private var articlePagerAdapter: ArticlePagerAdapter? = null

    companion object {
        fun createInstance(articleName: String, showBookmarks: Boolean = false): ArticlePagerFragment {
            val fragment = ArticlePagerFragment()
            fragment.viewModel.showBookmarks = showBookmarks
            fragment.viewModel.articleName = articleName
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        webview_pager_viewpager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
            moveContentBeneathStatusBar()
        }

        stableIdProvider = ViewModelProvider(this).get(StableIdViewModel::class.java).also {
            articlePagerAdapter = ArticlePagerAdapter(this, it)
        }

        viewModel.currentPosition?.let {
            webview_pager_viewpager.currentItem = it
        }

        viewModel.articleListLiveData.observeDistinct(this) {
            setArticles(it, viewModel.articlePosition)
            loading_screen.visibility = View.GONE
        }

    }

    override fun onResume() {
        setupViewPager()
        super.onResume()
    }

    override fun onStop() {
        webview_pager_viewpager.adapter = null
        super.onStop()
    }

    private fun setupViewPager() {
        webview_pager_viewpager?.apply {
            adapter = articlePagerAdapter
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 1
            registerOnPageChangeCallback(pageChangeListener)
        }
    }

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            viewModel.currentPosition = position
        }
    }

    private fun setArticles(articles: List<ArticleStub>, currentPosition: Int) {
        webview_pager_viewpager.apply {
            (adapter as ArticlePagerAdapter?)?.submitList(articles)
            setCurrentItem(currentPosition, false)
        }
    }

    private inner class ArticlePagerAdapter(
        fragment: Fragment,
        private val stableIdProvider: StableIdProvider
    ) : FragmentStateAdapter(fragment) {
        private var articles = emptyList<ArticleStub>()

        override fun createFragment(position: Int): Fragment {
            val article = articles[position]
            return ArticleWebViewFragment.createInstance(article)
        }

        override fun getItemCount(): Int = articles.size

        override fun getItemId(position: Int): Long {
            val filename = articles[position].articleFileName
            return stableIdProvider.getId(filename)
        }

        fun submitList(newArticles: List<ArticleStub>) {
            articles = newArticles
            notifyDataSetChanged()
        }

    }

    override fun onBackPressed(): Boolean {
        if (viewModel.showBookmarks) {
            showMainFragment(BookmarksFragment())
            showHome()
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                viewModel.articleList?.get(
                    viewModel.currentPosition ?: 0
                )?.getSectionStub()?.webViewDisplayableKey?.let {
                    withContext(Dispatchers.Main) {
                        showInWebView(it)
                    }
                }
            }
        }
        return true
    }


}