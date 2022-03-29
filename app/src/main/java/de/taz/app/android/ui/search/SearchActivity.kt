package de.taz.app.android.ui.search

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.size
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.dto.SearchFilter
import de.taz.app.android.api.dto.SearchHitDto
import de.taz.app.android.api.dto.Sorting
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivitySearchBinding
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.launch
import java.util.*


const val DEFAULT_SEARCH_RESULTS_TO_FETCH = 20
const val BEGIN_INFINITE_SCROLL_BEFORE_LAST = 5

class SearchActivity :
    ViewBindingActivity<ActivitySearchBinding>() {

    private val searchResultItemsList = mutableListOf<SearchHitDto>()
    private var searchFilter = SearchFilter.all
    private var sorting = Sorting.relevance
    private var pubDateFrom: String? = null
    private var pubDateUntil: String? = null
    private var total: Int = 0
    private var currentlyLoadingMore = false

    private lateinit var apiService: ApiService
    private lateinit var searchResultListAdapter: SearchResultListAdapter
    private val log by Log

    private val viewModel by viewModels<SearchResultPagerViewModel>()

    // region Activity functions
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiService = ApiService.getInstance(this)

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
                        pubDateFrom = pubDateFrom,
                        pubDateUntil = pubDateUntil,
                        searchFilter = searchFilter,
                        sorting = sorting
                    )
                    return@setOnEditorActionListener true
                }
                false
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
                    pubDateFrom = pubDateFrom,
                    pubDateUntil = pubDateUntil,
                    searchFilter = searchFilter,
                    sorting = sorting
                )
            }
            navigationBottom.setOnItemSelectedListener {
                if (it.itemId == R.id.bottom_navigation_action_home) {
                    this@SearchActivity.finish()
                    true
                } else {
                    false
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

        if (offset == 0) {
            clearRecyclerView()
        }

        toggleAdvancedSearchIndicator(
            title != "" || author != "" || pubDateUntil != null || searchFilter != SearchFilter.all || sorting != Sorting.relevance
        )

        lifecycleScope.launch {
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
                total = it.total
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
                showAmountFound(searchResultItemsList.size, total)
            }
            currentlyLoadingMore = false
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
                        if (checkIfLoadMore(lastVisible = llm.findLastVisibleItemPosition())) {
                            currentlyLoadingMore = true
                            loadMore()
                        }
                        super.onScrolled(recyclerView, dx, dy)
                    }
                })
            }
        }
    }

    private fun loadMore() {
        val offset = searchResultItemsList.size + DEFAULT_SEARCH_RESULTS_TO_FETCH
        viewBinding.apply {
            advancedSearch(
                searchText = searchInput.editText?.text.toString(),
                title = searchTitle.editText?.text.toString(),
                author = searchAuthor.editText?.text.toString(),
                offset = offset,
                pubDateFrom = pubDateFrom,
                pubDateUntil = pubDateUntil,
                searchFilter = searchFilter,
                sorting = sorting,
                showLoadingScreen = false
            )
        }
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
        viewBinding.apply {
            searchInput.editText?.text?.clear()
            clearRecyclerView()
            showSearchDescription()
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
            advancedSearchTimeslot.text = getString(R.string.search_advanced_radio_timeslot_any)
            pubDateFrom = null
            pubDateUntil = null
            advancedSearchPublishedIn.text =
                getString(R.string.search_advanced_radio_published_in_any)
            searchFilter = SearchFilter.all
            advancedSearchSortBy.text = getString(R.string.search_advanced_radio_sort_by_relevance)
            sorting = Sorting.relevance
        }
    }

    // endregion
    // region dialog functions
    private fun showSearchTimeDialog() {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_advanced_search_timeslot, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.search_advanced_apply_filter) { _, _ ->
                val radioGroup =
                    dialogView.findViewById<RadioGroup>(R.id.search_radio_group_timeslot)
                if (radioGroup != null && radioGroup.checkedRadioButtonId != -1) {
                    val radioButton: View = radioGroup.findViewById(radioGroup.checkedRadioButtonId)
                    val radioId: Int = radioGroup.indexOfChild(radioButton)
                    val btn = radioGroup.getChildAt(radioId)
                    val chosenTimeslot = (btn as RadioButton).text.toString()
                    mapTimeSlot(chosenTimeslot)
                    viewBinding.advancedSearchTimeslot.text = chosenTimeslot
                }
            }
            .setNegativeButton(R.string.cancel_button) { dialog, _ ->
                (dialog as AlertDialog).hide()
            }
            .create()
        dialog.show()
    }

    private fun showPublishedInDialog() {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_advanced_search_published_in, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.search_advanced_apply_filter) { _, _ ->
                val radioGroup =
                    dialogView.findViewById<RadioGroup>(R.id.search_radio_group_published_in)
                if (radioGroup != null && radioGroup.checkedRadioButtonId != -1) {
                    val radioButton: View = radioGroup.findViewById(radioGroup.checkedRadioButtonId)
                    val radioId: Int = radioGroup.indexOfChild(radioButton)
                    val btn = radioGroup.getChildAt(radioId)
                    val chosenPublishedIn = (btn as RadioButton).text.toString()
                    searchFilter = mapSearchFilter(chosenPublishedIn)
                    viewBinding.advancedSearchPublishedIn.text = chosenPublishedIn
                }
            }
            .setNegativeButton(R.string.cancel_button) { dialog, _ ->
                (dialog as AlertDialog).hide()
            }
            .create()
        dialog.show()
    }

    private fun showSortByDialog() {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_advanced_search_sort_by, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.search_advanced_apply_filter) { _, _ ->
                val radioGroup =
                    dialogView.findViewById<RadioGroup>(R.id.search_radio_group_sort_by)
                if (radioGroup != null && radioGroup.checkedRadioButtonId != -1) {
                    val radioButton: View = radioGroup.findViewById(radioGroup.checkedRadioButtonId)
                    val radioId: Int = radioGroup.indexOfChild(radioButton)
                    val btn = radioGroup.getChildAt(radioId)
                    val chosenSortBy = (btn as RadioButton).text.toString()
                    sorting = mapSortingFilter(chosenSortBy)
                    viewBinding.advancedSearchSortBy.text = chosenSortBy
                }
            }
            .setNegativeButton(R.string.cancel_button) { dialog, _ ->
                (dialog as AlertDialog).hide()
            }
            .create()
        dialog.show()
    }

    // endregion
    // region helper functions
    private fun mapSearchFilter(publishedIn: String): SearchFilter {
        return when (publishedIn) {
            getString(R.string.search_advanced_radio_published_in_taz) -> SearchFilter.taz
            getString(R.string.search_advanced_radio_published_in_lmd) -> SearchFilter.LMd
            getString(R.string.search_advanced_radio_published_in_kontext) -> SearchFilter.Kontext
            getString(R.string.search_advanced_radio_published_in_weekend) -> SearchFilter.weekend
            else -> SearchFilter.all
        }
    }

    private fun mapSortingFilter(sortBy: String): Sorting {
        return when (sortBy) {
            getString(R.string.search_advanced_radio_sort_by_appearance) -> Sorting.appearance
            getString(R.string.search_advanced_radio_sort_by_actuality) -> Sorting.actuality
            else -> Sorting.relevance
        }
    }

    private fun mapTimeSlot(timeSlotString: String) {
        val todayString = simpleDateFormat.format(Date())
        when (timeSlotString) {
            getString(R.string.search_advanced_radio_timeslot_last_day) -> {
                val yesterdayString = simpleDateFormat.format(DateHelper.yesterday())
                pubDateUntil = todayString
                pubDateFrom = yesterdayString
            }
            getString(R.string.search_advanced_radio_timeslot_last_week) -> {
                val lastWeekString = simpleDateFormat.format(DateHelper.lastWeek())
                pubDateUntil = todayString
                pubDateFrom = lastWeekString
            }
            getString(R.string.search_advanced_radio_timeslot_last_month) -> {
                val lastMonthString = simpleDateFormat.format(DateHelper.lastMonth())
                pubDateUntil = todayString
                pubDateFrom = lastMonthString
            }
            getString(R.string.search_advanced_radio_timeslot_last_year) -> {
                val lastYearString = simpleDateFormat.format(DateHelper.lastYear())
                pubDateUntil = todayString
                pubDateFrom = lastYearString
            }
            else -> {
                pubDateUntil = null
                pubDateFrom = null
            }
        }
    }

    private fun checkIfLoadMore(lastVisible: Int): Boolean {
        return lastVisible >= searchResultItemsList.size - BEGIN_INFINITE_SCROLL_BEFORE_LAST && !currentlyLoadingMore
    }
    // endregion
}