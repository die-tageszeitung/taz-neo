package de.taz.app.android.ui.search

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.core.view.size
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.dto.SearchFilter
import de.taz.app.android.api.dto.SearchHitDto
import de.taz.app.android.api.dto.Sorting
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivitySearchBinding
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.launch
import java.util.*

class SearchActivity :
    ViewBindingActivity<ActivitySearchBinding>() {

    private val searchResultItemsList = mutableListOf<SearchHitDto>()
    private var lastVisiblePosition = 0

    private lateinit var apiService: ApiService
    private lateinit var searchResultListAdapter: SearchResultListAdapter
    private lateinit var toastHelper: ToastHelper
    private val log by Log

    private val viewModel by viewModels<SearchResultPagerViewModel>()

    // region Activity functions
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiService = ApiService.getInstance(this)
        toastHelper = ToastHelper.getInstance(applicationContext)

        viewBinding.apply {
            searchCancelButton.setOnClickListener {
                clearSearchList()
                clearAdvancedSettings()
            }
            searchText.setOnEditorActionListener { _, actionId, _ ->
                // search button in keyboard layout clicked:
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    advancedSearch(
                        searchText = searchInput.editText?.text.toString(),
                        title = searchTitle.editText?.text.toString(),
                        author = searchAuthor.editText?.text.toString(),
                        pubDateFrom = viewModel.pubDateFrom.value,
                        pubDateUntil = viewModel.pubDateUntil.value,
                        searchFilter = viewModel.searchFilter.value ?: SearchFilter.all,
                        sorting = viewModel.sorting.value ?: Sorting.relevance
                    )
                    return@setOnEditorActionListener true
                }
                false
            }
            searchText.doOnTextChanged { _, _, _, _ ->
                searchInput.error = null
            }
            expandAdvancedSearchButton.setOnClickListener {
                toggleAdvancedSearchLayout(expandableAdvancedSearch.visibility == View.VISIBLE)
            }
            advancedSearchTimeslot.setOnClickListener {
                showSearchTimeDialog()
            }
            advancedSearchPublishedIn.setOnClickListener {
                showPublishedInDialog()
            }
            advancedSearchSortBy.setOnClickListener {
                showSortByDialog()
            }
            advancedSearchStartSearch.setOnClickListener {
                advancedSearch(
                    searchText = searchInput.editText?.text.toString(),
                    title = searchTitle.editText?.text.toString(),
                    author = searchAuthor.editText?.text.toString(),
                    pubDateFrom = viewModel.pubDateFrom.value,
                    pubDateUntil = viewModel.pubDateUntil.value,
                    searchFilter = viewModel.searchFilter.value ?: SearchFilter.all,
                    sorting = viewModel.sorting.value ?: Sorting.relevance
                )
                return@setOnClickListener
            }

            navigationBottom.apply {
                itemIconTintList = null

                // hack to not auto select first item
                menu.getItem(0).isCheckable = false

                setOnItemSelectedListener {
                    if (it.itemId == R.id.bottom_navigation_action_home) {
                        this@SearchActivity.finish()
                        true
                    } else {
                        false
                    }
                }
            }
            viewModel.chosenTimeSlot.observeDistinct(this@SearchActivity) {
                mapTimeSlot(it)
            }
            viewModel.chosenPublishedIn.observeDistinct(this@SearchActivity) {
                viewModel.searchFilter.postValue(mapSearchFilter(it))
            }
            viewModel.chosenSortBy.observeDistinct(this@SearchActivity) {
                viewModel.sorting.postValue(mapSortingFilter(it))
            }
            viewModel.pubDateFrom.observeDistinct(this@SearchActivity) { from ->
                if (from != null) {
                    if (viewModel.pubDateUntil.value != null) {
                        updateCustomTimeSlot(from, viewModel.pubDateUntil.value)
                    }
                }
            }
            viewModel.pubDateUntil.observeDistinct(this@SearchActivity) { until ->
                if (until != null) {
                    if (viewModel.pubDateFrom.value != null) {
                        updateCustomTimeSlot(viewModel.pubDateFrom.value, until)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        viewBinding.searchResultList.adapter = null
        super.onDestroy()
    }

    // endregion
    // region main functions
    private fun advancedSearch(
        searchText: String,
        title: String? = null,
        author: String? = null,
        rowCnt: Int = DEFAULT_SEARCH_RESULTS_TO_FETCH,
        offset: Int = 0,
        pubDateFrom: String? = null,
        pubDateUntil: String? = null,
        searchFilter: SearchFilter = SearchFilter.all,
        sorting: Sorting = Sorting.relevance,
        showLoadingScreen: Boolean = true
    ) {
        if (searchText.isBlank() && title.isNullOrBlank() && author.isNullOrBlank()) {
            showNoInputError()
            return
        }
        hideKeyboard()
        hideSearchDescription()
        if (showLoadingScreen) showLoadingScreen()

        // region throw debug logs
        log.debug("advanced SEARCH with following parameters:")
        log.debug("searchText: $searchText")
        log.debug("title: $title")
        log.debug("author: $author")
        log.debug("offset: $offset")
        log.debug("rowCnt: $rowCnt")
        log.debug("pubDateFrom: $pubDateFrom")
        log.debug("pubDateUntil: $pubDateUntil")
        log.debug("searchFilter: $searchFilter")
        log.debug("sorting: $sorting")
        // endregion

        // save search parameter to viewModel:
        viewModel.searchText.postValue(searchText)
        viewModel.searchTitle.postValue(title)
        viewModel.searchAuthor.postValue(author)

        if (offset == 0) {
            clearRecyclerView()
        }

        toggleAdvancedSearchIndicator(
            title != "" || author != "" || pubDateUntil != null || searchFilter != SearchFilter.all || sorting != Sorting.relevance
        )

        lifecycleScope.launch {
            if (apiService.checkForConnectivity()) {
                val result = apiService.search(
                    searchText = searchText,
                    title = title,
                    author = author,
                    rowCnt = rowCnt,
                    offset = offset,
                    pubDateFrom = pubDateFrom,
                    pubDateUntil = pubDateUntil,
                    filter = searchFilter,
                    sorting = sorting
                )
                result?.let {
                    viewModel.total = it.total
                    it.searchHitList?.let { hits ->
                        searchResultItemsList.addAll(hits)
                        viewModel.searchResultsLiveData.postValue(searchResultItemsList)
                        if (offset > 0) {
                            searchResultListAdapter.notifyItemRangeChanged(
                                offset,
                                rowCnt
                            )
                        } else {
                            initRecyclerView()
                        }
                    }
                    showAmountFound(searchResultItemsList.size, it.total)
                }
                viewModel.currentlyLoadingMore.postValue(false)
            }else {
                toastHelper.showNoConnectionToast()
                clearSearchList()
                clearAdvancedSettings()
                return@launch
            }
        }
    }

    private fun initRecyclerView() {
        viewBinding.apply {
            searchResultListAdapter = SearchResultListAdapter(searchResultItemsList)
            val llm = LinearLayoutManager(applicationContext)
            searchResultList.apply {
                layoutManager = llm
                adapter = searchResultListAdapter
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        if (lastVisiblePosition !=llm.findLastVisibleItemPosition()) {
                            lastVisiblePosition = llm.findLastVisibleItemPosition()
                            if (viewModel.checkIfLoadMore(lastVisible = lastVisiblePosition)) {
                                viewModel.currentlyLoadingMore.postValue(true)
                                loadMore()
                            }
                        }
                        super.onScrolled(recyclerView, dx, dy)
                    }
                })
            }
        }
    }

    fun loadMore() {
        val offset = searchResultItemsList.size
            advancedSearch(
                searchText = viewModel.searchText.value.toString(),
                title = viewModel.searchTitle.value.toString(),
                author = viewModel.searchAuthor.value.toString(),
                offset = offset,
                pubDateFrom = viewModel.pubDateFrom.value,
                pubDateUntil = viewModel.pubDateUntil.value,
                searchFilter = viewModel.searchFilter.value ?: SearchFilter.all,
                sorting = viewModel.sorting.value ?: Sorting.relevance,
                showLoadingScreen = false
            )
    }

    // endregion
    // region UI update functions
    private fun showLoadingScreen() {
        viewBinding.searchLoadingScreen.visibility = View.VISIBLE
    }

    private fun hideLoadingScreen() {
        viewBinding.searchLoadingScreen.visibility = View.GONE
    }

    private fun clearRecyclerView() {
        searchResultItemsList.clear()
        viewModel.searchResultsLiveData.postValue(emptyList())
    }

    private fun showSearchDescription() {
        viewBinding.apply {
            searchDescription.visibility = View.VISIBLE
            searchDescriptionIcon.visibility = View.VISIBLE
            searchResultList.visibility = View.GONE
        }
        hideKeyboard()
    }

    private fun hideSearchDescription() {
        viewBinding.apply {
            searchDescription.visibility = View.GONE
            searchDescriptionIcon.visibility = View.GONE
            searchResultList.visibility = View.VISIBLE
            expandableAdvancedSearch.visibility = View.GONE
            advancedSearchTitle.visibility = View.GONE
        }
        hideKeyboard()
    }

    private fun hideKeyboard() {
        this.currentFocus?.let { view ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun clearSearchList() {
        viewModel.total = 0
        viewBinding.apply {
            searchInput.editText?.text?.clear()
            clearRecyclerView()
            showSearchDescription()
            hideLoadingScreen()
            searchInput.clearFocus()
            expandableAdvancedSearch.visibility = View.GONE
            advancedSearchTitle.visibility = View.GONE
            searchResultAmount.text = ""
            searchAuthor.editText?.text?.clear()
            searchTitle.editText?.text?.clear()
            expandAdvancedSearchButton.setImageResource(R.drawable.ic_filter)
        }
    }

    private fun showAmountFound(index: Int, amount: Int) {
        viewBinding.apply {
            hideLoadingScreen()
            if (amount == 0) {
                searchResultAmount.text = getString(R.string.search_result_amount_none_found)
            } else {
                searchResultAmount.text = getString(
                    R.string.search_result_amount_found,
                    index,
                    amount
                )
            }
        }
    }

    private fun toggleAdvancedSearchLayout(isVisible: Boolean) {
        viewBinding.apply {
            if (isVisible) {
                if (searchResultList.size == 0) {
                    searchDescription.visibility = View.VISIBLE
                    searchDescriptionIcon.visibility = View.VISIBLE
                }
                expandableAdvancedSearch.visibility = View.GONE
                advancedSearchTitle.visibility = View.GONE
                expandAdvancedSearchButton.setImageResource(R.drawable.ic_filter)
            } else {
                searchDescription.visibility = View.GONE
                searchDescriptionIcon.visibility = View.GONE
                expandableAdvancedSearch.visibility = View.VISIBLE
                advancedSearchTitle.visibility = View.VISIBLE
                expandAdvancedSearchButton.setImageResource(R.drawable.ic_filter_active)
                searchInput.error = null
            }
        }
    }

    private fun toggleAdvancedSearchIndicator(advancedActive: Boolean) {
        viewBinding.apply {
            if (advancedActive) {
                expandAdvancedSearchButton.setImageResource(R.drawable.ic_filter_active)
            } else {
                expandAdvancedSearchButton.setImageResource(R.drawable.ic_filter)
            }
        }
    }

    private fun clearAdvancedSettings() {
        viewBinding.apply {
            searchTitle.editText?.text?.clear()
            searchAuthor.editText?.text?.clear()
            viewModel.pubDateFrom.postValue(null)
            viewModel.pubDateUntil.postValue(null)
            viewModel.chosenTimeSlot.value = getString(R.string.search_advanced_radio_timeslot_any)
            advancedSearchPublishedIn.text =
                getString(R.string.search_advanced_radio_published_in_any)
            viewModel.searchFilter.postValue(SearchFilter.all)
            advancedSearchSortBy.text = getString(R.string.search_advanced_radio_sort_by_relevance)
            viewModel.sorting.postValue(Sorting.relevance)
        }
    }

    private fun updateCustomTimeSlot(pubDateFrom: String?, pubDateUntil: String?) {
        val formattedFromDate = pubDateFrom?.let {
            DateHelper.stringToMediumLocalizedString(it)
        }
        val formattedUntilDate = pubDateUntil?.let {
            DateHelper.stringToMediumLocalizedString(it)
        }
        viewBinding.advancedSearchTimeslot.text = getString(
            R.string.search_advanced_timeslot_from_until,
            formattedFromDate,
            formattedUntilDate
        )
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

    // endregion
    // region helper functions
    private fun mapSearchFilter(publishedIn: String): SearchFilter {
        viewBinding.advancedSearchPublishedIn.text = publishedIn
        return when (publishedIn) {
            getString(R.string.search_advanced_radio_published_in_taz) -> SearchFilter.taz
            getString(R.string.search_advanced_radio_published_in_lmd) -> SearchFilter.LMd
            getString(R.string.search_advanced_radio_published_in_kontext) -> SearchFilter.Kontext
            getString(R.string.search_advanced_radio_published_in_weekend) -> SearchFilter.weekend
            else -> SearchFilter.all
        }
    }

    private fun mapSortingFilter(sortBy: String): Sorting {
        viewBinding.advancedSearchSortBy.text = sortBy
        return when (sortBy) {
            getString(R.string.search_advanced_radio_sort_by_appearance) -> Sorting.appearance
            getString(R.string.search_advanced_radio_sort_by_actuality) -> Sorting.actuality
            else -> Sorting.relevance
        }
    }

    private fun mapTimeSlot(timeSlotString: String?) {
        val todayString = simpleDateFormat.format(Date())
        viewBinding.advancedSearchTimeslot.text =
            timeSlotString ?: getString(R.string.search_advanced_radio_timeslot_any)
        when (timeSlotString) {
            getString(R.string.search_advanced_radio_timeslot_last_day) -> {
                val yesterdayString = simpleDateFormat.format(DateHelper.yesterday())
                viewModel.pubDateUntil.postValue(todayString)
                viewModel.pubDateFrom.postValue(yesterdayString)
            }
            getString(R.string.search_advanced_radio_timeslot_last_week) -> {
                val lastWeekString = simpleDateFormat.format(DateHelper.lastWeek())
                viewModel.pubDateUntil.postValue(todayString)
                viewModel.pubDateFrom.postValue(lastWeekString)
            }
            getString(R.string.search_advanced_radio_timeslot_last_month) -> {
                val lastMonthString = simpleDateFormat.format(DateHelper.lastMonth())
                viewModel.pubDateUntil.postValue(todayString)
                viewModel.pubDateFrom.postValue(lastMonthString)
            }
            getString(R.string.search_advanced_radio_timeslot_last_year) -> {
                val lastYearString = simpleDateFormat.format(DateHelper.lastYear())
                viewModel.pubDateUntil.postValue(todayString)
                viewModel.pubDateFrom.postValue(lastYearString)
            }
            else -> {
                viewModel.pubDateUntil.postValue(null)
                viewModel.pubDateFrom.postValue(null)
            }
        }

    }
    // endregion
}