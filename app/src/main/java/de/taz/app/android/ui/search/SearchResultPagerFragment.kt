package de.taz.app.android.ui.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.DISPLAYED_FEED
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.dto.SearchHitDto
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.content.ContentService
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.databinding.SearchResultWebviewPagerBinding
import de.taz.app.android.monkey.moveContentBeneathStatusBar
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsFragment
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setBottomNavigationBackActivity
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_webview_section.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

private const val RESTORATION_POSITION = "RESTORATION_POSITION"
private const val INITIAL_POSITION = "INITIAL_POSITION"

class SearchResultPagerFragment : BaseMainFragment<SearchResultWebviewPagerBinding>() {

    private val log by Log

    companion object {
        fun instance(position: Int) = SearchResultPagerFragment().apply {
            arguments = bundleOf(INITIAL_POSITION to position)
        }
    }

    private var articleRepository: ArticleRepository? = null
    private var apiService: ApiService? = null
    private var contentService: ContentService? = null

    // region views
    override val bottomNavigationMenuRes = R.menu.navigation_bottom_article
    private val webViewPager: ViewPager2
        get() = viewBinding.webviewPagerViewpager
    private val loadingScreen: ConstraintLayout
        get() = viewBinding.loadingScreen.root
    // endregion

    private var initialPosition = RecyclerView.NO_POSITION

    val viewModel by activityViewModels<SearchResultPagerViewModel>()
    private lateinit var tazApiCssDataStore: TazApiCssDataStore

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Set the tool bar invisible so it is not open the 1st time. It needs to be done here
        // in onViewCreated - when done in xml the 1st click wont be recognized...
        viewBinding.navigationBottomLayout.visibility = View.INVISIBLE

        loadingScreen.visibility = View.GONE

        // we either want to restore the last position or the one given on initialization
        initialPosition = savedInstanceState?.getInt(RESTORATION_POSITION)
                ?: requireArguments().getInt(INITIAL_POSITION, RecyclerView.NO_POSITION)
        log.verbose("initialPosition is $initialPosition")

        setupViewPager()

        articleRepository = ArticleRepository.getInstance(requireContext().applicationContext)
        apiService = ApiService.getInstance(requireContext().applicationContext)
        contentService = ContentService.getInstance(requireContext().applicationContext)
        val fontSizeLiveData = tazApiCssDataStore.fontSize.asLiveData()
        fontSizeLiveData.observeDistinct(this) {
            reloadAfterCssChange()
        }
    }

    private fun setupViewPager() {
        webViewPager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
            moveContentBeneathStatusBar()
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 2
            if (adapter == null) {
                setCurrentItem(initialPosition, false)
                adapter = SearchResultPagerAdapter(
                    this@SearchResultPagerFragment,
                    viewModel.totalFound,
                    viewModel.searchResultsLiveData.value ?: emptyList()
                )

                log.verbose("setting currentItem to initialPosition $initialPosition")
            }
        }
    }

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        private var isBookmarkedObserver = Observer<Boolean> { isBookmarked ->
            if (isBookmarked) {
                setIcon(R.id.bottom_navigation_action_bookmark, R.drawable.ic_bookmark_filled)
            } else {
                setIcon(R.id.bottom_navigation_action_bookmark, R.drawable.ic_bookmark)
            }
        }
        private var isBookmarkedLiveData: LiveData<Boolean>? = null

        override fun onPageSelected(position: Int) {
            viewModel.articleFileName = getCurrentSearchHit()?.article?.articleHtml?.name
            super.onPageSelected(position)
            if (viewModel.checkIfLoadMore(position)) {
                (activity as SearchActivity).loadMore()
            }
            lifecycleScope.launchWhenResumed {
                isBookmarkedLiveData?.removeObserver(isBookmarkedObserver)
                isBookmarkedLiveData = viewModel.isBookmarkedLiveData
                isBookmarkedLiveData?.observeDistinct(
                    this@SearchResultPagerFragment,
                    isBookmarkedObserver
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        webViewPager.registerOnPageChangeCallback(pageChangeCallback)
    }

    override fun onStop() {
        webViewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setBottomNavigationBackActivity(this.activity, BottomNavigationItem.Search)
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        setBottomNavigationBackActivity(null, BottomNavigationItem.Search)
        super.onDestroy()
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home_article -> MainActivity.start(requireActivity())

            R.id.bottom_navigation_action_bookmark -> {
                getCurrentSearchHit()?.let { hit ->
                    hit.article?.let { article ->
                        toggleBookmark(article.articleHtml.name, DateHelper.stringToDate(hit.date))
                    }
                }
            }

            R.id.bottom_navigation_action_share ->
                share()

            R.id.bottom_navigation_action_size -> {
                showBottomSheet(TextSettingsFragment())
            }
        }
    }

    private fun toggleBookmark(articleFileName: String, date: Date?) {
        CoroutineScope(Dispatchers.IO).launch {
            articleRepository?.get(articleFileName)?.let { article ->
                if (article.bookmarked) {
                    articleRepository?.debookmarkArticle(article)
                } else {
                    articleRepository?.bookmarkArticle(article)
                }
            } ?: date?.let {
                // We can assume that we want to bookmark it as we cannot de-bookmark a not downloaded article
                setIcon(R.id.bottom_navigation_action_bookmark, R.drawable.ic_bookmark_filled)
                // no articleStub so probably article not downloaded, so download it:
                downloadArticleAndSetBookmark(articleFileName, it)
            }
        }
    }

    /**
     * This function is for bookmarking articles "outside" an issue. Eg in the search result list.
     * then downloads the corresponding metadata
     * downloads the article and
     * finally bookmarks the article.
     */
    private suspend fun downloadArticleAndSetBookmark(
        articleFileName: String,
        datePublished: Date
    ) {
        withContext(Dispatchers.IO) {
            val issueMetadata = apiService?.getIssueByFeedAndDate(DISPLAYED_FEED, datePublished)
            issueMetadata?.let { issue ->
                contentService?.downloadMetadata(issue, maxRetries = 5)
                articleRepository?.get(articleFileName)?.let {
                    contentService?.downloadToCache(it)
                    articleRepository?.bookmarkArticle(it)
                }
            }
        }
    }

    fun share() {
        lifecycleScope.launch(Dispatchers.IO) {
            getCurrentSearchHit()?.let { hit ->
                val url: String? = hit.article?.onlineLink
                url?.let {
                    shareArticle(url, hit.title)
                } ?: showSharingNotPossibleDialog()
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

    private fun getCurrentPagerPosition(): Int {
        return webViewPager.currentItem
    }

    private fun getCurrentSearchHit(): SearchHitDto? {
        return getCurrentPagerPosition().let {
            viewModel.searchResultsLiveData.value?.get(it)
        }
    }

    private fun reloadAfterCssChange() {
        // draw every view again
        // TODO maybe only notify currently shown as changed?
        webViewPager.adapter?.notifyDataSetChanged()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tazApiCssDataStore = TazApiCssDataStore.getInstance(context.applicationContext)
    }

    override fun onDestroyView() {
        webViewPager.adapter = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(RESTORATION_POSITION, webViewPager.currentItem)
        super.onSaveInstanceState(outState)
    }
}