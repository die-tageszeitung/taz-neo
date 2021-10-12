package de.taz.app.android.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.base.NightModeActivity
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.variables.SearchVariables
import kotlinx.coroutines.launch

class ExperimentalSearchActivity : NightModeActivity(R.layout.activity_experimental_search) {

    private lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiService = ApiService.getInstance(this)
        val searchInput = findViewById<EditText>(R.id.searchInput)
        val listView = findViewById<ListView>(R.id.searchList)
        val listAdapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_list_item_1
        )
        listView.adapter = listAdapter
        findViewById<Button>(R.id.searchButton)
            .apply {
                setOnClickListener {
                    lifecycleScope.launch {
                        val result = apiService.search(
                            SearchVariables(
                                text = searchInput?.text?.toString()
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
}