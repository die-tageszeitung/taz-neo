package de.taz.app.android.ui.webview.pager

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.*
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.monkey.*
import de.taz.app.android.ui.bottomSheet.bookmarks.BookmarkSheetFragment
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsFragment
import de.taz.app.android.ui.drawer.sectionList.SectionDrawerViewModel
import de.taz.app.android.ui.issueViewer.IssueViewerActivity
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.webview.ArticleWebViewFragment
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_webview_pager.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookmarkPagerFragment :
    BaseViewModelFragment<BookmarkPagerViewModel>(R.layout.fragment_webview_pager) {

    val log by Log

    private lateinit var articlePagerAdapter: BookmarkPagerAdapter
    override val bottomNavigationMenuRes = R.menu.navigation_bottom_article

    private var isBookmarkedObserver = Observer<Boolean> { isBookmarked ->
        if (isBookmarked) {
            setIcon(R.id.bottom_navigation_action_bookmark, R.drawable.ic_bookmark_filled)
        } else {
            setIcon(R.id.bottom_navigation_action_bookmark, R.drawable.ic_bookmark)
        }
    }
    private var isBookmarkedLiveData: LiveData<Boolean>? = null

    override val viewModel: BookmarkPagerViewModel by lazy {
        ViewModelProvider(
            this.requireActivity(),
            SavedStateViewModelFactory(this.requireActivity().application, this.requireActivity())
        ).get(BookmarkPagerViewModel::class.java)
    }

    private val issueViewerViewModel: IssueViewerViewModel by lazy {
        ViewModelProvider(
            this.requireActivity(),
            SavedStateViewModelFactory(this.requireActivity().application, this.requireActivity())
        ).get(IssueViewerViewModel::class.java)
    }


    private val drawerViewModel: SectionDrawerViewModel by activityViewModels()

    override fun onResume() {
        super.onResume()
        viewModel.bookmarkedArticleStubsLiveData.observeDistinct(this) {
            log.debug("Set new stubs $it")

            articlePagerAdapter.articleStubs = it
            loading_screen.visibility = View.GONE
            tryScrollToArticle()
        }

        viewModel.articleFileNameLiveData.observeDistinct(this) {
            tryScrollToArticle()
        }

        // Receiving a displayable on the issueViewerViewModel means user clicked on a section, so we'll open an actual issuecontentviewer instead this pager
        issueViewerViewModel.issueKeyAndDisplayableKeyLiveData.observeDistinct(this) {
            if (it != null) {
                Intent(requireActivity(), IssueViewerActivity::class.java).apply {
                    putExtra(IssueViewerActivity.KEY_ISSUE_KEY, it.issueKey)
                    putExtra(IssueViewerActivity.KEY_DISPLAYABLE, it.displayableKey)
                    startActivity(this)
                }
                requireActivity().finish()
            }
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

        articlePagerAdapter.registerAdapterDataObserver(object :
            RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                lifecycleScope.launchWhenResumed {
                    articlePagerAdapter.getArticleStub(
                        webview_pager_viewpager.currentItem
                    )?.let {
                        rebindBottomNavigation(
                            it
                        )
                    }
                }
            }
        })
    }

    private suspend fun rebindBottomNavigation(articleToBindTo: ArticleStub) {
        withContext(Dispatchers.IO) {
            articleToBindTo.getNavButton(context?.applicationContext)?.let {
                drawerViewModel.navButton.postValue(it)
            }
        }
        navigation_bottom.menu.findItem(R.id.bottom_navigation_action_share).isVisible =
            articleToBindTo.onlineLink != null

        isBookmarkedLiveData?.removeObserver(isBookmarkedObserver)
        isBookmarkedLiveData = articleToBindTo.isBookmarkedLiveData()
        isBookmarkedLiveData?.observe(this@BookmarkPagerFragment, isBookmarkedObserver)

    }

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            // If the pager is empty (no bookmarks left) we want to pop it off the stack and return to the last fragment
            if (articlePagerAdapter.articleStubs.isEmpty()) {
                this@BookmarkPagerFragment.parentFragmentManager.popBackStack()
                return
            }
            val articleStub = articlePagerAdapter.getArticleStub(position)
            articleStub?.let {
                viewModel.articleFileNameLiveData.value = it.articleFileName
                lifecycleScope.launchWhenResumed {
                    rebindBottomNavigation(it)
                }
            }
        }
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home -> {
                requireActivity().finish()
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
            viewModel.bookmarkedArticleStubsLiveData.value?.map { it.key }
                ?.contains(articleFileName) == true
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

    private inner class BookmarkPagerAdapter : FragmentStateAdapter(
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


        fun getArticleStub(position: Int): ArticleStub? {
            return articleStubs.getOrNull(position)
        }

    }

    override fun onDestroyView() {
        webview_pager_viewpager.adapter = null
        super.onDestroyView()
    }

}