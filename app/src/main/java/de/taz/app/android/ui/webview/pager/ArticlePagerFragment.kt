package de.taz.app.android.ui.webview.pager

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.base.ViewModelBaseMainFragment
import de.taz.app.android.monkey.*
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.webview.ArticleWebViewFragment
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_webview_pager.*
import kotlinx.coroutines.launch

const val ARTICLE_NAME = "articleName"

class ArticlePagerFragment : ViewModelBaseMainFragment(R.layout.fragment_webview_pager),
    BackFragment {

    val viewModel = ArticlePagerViewModel()

    val log by Log

    private var articlePagerAdapter: ArticlePagerAdapter? = null

    private var articleName: String? = null
    private var hasBeenSwiped: Boolean = false

    companion object {
        fun createInstance(articleName: String): ArticlePagerFragment {
            val fragment = ArticlePagerFragment()
            fragment.articleName = articleName
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.apply {
            articleName = getString(ARTICLE_NAME)
            viewModel.currentPositionLiveData.value = getInt(POSITION, 0)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.articleNameLiveData.value = articleName

        webview_pager_viewpager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
            moveContentBeneathStatusBar()
        }

        viewModel.articleListLiveData.observeDistinct(this) {
            webview_pager_viewpager.apply {
                (adapter as ArticlePagerAdapter?)?.notifyDataSetChanged()
                setCurrentItem(viewModel.currentPosition, false)
            }
            loading_screen.visibility = View.GONE
        }

        viewModel.currentPositionLiveData.observeDistinct(this) {
            if (webview_pager_viewpager.currentItem != it) {
                webview_pager_viewpager.setCurrentItem(it, false)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setupViewPager()
    }


    private fun setupViewPager() {
        webview_pager_viewpager?.apply {
            if (adapter == null) {
                articlePagerAdapter = ArticlePagerAdapter()
                adapter = articlePagerAdapter
            }
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
                viewModel.sectionNameListLiveData.observeDistinctUntil(
                    viewLifecycleOwner, {
                        if (it.isNotEmpty()) {
                            it[position]?.let { sectionName ->
                                getMainView()?.setActiveDrawerSection(sectionName)
                            }
                        }
                    }, { it.isNotEmpty() }
                )
            }

            viewModel.currentPositionLiveData.value = position

            lifecycleScope.launch {
                articlePagerAdapter?.getArticleStub(position)?.getNavButton()?.let {
                    showNavButton(it)
                }
            }
        }
    }

    private inner class ArticlePagerAdapter : FragmentStateAdapter(this@ArticlePagerFragment) {

        private val articleStubs
            get() = viewModel.articleList

        override fun createFragment(position: Int): Fragment {
            val article = articleStubs[position]
            return ArticleWebViewFragment.createInstance(article)
        }

        override fun getItemCount(): Int = articleStubs.size

        fun getArticleStub(position: Int): ArticleStub {
            return articleStubs[position]
        }

    }

    override fun onBackPressed(): Boolean {
        val noSectionParent =  parentFragmentManager.backStackEntryCount == 1

        if (hasBeenSwiped || noSectionParent) {
            if (noSectionParent) {
                parentFragmentManager.popBackStack()
            }
            showSectionOrGoBack()
        } else {
            parentFragmentManager.popBackStack()
        }
        return true
    }

    private fun showSectionOrGoBack() {
        viewModel.sectionNameListLiveData.value?.getOrNull(viewModel.currentPosition)?.let {
            showInWebView(it)
        } ?: parentFragmentManager.popBackStack()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(ARTICLE_NAME, articleName)
        outState.putInt(POSITION, viewModel.currentPosition)
        super.onSaveInstanceState(outState)
    }
}