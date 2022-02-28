package de.taz.app.android.ui

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.api.ApiService
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivityExperimentalSearchBinding
import kotlinx.coroutines.launch

class ExperimentalSearchActivity :
    ViewBindingActivity<ActivityExperimentalSearchBinding>() {

    private lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiService = ApiService.getInstance(this)
        val listAdapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_list_item_1
        )
        viewBinding.apply {
            searchList.adapter = listAdapter
            cancelButton.setOnClickListener {
                searchInput.editText?.text?.clear()
            }
            searchText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    lifecycleScope.launch {
                        val result = apiService.search(
                            searchText = searchInput.editText?.text.toString()
                        )
                        listAdapter.clear()
                        result?.searchHitList?.let { hits ->
                            listAdapter.addAll(hits.map { it.title })
                        }
                    }
                    return@setOnEditorActionListener true
                }
                false
            }
        }
    }
}