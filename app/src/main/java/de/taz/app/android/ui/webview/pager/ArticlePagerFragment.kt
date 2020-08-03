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
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.monkey.*
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.bottomSheet.bookmarks.BookmarkSheetFragment
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsFragment
import de.taz.app.android.ui.webview.ArticleWebViewFragment
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_webview_pager.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val DISPLAYABLE_NAME = "articleName"

class ArticlePagerFragment() :
    BaseViewModelFragment<ArticlePagerViewModel>(R.layout.fragment_webview_pager),
    BackFragment {

    val log by Log
    override val enableSideBar: Boolean = true

    private var articlePagerAdapter: ArticlePagerAdapter? = null

    private var articleName: String? = null
    private var hasBeenSwiped: Boolean = false

    override val bottomNavigationMenuRes = R.menu.navigation_bottom_article
    private val issueContentViewModel: IssueContentViewModel? by lazy { (parentFragment as? IssueContentFragment)?.viewModel }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.apply {
            articleName = getString(DISPLAYABLE_NAME)
            viewModel.currentPositionLiveData.value = getInt(POSITION, 0)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        webview_pager_viewpager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
            moveContentBeneathStatusBar()
        }

        webview_pager_viewpager.apply {
            (adapter as ArticlePagerAdapter?)?.notifyDataSetChanged()
            setCurrentItem(viewModel.currentPosition, false)
        }
        loading_screen.visibility = View.GONE

        viewModel.currentPositionLiveData.observeDistinct(this) {
            if (webview_pager_viewpager.currentItem != it) {
                webview_pager_viewpager.setCurrentItem(it, false)
            }
        }

        issueContentViewModel?.issueOperationsLiveData?.observeDistinct(this) { issueOperations ->
            issueOperations?.let { setDrawerIssue(it) }
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
        private var isBookmarkedObserver = Observer<Boolean> { isBookmarked ->
            if (isBookmarked) {
                setIcon(R.id.bottom_navigation_action_bookmark, R.drawable.ic_bookmark_filled)
            } else {
                setIcon(R.id.bottom_navigation_action_bookmark, R.drawable.ic_bookmark)
            }
        }
        private var isBookmarkedLiveData: LiveData<Boolean>? = null

        override fun onPageSelected(position: Int) {
            if (firstSwipe) {
                firstSwipe = false
            } else {
                hasBeenSwiped = true
                issueContentViewModel?.sectionNameListLiveData?.observeDistinctUntil(
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

            lifecycleScope.launchWhenResumed {
                articlePagerAdapter?.getArticleStub(position)?.let { articleStub ->
                    articleStub.getNavButton(context?.applicationContext)?.let {
                        showNavButton(it)
                    }
                    navigation_bottom.menu.findItem(R.id.bottom_navigation_action_share).isVisible =
                        articleStub.onlineLink != null

                    isBookmarkedLiveData?.removeObserver(isBookmarkedObserver)
                    isBookmarkedLiveData = articleStub.isBookmarkedLiveData()
                    isBookmarkedLiveData?.observe(this@ArticlePagerFragment, isBookmarkedObserver)
                }
            }
        }
    }

    private inner class ArticlePagerAdapter : FragmentStateAdapter(this@ArticlePagerFragment) {

        private val articleStubs
            get() = issueContentViewModel?.articleList ?: emptyList()

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
        val noSectionParent = parentFragmentManager.backStackEntryCount == 1

        return if (hasBeenSwiped || noSectionParent) {
            showSectionOrGoBack()
        } else {
            false
        }
    }

    private fun showSectionOrGoBack(): Boolean {
        return articlePagerAdapter?.getArticleStub(viewModel.currentPosition)?.let {
            lifecycleScope.launch(Dispatchers.IO) {
                it.getSectionStub(context?.applicationContext)?.key?.let { sectionKey ->
                    showInWebView(sectionKey)
                }
            }
            true
        } ?: false
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

    fun tryLoadArticle(articleFileName: String) {
        lifecycleScope.launchWhenStarted {
            issueContentViewModel?.articleList?.indexOfFirst { it.key == articleFileName }?.let {
                if (it >= 0) {
                    webview_pager_viewpager.setCurrentItem(it, false)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(DISPLAYABLE_NAME, articleName)
        outState.putInt(POSITION, viewModel.currentPosition)
        super.onSaveInstanceState(outState)
    }

}