package de.taz.app.android.ui.search

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.dto.SearchHitDto
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivitySearchBinding
import de.taz.app.android.util.Log
import kotlinx.coroutines.launch


const val DEFAULT_SEARCH_RESULTS_TO_FETCH = 20

class SearchActivity :
    ViewBindingActivity<ActivitySearchBinding>() {

    private val searchResultItemsList = mutableListOf<SearchHitDto>()
    private lateinit var apiService: ApiService
    private lateinit var searchResultListAdapter: SearchResultListAdapter
    private val log by Log
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiService = ApiService.getInstance(this)

        viewBinding.apply {
            searchCancelButton.setOnClickListener {
                searchInput.editText?.text?.clear()
                clearRecyclerView()
                showSearchDescription()
                searchInput.clearFocus()
                searchButtonLoadMore.visibility = View.GONE
                expandableAdvancedSearch.visibility = View.GONE
            }
            searchText.setOnEditorActionListener { _, actionId, _ ->
                // search button in keyboard layout clicked:
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    searchLoadingScreen.visibility = View.VISIBLE
                    hideKeyboard()
                    clearRecyclerView()
                    lifecycleScope.launch {
                        val result = apiService.search(
                            searchText = searchInput.editText?.text.toString()
                        )
                        hideSearchDescription()
                        result?.searchHitList?.let { hits ->
                            searchResultItemsList.addAll(hits)
                            initRecyclerView()
                        }
                    }
                    return@setOnEditorActionListener true
                }
                searchInput.hint = ""
                false
            }
            searchText.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    searchInput.hint = ""
                } else {
                    searchInput.hint = getString(R.string.search_input_hint)
                }
            }
            searchButtonLoadMore.setOnClickListener {
                lifecycleScope.launch {
                    val offset = searchResultItemsList.size + DEFAULT_SEARCH_RESULTS_TO_FETCH
                    val result = apiService.search(
                        searchText = searchInput.editText?.text.toString(),
                        rowCnt = DEFAULT_SEARCH_RESULTS_TO_FETCH,
                        offset = offset
                    )
                    result?.searchHitList?.let { hits ->
                        log.verbose("search result: ${result.searchHitList}")
                        searchResultItemsList.addAll(hits)
                        searchResultListAdapter.notifyItemRangeChanged(
                            offset,
                            DEFAULT_SEARCH_RESULTS_TO_FETCH
                        )
                    }
                    searchButtonLoadMore.visibility = View.GONE
                }
            }
            searchFilterButton.setOnClickListener {
                if (expandableAdvancedSearch.visibility == View.VISIBLE) {
                    searchDescription.visibility = View.VISIBLE
                    searchDescriptionIcon.visibility = View.VISIBLE
                    expandableAdvancedSearch.visibility = View.GONE
                    advancedSearchTitle.visibility = View.GONE
                    searchFilterButton.setImageResource(R.drawable.ic_filter)
                } else {
                    searchDescription.visibility = View.GONE
                    searchDescriptionIcon.visibility = View.GONE
                    expandableAdvancedSearch.visibility = View.VISIBLE
                    advancedSearchTitle.visibility = View.VISIBLE
                    searchFilterButton.setImageResource(R.drawable.ic_filter_filled)
                }
            }
            advancedSearchTimeslot.setOnClickListener {
                showTimeslotDialog()
            }
            advancedSearchPublishedIn.setOnClickListener {
                showPublishedInDialog()
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
                        if (llm.findLastVisibleItemPosition() == searchResultItemsList.size - 1
                            && searchResultList.visibility == View.VISIBLE
                        ) {
                            searchButtonLoadMore.visibility = View.VISIBLE
                        } else {
                            searchButtonLoadMore.visibility = View.GONE
                        }
                        super.onScrolled(recyclerView, dx, dy)
                    }
                })
            }
            searchLoadingScreen.visibility = View.GONE
        }
    }

    private fun clearRecyclerView() {
        searchResultItemsList.clear()
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
        }
        hideKeyboard()
    }

    private fun hideKeyboard() {
        this.currentFocus?.let { view ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun showTimeslotDialog() {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_advanced_search_timeslot, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.search_advanced_apply_filter) { _, _ ->
                val radioGroup =
                    dialogView.findViewById<RadioGroup>(R.id.search_radio_group_timeslot)
                log.debug("radioGroup: $radioGroup")
                if (radioGroup != null && radioGroup.checkedRadioButtonId != -1) {
                    val radioButton: View = radioGroup.findViewById(radioGroup.checkedRadioButtonId)
                    val radioId: Int = radioGroup.indexOfChild(radioButton)
                    val btn = radioGroup.getChildAt(radioId)
                    val chosenTimeslot = (btn as RadioButton).text.toString()
                    log.debug("chosen timeslot: $chosenTimeslot")
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
                log.debug("radioGroup: $radioGroup")
                if (radioGroup != null && radioGroup.checkedRadioButtonId != -1) {
                    val radioButton: View = radioGroup.findViewById(radioGroup.checkedRadioButtonId)
                    val radioId: Int = radioGroup.indexOfChild(radioButton)
                    val btn = radioGroup.getChildAt(radioId)
                    val chosenPublishedIn = (btn as RadioButton).text.toString()
                    log.debug("chosen published in: $chosenPublishedIn")
                    viewBinding.advancedSearchPublishedIn.text = chosenPublishedIn
                }
            }
            .setNegativeButton(R.string.cancel_button) { dialog, _ ->
                (dialog as AlertDialog).hide()
            }
            .create()
        dialog.show()

    }

    override fun onDestroy() {
        viewBinding.searchResultList.adapter = null
        super.onDestroy()
    }
}