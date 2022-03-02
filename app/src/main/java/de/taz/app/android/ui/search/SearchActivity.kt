package de.taz.app.android.ui.search

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.dto.SearchHitDto
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivitySearchBinding
import kotlinx.coroutines.launch

class SearchActivity :
    ViewBindingActivity<ActivitySearchBinding>() {

    private val searchResultItemsList = mutableListOf<SearchHitDto>()
    private lateinit var apiService: ApiService
    private lateinit var searchResultListAdapter: SearchResultListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiService = ApiService.getInstance(this)
        val listAdapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_list_item_1
        )

        viewBinding.apply {
            searchCancelButton.setOnClickListener {
                searchInput.editText?.text?.clear()
                listAdapter.clear()
                showSearchDescription()
            }
            searchText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    lifecycleScope.launch {
                        val result = apiService.search(
                            searchText = searchInput.editText?.text.toString()
                        )
                        hideSearchDescription()
                        result?.searchHitList?.let { hits ->
                            searchResultItemsList.addAll(hits)
                            initRecyclerView()
                           // listAdapter.addAll(hits.map { it.title })
                        }
                    }
                    return@setOnEditorActionListener true
                }
                false
            }
        }
    }

    private fun initRecyclerView() {
        viewBinding.apply {
            val recyclerView = searchResultList
            searchResultListAdapter = SearchResultListAdapter(searchResultItemsList)
            recyclerView.apply {
                setHasFixedSize(true) // TODO <- check if needed
                layoutManager = LinearLayoutManager(applicationContext)
                adapter = searchResultListAdapter
            }
        }
    }

    private fun showSearchDescription() {
        viewBinding.apply {
            searchDescription.visibility = View.VISIBLE
            searchDescriptionIcon.visibility = View.VISIBLE
        }
        hideKeyboard()
    }

    private fun hideSearchDescription() {
        viewBinding.apply {
            searchDescription.visibility = View.GONE
            searchDescriptionIcon.visibility = View.GONE
        }
        hideKeyboard()
    }

    private fun hideKeyboard() {
        this.currentFocus?.let { view ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}