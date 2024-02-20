package de.taz.app.android.ui.search

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.databinding.SearchResultWebviewPagerBinding
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.BookmarkRepository
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsBottomSheetFragment
import de.taz.app.android.ui.drawer.DrawerAndLogoViewModel
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setBottomNavigationBackActivity
import de.taz.app.android.ui.webview.pager.ArticleBottomActionBarNavigationHelper
import de.taz.app.android.util.Log
import io.sentry.Sentry
import kotlinx.coroutines.flow.filterNotNull
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
    private lateinit var tracker: Tracker
    private lateinit var generalDataStore: GeneralDataStore

    // region views
    private val webViewPager: ViewPager2
        get() = viewBinding.webviewPagerViewpager
    private val loadingScreen: ConstraintLayout
        get() = viewBinding.loadingScreen.root
    // endregion

    private var initialPosition = RecyclerView.NO_POSITION

    private val viewModel by activityViewModels<SearchResultViewModel>()
    private val drawerAndLogoViewModel: DrawerAndLogoViewModel by activityViewModels()

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
        toastHelper = ToastHelper.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        articleBottomActionBarNavigationHelper
            .setBottomNavigationFromContainer(viewBinding.navigationBottomLayout)

        if (resources.getBoolean(R.bool.isTablet)) {
            articleBottomActionBarNavigationHelper.fixToolbarForever()
        }

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

        // Wait for the loaded result being ready before jumping to the initial position
        viewLifecycleOwner.lifecycleScope.launch {
            val searchResults = viewModel.searchResults.filterNotNull().first()
            if (initialPosition < searchResults.loadedResults) {
                searchResultPagerAdapter?.updateSearchResults(searchResults)
                log.verbose("setting currentItem to initialPosition $initialPosition")
                webViewPager.setCurrentItem(initialPosition, false)
                viewModel.setCurrentPosition(initialPosition)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.searchResults
                        .filterNotNull()
                        .collect {
                            searchResultPagerAdapter?.updateSearchResults(it)
                        }
                }

                launch {
                    viewModel.connectionError.filterNotNull().collect {
                        toastHelper.showNoConnectionToast()
                        viewModel.connectionErrorWasHandled()
                    }
                }
            }
        }

        updateHeader()

        // Adjust padding when we have cutout display
        lifecycleScope.launch {
            val extraPadding = generalDataStore.displayCutoutExtraPadding.get()
            if (extraPadding > 0 && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                viewBinding.collapsingToolbarLayout.setPadding(0, extraPadding, 0, 0)
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

            updateHeader()

            viewModel.setCurrentPosition(position)
            viewModel.tryLoadMore(position)

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

            // ensure the app bar of the webView is shown when article changes
            expandAppBarIfCollapsed()
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

            R.id.bottom_navigation_action_size ->
                TextSettingsBottomSheetFragment.newInstance(hideMultiColumnModeSwitch = true)
                    .show(childFragmentManager, TextSettingsBottomSheetFragment.TAG)
        }
    }

    private fun toggleBookmark(articleFileName: String, date: Date?) {
        applicationScope.launch {
            val articleStub = articleRepository.getStub(articleFileName)

            when {
                articleStub != null -> {
                    val isBookmarked = bookmarkRepository.toggleBookmarkAsync(articleStub).await()
                    if (isBookmarked) {
                        toastHelper.showToast(R.string.toast_article_bookmarked)
                    } else {
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
            val issuePublication = IssuePublication(BuildConfig.DISPLAYED_FEED, simpleDateFormat.format(datePublished))
            if (!contentService.isPresent(issuePublication)) {
                contentService.downloadMetadata(issuePublication, maxRetries = 5)
            }
            val article = requireNotNull(articleRepository.get(articleFileName))
            contentService.downloadToCache(article)
            bookmarkRepository.addBookmark(article)
        } catch (e: Exception) {
            log.warn("Error while trying to download a full article because of a bookmark request", e)
            Sentry.captureException(e)
            ToastHelper.getInstance(requireActivity().applicationContext)
                .showToast(R.string.toast_problem_bookmarking_article, long = true)
        }
    }

    private fun share() {
        getCurrentSearchHit()?.let { hit ->
            hit.onlineLink?.let {
                tracker.trackShareSearchHitEvent(it)
                shareArticle(it, hit.title)
            } ?: showSharingNotPossibleDialog()
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
        return viewModel.getSearchHit(getCurrentPagerPosition())
    }

    private fun reloadAfterCssChange() {
        // draw every view again
        searchResultPagerAdapter?.notifyDataSetChanged()
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

    private fun updateHeader() {
        val currentPosition = getCurrentPagerPosition()
        val currentResults = viewModel.searchResults.value
        val currentHit = viewModel.getSearchHit(currentPosition)

        if (currentHit != null) {
            setHeader(currentHit, currentPosition, currentResults?.totalResults ?: 0)
        }
    }

    private fun setHeader(searchHit: SearchHit, currentPosition: Int, totalResults: Int) {
        val publishedDateString = if (BuildConfig.IS_LMD) {
            DateHelper.stringToLocalizedMonthAndYearString(searchHit.date)
        } else {
            DateHelper.stringToMediumLocalizedString(searchHit.date)
        }

        viewBinding.headerCustom.apply {
            indexIndicator.text = getString(
                R.string.fragment_header_custom_index_indicator,
                currentPosition + 1,
                totalResults
            )
            sectionTitle.text = searchHit.sectionTitle ?: ""
            publishedDate.text =
                getString(R.string.fragment_header_custom_published_date, publishedDateString)
        }
    }

    /**
     * Check if appBarLayout is fully expanded and if not then expand it and show the logo.
     */
    private fun expandAppBarIfCollapsed() {
        val appBarFullyExpanded =
            viewBinding.appBarLayout.height - viewBinding.appBarLayout.bottom == 0

        if (!appBarFullyExpanded) {
            viewBinding.appBarLayout.setExpanded(true, false)
            drawerAndLogoViewModel.showLogo()
        }
    }

}