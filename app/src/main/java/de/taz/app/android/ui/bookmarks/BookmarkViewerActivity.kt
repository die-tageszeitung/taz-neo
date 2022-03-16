package de.taz.app.android.ui.bookmarks

import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import de.taz.app.android.ui.TazViewerActivity
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setupBottomNavigation
import de.taz.app.android.ui.webview.pager.BookmarkPagerFragment
import de.taz.app.android.ui.webview.pager.BookmarkPagerViewModel
import kotlinx.android.synthetic.main.fragment_webview_pager.*
import kotlin.reflect.KClass

class BookmarkViewerActivity : TazViewerActivity() {
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
        setupBottomNavigation(
            navigation_bottom_webview_pager,
            BottomNavigationItem.ChildOf(BottomNavigationItem.Bookmark)
        )
    }
}