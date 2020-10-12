package de.taz.app.android.ui.webview.pager

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.monkey.*
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.bottomSheet.bookmarks.BookmarkSheetFragment
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsFragment
import de.taz.app.android.ui.webview.ArticleWebViewFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlinx.android.synthetic.main.fragment_webview_pager.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArticlePagerFragment : BaseMainFragment(
    R.layout.fragment_webview_pager
), BackFragment {

    private val log by Log

    override val bottomNavigationMenuRes = R.menu.navigation_bottom_article
    override val enableSideBar: Boolean = true
    private var hasBeenSwiped = false

    private val issueContentViewModel: IssueContentViewModel by lazy {
        ViewModelProvider(
            requireActivity(), SavedStateViewModelFactory(
                requireActivity().application, requireActivity()
            )
        ).get(IssueContentViewModel::class.java)
    }

    override fun onResume() {
        super.onResume()
        issueContentViewModel.articleListLiveData.observeDistinct(this.viewLifecycleOwner) { articleStubs ->
            webview_pager_viewpager.adapter = ArticlePagerAdapter(articleStubs)
            tryScrollToArticle()
        }

        issueContentViewModel.displayableKeyLiveData.observeDistinct(this.viewLifecycleOwner) {
            tryScrollToArticle()
        }

        issueContentViewModel.activeDisplayMode.observeDistinct(this) {
            // reset swiped flag on navigating away from article pager
            if (it != IssueContentDisplayMode.Article) {
                hasBeenSwiped = false
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        webview_pager_viewpager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
            moveContentBeneathStatusBar()

            (adapter as ArticlePagerAdapter?)?.notifyDataSetChanged()
        }
        loading_screen.visibility = View.GONE
    }

    override fun onStart() {
        super.onStart()
        setupViewPager()
    }


    private fun setupViewPager() {
        webview_pager_viewpager?.apply {
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 2
            registerOnPageChangeCallback(pageChangeListener)
        }
    }

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        private var lastPage: Int? = null
        private var isBookmarkedObserver = Observer<Boolean> { isBookmarked ->
            if (isBookmarked) {
                setIcon(R.id.bottom_navigation_action_bookmark, R.drawable.ic_bookmark_filled)
            } else {
                setIcon(R.id.bottom_navigation_action_bookmark, R.drawable.ic_bookmark)
            }
        }
        private var isBookmarkedLiveData: LiveData<Boolean>? = null

        override fun onPageSelected(position: Int) {
            if (lastPage != null && lastPage != position) {
                hasBeenSwiped = true
                runIfNotNull(
                    issueContentViewModel.issueStubAndDisplayableKeyLiveData.value?.first,
                    issueContentViewModel.articleListLiveData.value?.getOrNull(position)
                ) { issueStub, displayable ->
                    log.debug("After swiping select displayable to ${displayable.key} (${displayable.title})")
                    if (issueContentViewModel.activeDisplayMode.value == IssueContentDisplayMode.Article) {
                        issueContentViewModel.setDisplayable(issueStub.issueKey, displayable.key)
                    }
                }
            }
            lastPage = position

            lifecycleScope.launchWhenResumed {
                getCurrentArticleStub()?.let { articleStub ->
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

    private inner class ArticlePagerAdapter(val articleStubs: List<ArticleStub>) :
        FragmentStateAdapter(this@ArticlePagerFragment) {


        override fun createFragment(position: Int): Fragment {
            val article = articleStubs[position]
            return ArticleWebViewFragment.createInstance(article.articleFileName)
        }

        override fun getItemCount(): Int = articleStubs.size

        fun getArticleStub(position: Int): ArticleStub {
            return articleStubs[position]
        }
    }

    override fun onBackPressed(): Boolean {
        return if (hasBeenSwiped) {
            lifecycleScope.launch { showSectionOrGoBack() }
            true
        } else {
            return false
        }
    }

    private suspend fun showSectionOrGoBack(): Boolean = withContext(Dispatchers.IO) {
        getCurrentArticleStub()?.let { articleStub ->
            runIfNotNull(
                issueContentViewModel.issueStubAndDisplayableKeyLiveData.value?.first,
                articleStub.getSectionStub(null)
            ) { issueStub, sectionStub ->
                issueContentViewModel.setDisplayable(issueStub.issueKey, sectionStub.key)
                true
            }
        } ?: false
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home -> {
                showHome(skipToNewestIssue = true)
            }

            R.id.bottom_navigation_action_bookmark -> {
                getCurrentArticleStub()?.let {
                    showBottomSheet(BookmarkSheetFragment.create(it.key))
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
            getCurrentArticleStub()?.let { articleStub ->
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

    private fun tryScrollToArticle() {
        val articleKey = issueContentViewModel.displayableKeyLiveData.value
        if (
            articleKey?.startsWith("art") == true &&
            issueContentViewModel.articleListLiveData.value?.map { it.key }?.contains(articleKey) == true
        ) {
            issueContentViewModel.activeDisplayMode.postValue(IssueContentDisplayMode.Article)
            if (articleKey != getCurrentArticleStub()?.key) {
                log.debug("I will now display $articleKey")
                getSupposedPagerPosition()?.let {
                    if (it >= 0) {
                        webview_pager_viewpager.setCurrentItem(it, false)
                    }
                }
            }
        }
    }

    private fun getCurrentPagerPosition(): Int? {
        return webview_pager_viewpager?.currentItem
    }

    private fun getSupposedPagerPosition(): Int? {
        val position = (webview_pager_viewpager.adapter as? ArticlePagerAdapter)?.articleStubs?.indexOfFirst {
            it.key == issueContentViewModel.displayableKeyLiveData.value
        }
        return if (position != null && position >= 0) {
            position
        } else {
            null
        }
    }

    private fun getCurrentArticleStub(): ArticleStub? {
        return getCurrentPagerPosition()?.let {
            issueContentViewModel.articleListLiveData.value?.get(it)
        }
    }

    override fun onDestroyView() {
        webview_pager_viewpager.adapter = null
        super.onDestroyView()
    }
}