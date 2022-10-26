package de.taz.app.android.ui.bookmarks

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomnavigation.BottomNavigationView
import de.taz.app.android.R
import de.taz.app.android.ui.TazViewerFragment
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setBottomNavigationBackActivity
import de.taz.app.android.ui.navigation.setupBottomNavigation
import de.taz.app.android.ui.webview.pager.BookmarkPagerFragment
import de.taz.app.android.ui.webview.pager.BookmarkPagerViewModel
import kotlin.reflect.KClass

class BookmarkViewerActivity : AppCompatActivity() {
    companion object {
        const val KEY_SHOWN_ARTICLE = "KEY_SHOWN_ARTICLE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction().add(
            android.R.id.content,
            BookmarkViewerFragment.newInstance(
                intent.getStringExtra(KEY_SHOWN_ARTICLE),
            )
        ).commit()
    }

    override fun onResume() {
        super.onResume()
        setBottomNavigationBackActivity(this, BottomNavigationItem.Bookmark)
        setupBottomNavigation(
            findViewById<BottomNavigationView>(R.id.navigation_bottom_webview_pager),
            BottomNavigationItem.ChildOf(BottomNavigationItem.Bookmark)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        setBottomNavigationBackActivity(null, BottomNavigationItem.Bookmark)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        setBottomNavigationBackActivity(null, BottomNavigationItem.Bookmark)
    }
}

class BookmarkViewerFragment : TazViewerFragment() {
    companion object {
        const val KEY_SHOWN_ARTICLE = "KEY_SHOWN_ARTICLE"
        fun newInstance(showArticle: String?) = BookmarkViewerFragment().apply {
            arguments = bundleOf(KEY_SHOWN_ARTICLE to showArticle)
        }
    }

    private val bookmarkPagerViewModel: BookmarkPagerViewModel by activityViewModels()

    override val fragmentClass: KClass<out Fragment> = BookmarkPagerFragment::class

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            arguments?.getString(KEY_SHOWN_ARTICLE)?.let {
                bookmarkPagerViewModel.articleFileNameLiveData.postValue(it)
            }
        }
    }
}