package de.taz.app.android.ui.bookmarks

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import de.taz.app.android.R
import de.taz.app.android.ui.ExperimentalSearchActivity
import de.taz.app.android.ui.TazViewerActivity
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.settings.SettingsActivity
import de.taz.app.android.ui.webview.pager.BookmarkPagerFragment
import de.taz.app.android.ui.webview.pager.BookmarkPagerViewModel
import kotlinx.android.synthetic.main.fragment_webview_pager.*
import kotlin.reflect.KClass

class BookmarkViewerActivity: TazViewerActivity() {
    companion object {
        const val KEY_SHOWN_ARTICLE = "KEY_SHOWN_ARTICLE"
    }

    private val bookmarkPagerViewModel: BookmarkPagerViewModel by viewModels()

    override val fragmentClass: KClass<out Fragment> = BookmarkPagerFragment::class

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val initialArticle = intent.getStringExtra(KEY_SHOWN_ARTICLE)
            initialArticle?.let {
                bookmarkPagerViewModel.articleFileNameLiveData.postValue(it)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        navigation_bottom_webview_pager.menu.findItem(R.id.bottom_navigation_action_bookmark)?.isChecked = true
        navigation_bottom_webview_pager.setOnItemSelectedListener { menuItem ->
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
                    Intent(
                        this,
                        ExperimentalSearchActivity::class.java
                    ).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        .apply { startActivity(this) }
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