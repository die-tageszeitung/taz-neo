package de.taz.app.android.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.variables.SearchVariables
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivityExperimentalSearchBinding
import de.taz.app.android.ui.bookmarks.BookmarkListActivity
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.settings.SettingsActivity
import kotlinx.android.synthetic.main.activity_settings.*
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
        viewBinding.navigationBottom.menu.findItem(R.id.bottom_navigation_action_search)?.isChecked = true
        viewBinding.navigationBottom.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.bottom_navigation_action_home -> {
                    Intent(
                        this,
                        MainActivity::class.java
                    ).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        .apply { startActivity(this) }
                    true
                }
                R.id.bottom_navigation_action_bookmark -> {
                    Intent(
                        this,
                        BookmarkListActivity::class.java
                    ).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        .apply { startActivity(this) }
                    true
                }
                R.id.bottom_navigation_action_search -> {
                    true
                }
                R.id.bottom_navigation_action_settings -> {
                    Intent(
                        this,
                        SettingsActivity::class.java
                    ).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        .apply { startActivity(this) }
                    true
                }
                else -> false
            }
        }
    }
}