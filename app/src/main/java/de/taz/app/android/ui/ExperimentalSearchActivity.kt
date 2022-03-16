package de.taz.app.android.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.variables.SearchVariables
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivityExperimentalSearchBinding
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setupBottomNavigation
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
            searchButton.setOnClickListener {
                lifecycleScope.launch {
                    val result = apiService.search(
                        SearchVariables(
                            text = searchInput.text?.toString()
                        )
                    )
                    listAdapter.clear()
                    result?.searchHitList?.let { hits ->
                        listAdapter.addAll(hits.map { it.title })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        setupBottomNavigation(
            viewBinding.navigationBottom,
            BottomNavigationItem.Search
        )
    }
}