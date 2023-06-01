package de.taz.app.android.ui.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.SearchHit
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.content.ContentService
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.databinding.SearchResultWebviewPagerBinding
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.BookmarkRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsFragment
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setBottomNavigationBackActivity
import de.taz.app.android.ui.webview.pager.ArticleBottomActionBarNavigationHelper
import de.taz.app.android.util.Log
import io.sentry.Sentry
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date

private const val RESTORATION_POSITION = "RESTORATION_POSITION"
private const val INITIAL_POSITION = "INITIAL_POSITION"

class SearchResultPagerFragment : BaseMainFragment<SearchResultWebviewPagerBinding>() {

    private val log by Log

    companion object {
        fun newInstance(position: Int) = SearchResultPagerFragment().apply {
            arguments = bundleOf(INITIAL_POSITION to position)
        }
    }

    private lateinit var articleRepository: ArticleRepository
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var apiService: ApiService
    private lateinit var contentService: ContentService
    private lateinit var toastHelper: ToastHelper

    // region views
    private val webViewPager: ViewPager2
        get() = viewBinding.webviewPagerViewpager
    private val loadingScreen: ConstraintLayout
        get() = viewBinding.loadingScreen.root
    // endregion

    private var initialPosition = RecyclerView.NO_POSITION

    val viewModel by activityViewModels<SearchResultPagerViewModel>()
    private val searchResultViewModel by activityViewModels<SearchResultViewModel>()

    private lateinit var tazApiCssDataStore: TazApiCssDataStore
    private val articleBottomActionBarNavigationHelper =
        ArticleBottomActionBarNavigationHelper(::onBottomNavigationItemClicked)

    private var searchResultPagerAdapter: SearchResultPagerAdapter? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        articleRepository = ArticleRepository.getInstance(context.applicationContext)
        bookmarkRepository = BookmarkRepository.getInstance(context.applicationContext)
        apiService = ApiService.getInstance(context.applicationContext)
        contentService = ContentService.getInstance(context.applicationContext)
        tazApiCssDataStore = TazApiCssDataStore.getInstance(context.applicationContext)
        toastHelper = ToastHelper.getInstance(requireActivity().applicationContext)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        articleBottomActionBarNavigationHelper
            .setBottomNavigationFromContainer(viewBinding.navigationBottomLayout)

        loadingScreen.visibility = View.GONE

        // we either want to restore the last position or the one given on initialization
        initialPosition = savedInstanceState?.getInt(RESTORATION_POSITION)
            ?: requireArguments().getInt(INITIAL_POSITION, RecyclerView.NO_POSITION)
        log.verbose("initialPosition is $initialPosition")

        setupViewPager()

        tazApiCssDataStore.fontSize
            .asLiveData()
            .distinctUntilChanged()
            .observe(viewLifecycleOwner) {
                reloadAfterCssChange()
            }

        viewModel.isBookmarkedLiveData.observe(
            viewLifecycleOwner,
            isBookmarkedObserver
        )

        bringAudioPlayerOverlayToFront()

        viewModel.searchResultsLiveData.observe(viewLifecycleOwner) { updatedSearchResults ->
            searchResultViewModel.mapFromSearchResultPagerViewModel(
                viewModel.sessionId,
                updatedSearchResults,
                viewModel.totalFound
            )
        }

        // Wait for the loaded result being ready before jumping to the initial position
        viewLifecycleOwner.lifecycleScope.launch {
            val loadedCount = searchResultViewModel.loadedSearchResults.filter { it > 0 }.first()
            if (initialPosition < loadedCount) {
                searchResultPagerAdapter?.updateLoadedCount(loadedCount)
                log.verbose("setting currentItem to initialPosition $initialPosition")
                webViewPager.setCurrentItem(initialPosition, false)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                searchResultViewModel.loadedSearchResults.collect {
                    searchResultPagerAdapter?.updateLoadedCount(it)
                }
            }
        }

    }

    private fun setupViewPager() {
        searchResultPagerAdapter =
            SearchResultPagerAdapter(this)
        webViewPager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 2
            adapter = searchResultPagerAdapter
        }
    }

    private var isBookmarkedObserver = Observer<Boolean> { isBookmarked ->
        articleBottomActionBarNavigationHelper.setBookmarkIcon(isBookmarked)
    }

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            val currentSearchHit = getCurrentSearchHit()
            viewModel.articleFileName = currentSearchHit?.articleFileName
            super.onPageSelected(position)
            if (viewModel.checkIfLoadMore(position)) {
                (activity as SearchActivity).loadMore()
            }

            articleBottomActionBarNavigationHelper.apply {
                // show the share icon always when in public article
                // OR when an onLink link is provided
                setShareIconVisibility(
                    currentSearchHit?.onlineLink,
                    currentSearchHit?.articleFileName
                )

                // ensure the action bar is showing when the article changes
                expand(true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        webViewPager.registerOnPageChangeCallback(pageChangeCallback)
    }

    override fun onStop() {
        webViewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        (activity as? SearchActivity)?.updateRecyclerView(
            getCurrentPagerPosition()
        )
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

    private fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home_article -> MainActivity.start(requireActivity())

            R.id.bottom_navigation_action_bookmark -> {
                getCurrentSearchHit()?.let { hit ->
                    toggleBookmark(hit.articleFileName, DateHelper.stringToDate(hit.date))
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
        applicationScope.launch {
            val articleStub = articleRepository.getStub(articleFileName)

            when {
                articleStub != null -> {
                    val isBookmarked = bookmarkRepository.toggleBookmarkAsync(articleStub.articleFileName).await()
                    if (isBookmarked) {
                        toastHelper.showToast(R.string.toast_article_bookmarked)
                    }
                    else {
                        toastHelper.showToast(R.string.toast_article_debookmarked)
                    }
                }
                articleStub == null && date != null -> {
                    // We can assume that we want to bookmark it as we cannot de-bookmark a not downloaded article
                    articleBottomActionBarNavigationHelper.setBookmarkIcon(isBookmarked = true)
                    toastHelper.showToast(R.string.toast_article_bookmarked)
                    // no articleStub so probably article not downloaded, so download it:
                    downloadArticleAndSetBookmark(articleFileName, date)
                }
                // This is an unexpected case with the date being null. We simply have to ignore this
                else -> Unit
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
        try {
            val issueMetadata = apiService.getIssueByFeedAndDate(BuildConfig.DISPLAYED_FEED, datePublished)
            contentService.downloadMetadata(issueMetadata, maxRetries = 5)
            val article = requireNotNull(articleRepository.get(articleFileName))
            contentService.downloadToCache(article)
            bookmarkRepository.addBookmark(article.key)
        } catch (e: Exception) {
            log.warn("Error while trying to download a full article because of a bookmark request", e)
            Sentry.captureException(e)
            ToastHelper.getInstance(requireActivity().applicationContext)
                .showToast(R.string.toast_problem_bookmarking_article, long = true)
        }
    }

    fun share() {
        lifecycleScope.launch {
            getCurrentSearchHit()?.let { hit ->
                hit.onlineLink?.let {
                    shareArticle(it, hit.title)
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

    private fun getCurrentSearchHit(): SearchHit? {
        return getCurrentPagerPosition().let {
            viewModel.searchResultsLiveData.value?.get(it)
        }
    }

    private fun reloadAfterCssChange() {
        // draw every view again
        webViewPager.adapter?.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        webViewPager.adapter = null
        articleBottomActionBarNavigationHelper.onDestroyView()
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(RESTORATION_POSITION, webViewPager.currentItem)
        super.onSaveInstanceState(outState)
    }

    /**
     * Try to bring the audio player on the parent activity to the front.
     * This is required because this Fragment is added to android.R.id.content view and will
     * be in front of the AudioPlayer because it was added later.
     */
    // FIXME (johannes): Consider to add the [SearchResultPagerFragment] to a container defined by the app, or using windows for the overlay.
    private fun bringAudioPlayerOverlayToFront() {
        (activity as? SearchActivity)?.let { searchActivity ->
            searchActivity.bringAudioPlayerOverlayToFront()
        }
    }
}