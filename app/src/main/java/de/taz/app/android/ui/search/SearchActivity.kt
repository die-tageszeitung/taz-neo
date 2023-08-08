package de.taz.app.android.ui.search

import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_HTML_FILE_SEARCH_HELP
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.Sorting
import de.taz.app.android.api.variables.SearchFilter
import de.taz.app.android.audioPlayer.AudioPlayerViewController
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.content.ContentService
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.ActivitySearchBinding
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.BookmarkRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.SuccessfulLoginAction
import de.taz.app.android.ui.WebViewActivity
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.bottomNavigationBack
import de.taz.app.android.ui.navigation.setupBottomNavigation
import de.taz.app.android.util.Log
import de.taz.app.android.util.hideSoftInputKeyboard
import io.sentry.Sentry
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.*


private const val DEFAULT_SEARCH_RESULTS_TO_FETCH = 20
private const val SEARCH_RESULT_PAGER_BACKSTACK_NAME = "search_result_pager"

class SearchActivity :
    ViewBindingActivity<ActivitySearchBinding>(),
    SuccessfulLoginAction {

    private lateinit var apiService: ApiService
    private lateinit var articleRepository: ArticleRepository
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var contentService: ContentService
    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var toastHelper: ToastHelper
    private lateinit var tracker: Tracker
    private val log by Log

    private val viewModel by viewModels<SearchResultViewModel>()

    @Suppress("unused")
    private val audioPlayerViewController = AudioPlayerViewController(this)

    // region Activity functions
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiService = ApiService.getInstance(this)
        articleRepository = ArticleRepository.getInstance(applicationContext)
        bookmarkRepository = BookmarkRepository.getInstance(applicationContext)
        contentService = ContentService.getInstance(applicationContext)
        generalDataStore = GeneralDataStore.getInstance(applicationContext)
        toastHelper = ToastHelper.getInstance(applicationContext)
        tracker = Tracker.getInstance(applicationContext)

        viewBinding.apply {
            searchCancelButton.setOnClickListener {
                viewModel.clearSearch()
            }
            searchText.apply {
                setOnEditorActionListener { _, actionId, _ ->
                    // search button in keyboard layout clicked:
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        startSearch()
                        return@setOnEditorActionListener true
                    }
                    false
                }

                setOnKeyListener { _, _, keyEvent ->
                    if (keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                        startSearch()
                    }
                    false
                }

                doOnTextChanged { _, _, _, _ ->
                    searchInput.error = null
                }

                setOnFocusChangeListener { _, isFocused ->
                    if (isFocused) {
                        searchCancelButton.visibility = View.VISIBLE
                    }
                }

                doAfterTextChanged {
                    val searchText: String = it?.toString() ?: ""
                    viewModel.setSelectedSearchText(searchText)
                }
            }

            searchHelp.setOnClickListener {
                showHelp()
            }
            expandAdvancedSearchButton.setOnClickListener {
                viewModel.toggleAdvancedSearchOpen()
            }
            advancedSearchTimeslot.setOnClickListener {
                showSearchTimeDialog()
            }
            if (BuildConfig.IS_LMD) {
                // Restrict the publishedIn parameter to LMd by hiding the selection action ...
                advancedSearchPublishedInWrapper.visibility = View.GONE
            } else {
                advancedSearchPublishedIn.setOnClickListener {
                    showPublishedInDialog()
                }
            }
            advancedSearchSortBy.setOnClickListener {
                showSortByDialog()
            }

            searchTitle.editText?.doAfterTextChanged {
                val searchTitle: String? = it?.toString()
                viewModel.setSelectedAdvancedOptions(
                    viewModel.selectedSearchOptions.value.advancedOptions.copy(title = searchTitle)
                )
            }

            searchAuthor.editText?.doAfterTextChanged {
                val searchAuthor: String? = it?.toString()
                viewModel.setSelectedAdvancedOptions(
                    viewModel.selectedSearchOptions.value.advancedOptions.copy(author = searchAuthor)
                )
            }

            searchAuthorInput.setOnKeyListener { _, _, keyEvent ->
                if (keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                    hideSoftInputKeyboard()
                }
                false
            }

            advancedSearchStartSearch.setOnClickListener {
                startSearch()
            }

            // Adjust extra padding when we have cutout display
            lifecycleScope.launch {
                val extraPadding = generalDataStore.displayCutoutExtraPadding.get()
                if (extraPadding > 0 && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    root.setPadding(0, extraPadding, 0, 0)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.selectedSearchOptions.collect {
                        updateSearchViews(it)
                    }
                }

                launch {
                    viewModel.isAdvancedSearchOpen.collect {
                        setAdvancedSearchLayoutVisibility(it)
                    }
                }

                launch {
                    viewModel.isAdvancedSearchHighlighted.collect {
                        setAdvancedSearchIndicator(it)
                    }
                }

                launch {
                    viewModel.searchUiState.collect {
                        updateUiState(it)
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
    }


    override fun onResume() {
        super.onResume()
        tracker.trackSearchScreen()
        setupBottomNavigation(
            viewBinding.navigationBottom,
            BottomNavigationItem.Search
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (audioPlayerViewController.onBackPressed()) {
            return
        }

        val searchResultPagerFragment =
            supportFragmentManager.fragments.firstOrNull { it is SearchResultPagerFragment } as? SearchResultPagerFragment

        if (searchResultPagerFragment?.isAdded == true) {
            super.onBackPressed()
        } else {
            bottomNavigationBack()
        }
    }

    override fun onDestroy() {
        viewBinding.searchResultList.adapter = null
        super.onDestroy()
    }

    // endregion

    private fun startSearch() {
        val searchOptions = viewModel.selectedSearchOptions.value
        if (!searchOptions.isValid()) {
            showNoInputError()
        } else {
            viewModel.closeAdvancedSearch()
            viewModel.startSearch(searchOptions)
        }
    }


    private fun updateSearchResults(searchResults: SearchResults) {
        val adapter = viewBinding.searchResultList.adapter as? SearchResultListAdapter
        if (adapter == null) {
            initRecyclerView(searchResults)
        } else {
            adapter.updateSearchResults(searchResults)
        }
    }


    private fun initRecyclerView(searchResults: SearchResults) {
        viewBinding.apply {
            val searchResultListAdapter =
                SearchResultListAdapter(
                    searchResults,
                    ::toggleBookmark,
                    bookmarkRepository::createBookmarkStateFlow,
                    ::onSearchResultClick,
                )
            val llm = LinearLayoutManager(applicationContext)
            searchResultList.apply {
                layoutManager = llm
                adapter = searchResultListAdapter

                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    private var lastVisiblePosition = 0

                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        val currentLastVisiblePosition = llm.findLastVisibleItemPosition()
                        if (lastVisiblePosition < currentLastVisiblePosition) {
                            lastVisiblePosition = currentLastVisiblePosition
                            viewModel.tryLoadMore(currentLastVisiblePosition)
                        }
                    }
                })
            }
        }
    }

    // region UI update functions
    fun updateRecyclerView(position: Int) {
        viewBinding.searchResultList.scrollToPosition(position)
    }

    private fun showLoadingScreen() {
        viewBinding.searchLoadingScreen.visibility = View.VISIBLE
    }

    private fun hideLoadingScreen() {
        viewBinding.searchLoadingScreen.visibility = View.GONE
    }

    private fun clearRecyclerView() {
        viewBinding.searchResultList.adapter = null
    }

    private fun showSearchDescription() {
        viewBinding.apply {
            searchDescription.visibility = View.VISIBLE
            searchDescriptionIcon.visibility = View.VISIBLE
            searchResultList.visibility = View.GONE
        }
    }

    private fun hideSearchDescription() {
        viewBinding.apply {
            searchDescription.visibility = View.GONE
            searchDescriptionIcon.visibility = View.GONE
            searchResultList.visibility = View.VISIBLE
        }
    }

    private fun updateUiState(state: SearchUiState) {
        when (state) {
            SearchUiState.Init -> {
                hideSoftInputKeyboard()
                hideLoadingScreen()
                clearRecyclerView()
                viewBinding.apply {
                    searchInput.apply {
                        editText?.text?.clear()
                        clearFocus()
                    }
                    searchResultAmount.isVisible = false
                    searchCancelButton.isVisible = false
                }
                showSearchDescription()
            }

            SearchUiState.Loading -> {
                hideSoftInputKeyboard()
                hideSearchDescription()
                clearRecyclerView()
                viewBinding.apply {
                    searchInput.clearFocus()
                    searchResultAmount.isVisible = false
                }
                showLoadingScreen()
            }

            SearchUiState.NoResults -> {
                hideSoftInputKeyboard()
                hideSearchDescription()
                hideLoadingScreen()
                clearRecyclerView()
                viewBinding.apply {
                    searchInput.clearFocus()
                    searchResultAmount.apply {
                        isVisible = true
                        setText(R.string.search_result_amount_none_found)
                    }
                }
            }

            is SearchUiState.Results -> {
                hideSoftInputKeyboard()
                hideSearchDescription()
                hideLoadingScreen()
                viewBinding.apply {
                    searchInput.clearFocus()
                    searchResultAmount.apply {
                        isVisible = true
                        text = getString(
                            R.string.search_result_amount_found,
                            state.searchResults.loadedResults,
                            state.searchResults.totalResults
                        )
                    }
                }
                updateSearchResults(state.searchResults)
            }
        }
    }

    private fun updateSearchViews(options: SearchOptions) {
        viewBinding.searchInput.editText?.apply {
            if (text.toString() != options.searchText) {
                setText(options.searchText)
            }
        }
        updateAdvancedSearchViews(options.advancedOptions)
    }

    private fun updateAdvancedSearchViews(options: AdvancedSearchOptions) {
        viewBinding.searchTitle.editText?.apply {
            val searchTitle = options.title ?: ""
            if (text.toString() != searchTitle) {
                setText(searchTitle)
            }
        }
        viewBinding.searchAuthor.editText?.apply {
            val searchAuthor = options.author ?: ""
            if (text.toString() != searchAuthor) {
                setText(searchAuthor)
            }
        }

        updateTimeslotView(options.publicationDateFilter)

        val filterStringId = when (options.searchFilter) {
            SearchFilter.all -> R.string.search_advanced_radio_published_in_any
            SearchFilter.taz -> R.string.search_advanced_radio_published_in_taz
            SearchFilter.LMd -> R.string.search_advanced_radio_published_in_lmd
            SearchFilter.Kontext -> R.string.search_advanced_radio_published_in_kontext
            SearchFilter.weekend -> R.string.search_advanced_radio_published_in_weekend
        }
        viewBinding.advancedSearchPublishedIn.setText(filterStringId)

        val sortStringResId = when (options.sorting) {
            Sorting.appearance -> R.string.search_advanced_radio_sort_by_appearance
            Sorting.actuality -> R.string.search_advanced_radio_sort_by_actuality
            Sorting.relevance -> R.string.search_advanced_radio_sort_by_relevance
        }
        viewBinding.advancedSearchSortBy.setText(sortStringResId)
    }

    private fun updateTimeslotView(publicationDateFilter: PublicationDateFilter) {
        val today = Calendar.getInstance().time
        val publicationDateString = when (publicationDateFilter) {
            PublicationDateFilter.Any -> getString(R.string.search_advanced_radio_timeslot_any)
            is PublicationDateFilter.Custom -> {
                val from = publicationDateFilter.from ?: viewModel.minPublicationDate
                val until = publicationDateFilter.until ?: today
                getTimeslotString(from, until)
            }

            PublicationDateFilter.Last31Days -> getTimeslotString(DateHelper.lastMonth(), today)
            PublicationDateFilter.Last365Days -> getTimeslotString(DateHelper.lastYear(), today)
            PublicationDateFilter.Last7Days -> getTimeslotString(DateHelper.lastWeek(), today)
            PublicationDateFilter.LastDay -> getTimeslotString(DateHelper.yesterday(), today)
        }
        viewBinding.advancedSearchTimeslot.text = publicationDateString
    }

    private fun getTimeslotString(from: Date, until: Date): String {
        val fromString = DateHelper.dateToMediumLocalizedString(from)
        val untilString = DateHelper.dateToMediumLocalizedString(until)
        return getString(
            R.string.search_advanced_timeslot_from_until,
            fromString,
            untilString
        )
    }

    private fun setAdvancedSearchLayoutVisibility(isVisible: Boolean) {
        viewBinding.apply {
            if (!isVisible) {
                val showSearchDescription = viewModel.searchUiState.value is SearchUiState.Init
                searchDescription.isVisible = showSearchDescription
                searchDescriptionIcon.isVisible = showSearchDescription

                searchResultAmount.isVisible = true
                expandableAdvancedSearch.isVisible = false
                advancedSearchTitle.isVisible = false
            } else {
                searchDescription.isVisible = false
                searchDescriptionIcon.isVisible = false
                searchResultAmount.isVisible = false
                expandableAdvancedSearch.isVisible = true
                advancedSearchTitle.isVisible = true
                searchInput.error = null
            }
        }
    }

    private fun setAdvancedSearchIndicator(isActive: Boolean) {
        viewBinding.apply {
            if (isActive) {
                expandAdvancedSearchButton.setImageResource(R.drawable.ic_filter_active)
            } else {
                expandAdvancedSearchButton.setImageResource(R.drawable.ic_filter)
            }
        }
    }

    private fun showNoInputError() {
        viewBinding.apply {
            searchInput.error = getString(R.string.search_input_error)
        }
    }
    // endregion

    // region dialog functions
    private fun showSearchTimeDialog() {
        AdvancedTimeslotDialogFragment().show(
            supportFragmentManager,
            "advancedTimeslotDialog"
        )
    }

    private fun showPublishedInDialog() {
        AdvancedPublishedInDialog().show(
            supportFragmentManager,
            "advancedPublishedInDialog"
        )
    }

    private fun showSortByDialog() {
        AdvancedSortByDialog().show(
            supportFragmentManager,
            "advancedSortByDialog"
        )
    }

    private fun showHelp() {
        val intent = WebViewActivity.newIntent(this, WEBVIEW_HTML_FILE_SEARCH_HELP)
        startActivity(intent)
    }
    // endregion

    override fun onLogInSuccessful(articleName: String) {
        // Close the result pager
        supportFragmentManager.popBackStack(
            SEARCH_RESULT_PAGER_BACKSTACK_NAME,
            POP_BACK_STACK_INCLUSIVE
        )
        // Re-start the search
        viewModel.restartSearch()
    }

    // region helper functions
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

                date != null -> {
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
     * Bookmarks articles "outside" an issue (eg in the search result list)
     * then downloads the corresponding metadata
     * downloads the article and
     * finally bookmarks the article.
     */
    private suspend fun downloadArticleAndSetBookmark(
        articleFileName: String,
        datePublished: Date
    ) {
        try {
            val issueMetadata =
                apiService.getIssueByFeedAndDate(BuildConfig.DISPLAYED_FEED, datePublished)
            contentService.downloadMetadata(issueMetadata, maxRetries = 5)
            val article = requireNotNull(articleRepository.get(articleFileName))
            contentService.downloadToCache(article)
            bookmarkRepository.addBookmark(article)
        } catch (e: Exception) {
            log.warn("Error while trying to download a full article because of a bookmark request", e)
            Sentry.captureException(e)
            toastHelper.showToast(R.string.toast_problem_bookmarking_article, long = true)
        }
    }
    // endregion

    private fun onSearchResultClick(position: Int) {
        val fragment = SearchResultPagerFragment.newInstance(position)
        supportFragmentManager.commit {
            add(android.R.id.content, fragment)
            addToBackStack(SEARCH_RESULT_PAGER_BACKSTACK_NAME)
        }
    }

    /**
     * Try to bring the audio player to the front.
     */
    // FIXME (johannes): Consider to add the [SearchResultPagerFragment] to a container defined by the app, or using windows for the overlay.
    fun bringAudioPlayerOverlayToFront() {
        audioPlayerViewController.bringOverlayToFront()
    }
}