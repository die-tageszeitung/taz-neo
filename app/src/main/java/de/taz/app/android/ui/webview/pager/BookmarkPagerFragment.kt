package de.taz.app.android.ui.webview.pager

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.DISPLAYABLE_NAME
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.monkey.*
import de.taz.app.android.ui.bottomSheet.bookmarks.BookmarkSheetFragment
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsFragment
import de.taz.app.android.ui.webview.ArticleWebViewFragment
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_webview_pager.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class BookmarkPagerFragment :
    BaseViewModelFragment<BookmarkPagerViewModel>(R.layout.fragment_webview_pager) {

    val log by Log

    override val enableSideBar = true

    private var articlePagerAdapter: BookmarkPagerAdapter? = null
    override val bottomNavigationMenuRes = R.menu.navigation_bottom_article

    private var articleName: String? = null
    private var hasBeenSwiped: Boolean = false

    companion object {
        fun createInstance(
            articleName: String
        ): BookmarkPagerFragment {
            val fragment = BookmarkPagerFragment()
            fragment.articleName = articleName
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.apply {
            articleName = getString(DISPLAYABLE_NAME)
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
                (adapter as BookmarkPagerAdapter?)?.notifyDataSetChanged()
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
                articlePagerAdapter = BookmarkPagerAdapter()
                adapter = articlePagerAdapter
            }
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 2
            registerOnPageChangeCallback(pageChangeListener)
        }
    }

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        var firstSwipe = true
        var showButtonJob: Job? = null

        private var isBookmarkedObserver = Observer<Boolean> { isBookmarked ->
            if (isBookmarked) {
                setIcon(R.id.bottom_navigation_action_bookmark, R.drawable.ic_bookmark_filled)
            } else {
                setIcon(R.id.bottom_navigation_action_bookmark, R.drawable.ic_bookmark)
            }
        }
        private var isBookmarkedLiveData: LiveData<Boolean>? = null

        override fun onPageSelected(position: Int) {
            viewModel.issueStubListLiveData.value?.getOrNull(position)?.let {
                setDrawerIssue(it)
            }
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

            showButtonJob?.cancel()
            showButtonJob = lifecycleScope.launchWhenResumed {
                articlePagerAdapter?.getArticleStub(position)?.let { articleStub ->
                    articleStub.getNavButton(context?.applicationContext)?.let {
                        showNavButton(it)
                    }
                    navigation_bottom.menu.findItem(R.id.bottom_navigation_action_share).isVisible =
                        articleStub.onlineLink != null

                    isBookmarkedLiveData?.removeObserver(isBookmarkedObserver)
                    isBookmarkedLiveData = articleStub.isBookmarkedLiveData()
                    isBookmarkedLiveData?.observe(this@BookmarkPagerFragment, isBookmarkedObserver)
                }
            }
        }
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home -> {
                showHome(skipToNewestIssue = true)
            }

            R.id.bottom_navigation_action_bookmark -> {
                articlePagerAdapter?.getArticleStub(viewModel.currentPosition)?.key?.let {
                    showBottomSheet(BookmarkSheetFragment.create(it))
                }
            }

            R.id.bottom_navigation_action_share ->
                share()

            R.id.bottom_navigation_action_size -> {
                showBottomSheet(TextSettingsFragment())
            }
        }
    }

    fun share() {
        lifecycleScope.launch(Dispatchers.IO) {
            articlePagerAdapter?.getArticleStub(viewModel.currentPosition)?.let { articleStub ->
                val url = articleStub.onlineLink
                url?.let {
                    shareArticle(url, articleStub.title)
                }
            }
        }
    }

    private fun shareArticle(url: String, title: String?) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, url)
            title?.let {
                putExtra(Intent.EXTRA_SUBJECT, title)
            }
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

    private inner class BookmarkPagerAdapter : FragmentStateAdapter(this@BookmarkPagerFragment) {

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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(DISPLAYABLE_NAME, articleName)
        outState.putInt(POSITION, viewModel.currentPosition)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        webview_pager_viewpager.adapter = null
        super.onDestroyView()
    }

}