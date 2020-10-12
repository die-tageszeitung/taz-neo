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

    private lateinit var articlePagerAdapter: BookmarkPagerAdapter
    override val bottomNavigationMenuRes = R.menu.navigation_bottom_article

    override val viewModel: BookmarkPagerViewModel by lazy {
        ViewModelProvider(
            this.requireActivity(),
            SavedStateViewModelFactory(this.requireActivity().application, this.requireActivity())
        ).get(BookmarkPagerViewModel::class.java)
    }

    override fun onResume() {
        super.onResume()
        viewModel.articleListLiveData.observeDistinct(this) {
            log.debug("Set new stubs $it")
            articlePagerAdapter.articleStubs = it
            loading_screen.visibility = View.GONE
            tryScrollToArticle()
        }

        viewModel.articleFileNameLiveData.observeDistinct(this) {
            tryScrollToArticle()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        webview_pager_viewpager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
            moveContentBeneathStatusBar()
        }
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
            articlePagerAdapter = BookmarkPagerAdapter()
            webview_pager_viewpager.adapter = articlePagerAdapter
        }
    }

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
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
            // If the pager is empty (no bookmarks left) we want to pop it off the stack and return to the last fragment
            if (articlePagerAdapter.articleStubs.isEmpty()) {
                this@BookmarkPagerFragment.parentFragmentManager.popBackStack()
                return
            }
            viewModel.articleFileNameLiveData.value = articlePagerAdapter.getArticleStub(position).articleFileName
            showButtonJob?.cancel()
            showButtonJob = lifecycleScope.launchWhenResumed {
                articlePagerAdapter.getArticleStub(position).let { articleStub ->
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
                getCurrentlyDisplayedArticleStub()?.let {
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
            getCurrentlyDisplayedArticleStub()?.let { articleStub ->
                val url = articleStub.onlineLink
                url?.let {
                    shareArticle(url, articleStub.title)
                }
            }
        }
    }

    private fun tryScrollToArticle() {
        val articleFileName = viewModel.articleFileNameLiveData.value
        if (
            articleFileName?.startsWith("art") == true &&
            viewModel.articleListLiveData.value?.map { it.key }?.contains(articleFileName) == true
        ) {
            log.debug("I will now display $articleFileName")
            lifecycleScope.launchWhenResumed {
                getSupposedPagerPosition()?.let {
                    if (it >= 0) {
                        webview_pager_viewpager.setCurrentItem(it, false)
                    }
                }
            }
        }
    }

    private fun getCurrentlyDisplayedArticleStub(): ArticleStub? {
        return getCurrentPagerPosition()?.let {
            articlePagerAdapter.getArticleStub(it)

        }
    }

    private fun getCurrentPagerPosition(): Int? {
        return webview_pager_viewpager?.currentItem
    }

    private fun getSupposedPagerPosition(): Int? {
        val position = articlePagerAdapter.articleStubs.indexOfFirst {
            it.key == viewModel.articleFileNameLiveData.value
        }
        return if (position >= 0) {
            position
        } else {
            null
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

    private inner class BookmarkPagerAdapter(
    ): FragmentStateAdapter(
        this@BookmarkPagerFragment
    ) {

        var articleStubs: List<ArticleStub> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun createFragment(position: Int): Fragment {
            val article = articleStubs[position]
            return ArticleWebViewFragment.createInstance(article.articleFileName)
        }

        override fun getItemId(position: Int): Long {
            return articleStubs[position].key.hashCode().toLong()
        }

        override fun containsItem(itemId: Long): Boolean {
            return articleStubs.any { itemId == it.key.hashCode().toLong() }
        }

        override fun getItemCount(): Int = articleStubs.size


        fun getArticleStub(position: Int): ArticleStub {
            return articleStubs[position]
        }

    }

    override fun onDestroyView() {
        webview_pager_viewpager.adapter = null
        super.onDestroyView()
    }

}