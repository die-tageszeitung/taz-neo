package de.taz.app.android.ui.navigation

import android.app.Activity
import android.content.Intent
import androidx.annotation.IdRes
import com.google.android.material.bottomnavigation.BottomNavigationView
import de.taz.app.android.R
import de.taz.app.android.ui.bookmarks.BookmarkListActivity
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.playlist.PlaylistActivity
import de.taz.app.android.ui.search.SearchActivity
import de.taz.app.android.ui.settings.SettingsActivity
import kotlin.reflect.KClass

sealed class BottomNavigationItem(@IdRes val itemId: Int) {
    data object Home : BottomNavigationItem(R.id.bottom_navigation_action_home)
    data object Bookmark : BottomNavigationItem(R.id.bottom_navigation_action_bookmark)
    data object Playlist : BottomNavigationItem(R.id.bottom_navigation_action_playlist)
    data object Search : BottomNavigationItem(R.id.bottom_navigation_action_search)
    data object Settings : BottomNavigationItem(R.id.bottom_navigation_action_settings)
    class ChildOf(val parent: BottomNavigationItem) : BottomNavigationItem(0)
}
private var bottomGroup: BottomNavigationItem? = null
fun Activity.setupBottomNavigation(
    navigationBottom: BottomNavigationView,
    currentItem: BottomNavigationItem
) {
    val menuId = when (currentItem) {
        is BottomNavigationItem.ChildOf -> {
            bottomGroup = currentItem.parent
            currentItem.parent.itemId
        }
        else ->  {
            if (bottomGroup == currentItem) bottomGroup = null
            currentItem.itemId
        }
    }

    navigationBottom.menu.findItem(menuId)?.isChecked = true
    navigationBottom.setOnItemSelectedListener { menuItem ->
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home -> {
                if (currentItem is BottomNavigationItem.Home || (currentItem is BottomNavigationItem.ChildOf && bottomGroup == BottomNavigationItem.Home)) {
                    // FIXME: This is bad style. this helper should not have to know about the MainActivities behavior.
                    //        But for the first iteration it seems okay to me
                    (this as? MainActivity)?.showHome()
                } else {
                    navigateToMain()
                }
                true
            }

            R.id.bottom_navigation_action_bookmark -> {
                navigateToBookmarks()
                true
            }

            R.id.bottom_navigation_action_playlist -> {
                navigateToPlaylist()
                // return false so the icon gets not marked as "active"
                false
            }

            R.id.bottom_navigation_action_search -> {
                navigateToSearch()
                true
            }

            R.id.bottom_navigation_action_settings -> {
                navigateToSettings()
                true
            }
            else -> false
        }
    }
}

// Always navigate back to Home (MainActivity)
// To be used from the root bottom navigation activities.
fun Activity.bottomNavigationBack() {
    navigateToMain()
}

private fun Activity.navigateToMain() = startActivity(this, MainActivity::class)
private fun Activity.navigateToBookmarks() = startActivity(this, BookmarkListActivity::class)
private fun Activity.navigateToPlaylist() = startActivity(this, PlaylistActivity::class)
private fun Activity.navigateToSearch() = startActivity(this, SearchActivity::class)
private fun Activity.navigateToSettings() = startActivity(this, SettingsActivity::class)


private fun startActivity(parentActivity: Activity, activityClass: KClass<out Activity>) {
    Intent(parentActivity, activityClass.java)
        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        .apply(parentActivity::startActivity)
}