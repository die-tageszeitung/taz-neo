package de.taz.app.android.ui.navigation

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.annotation.IdRes
import com.google.android.material.bottomnavigation.BottomNavigationView
import de.taz.app.android.R
import de.taz.app.android.ui.bookmarks.BookmarkListActivity
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.search.SearchActivity
import de.taz.app.android.ui.settings.SettingsActivity
import kotlin.reflect.KClass

sealed class BottomNavigationItem(@IdRes val itemId: Int) {
    object Home : BottomNavigationItem(R.id.bottom_navigation_action_home)
    object Bookmark : BottomNavigationItem(R.id.bottom_navigation_action_bookmark)
    object Search : BottomNavigationItem(R.id.bottom_navigation_action_search)
    object Settings : BottomNavigationItem(R.id.bottom_navigation_action_settings)
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
        Log.d("!!!", "currentItem: $currentItem with bottomGroup: $bottomGroup on back $backActivityClass")
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home -> {
                if (currentItem is BottomNavigationItem.Home) {
                    // FIXME: This is bad style. this helper should not have to know about the MainActivities behavior.
                    //        But for the first iteration it seems okay to me
                    (this as? MainActivity)?.showHome()
                } else {
                    if (currentItem is BottomNavigationItem.ChildOf && bottomGroup == BottomNavigationItem.Home) {
                        if (homeBackActivityClass == backActivityClass) {
                            backActivityClass = null
                        }
                        homeBackActivityClass = null
                    }
                    navigateToMain()
                }
                true
            }

            R.id.bottom_navigation_action_bookmark -> {
                if (currentItem is BottomNavigationItem.ChildOf && bottomGroup == BottomNavigationItem.Bookmark) {
                    if (bookmarkBackActivityClass == backActivityClass) {
                        backActivityClass = null
                    }
                    bookmarkBackActivityClass = null
                }
                if (currentItem !is BottomNavigationItem.Bookmark) {
                    navigateToBookmarks()
                }
                true
            }

            R.id.bottom_navigation_action_search -> {
                if (currentItem is BottomNavigationItem.ChildOf && bottomGroup == BottomNavigationItem.Search) {
                    if (searchBackActivityClass == backActivityClass) {
                        backActivityClass = null
                    }
                    searchBackActivityClass = null
                }
                if (currentItem !is BottomNavigationItem.Search) {
                    navigateToSearch()
                }
                true
            }

            R.id.bottom_navigation_action_settings -> {
                if (currentItem is BottomNavigationItem.ChildOf && bottomGroup == BottomNavigationItem.Settings) {
                    if (settingsBackActivityClass == backActivityClass) {
                        backActivityClass = null
                    }
                    settingsBackActivityClass = null
                }
                if (currentItem !is BottomNavigationItem.Settings) {
                    navigateToSettings()
                }
                true
            }
            else -> false
        }
    }
}

// DANGER: this is global state. this is dangerous! we should not do that!
// but in this case it is okay as even if the variable is reset to null due to the app being backgrounded
// and killed by android having the behavior is still acceptable.
private var backActivityClass: KClass<out Activity>? = null
private var homeBackActivityClass: KClass<out Activity>? = null
private var bookmarkBackActivityClass: KClass<out Activity>? = null
private var searchBackActivityClass: KClass<out Activity>? = null
private var settingsBackActivityClass: KClass<out Activity>? = null
fun setBottomNavigationBackActivity(activity: Activity?, bottomGroup: BottomNavigationItem? = null) {
    backActivityClass = activity?.let { it::class }
    Log.d("!!!", "setBottomNavigationBackActivity to $backActivityClass")
    when (bottomGroup) {
        BottomNavigationItem.Home -> homeBackActivityClass = backActivityClass
        BottomNavigationItem.Bookmark -> bookmarkBackActivityClass = backActivityClass
        BottomNavigationItem.Search -> searchBackActivityClass = backActivityClass
        BottomNavigationItem.Settings -> settingsBackActivityClass = backActivityClass
        else -> {}
    }
}

// Navigate back to Home (MainActivity)
// Or if an explicit activity is set via setBottomNavigationBackActivity navigate back to that one.
// To be used from the root bottom navigation activities.
fun Activity.bottomNavigationBack() {
    val currentBackActivityClass = backActivityClass
    Log.d("!!!", "bottomNavigationBack with backActivityClass: $currentBackActivityClass and bottomGroup $bottomGroup")
   /* if (currentBackActivityClass != null) {
        backActivityClass = null
        startActivity(this, currentBackActivityClass)
    } else {*/
        navigateToMain()
    //}
}

private fun Activity.navigateToMain() = startActivity(this, homeBackActivityClass ?: MainActivity::class)
private fun Activity.navigateToBookmarks() = startActivity(this, bookmarkBackActivityClass ?: BookmarkListActivity::class)
private fun Activity.navigateToSearch() = startActivity(this, searchBackActivityClass ?: SearchActivity::class)
private fun Activity.navigateToSettings() = startActivity(this, settingsBackActivityClass ?: SettingsActivity::class)


private fun startActivity(parentActivity: Activity, activityClass: KClass<out Activity>) {
    Intent(parentActivity, activityClass.java)
        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        .apply(parentActivity::startActivity)
}