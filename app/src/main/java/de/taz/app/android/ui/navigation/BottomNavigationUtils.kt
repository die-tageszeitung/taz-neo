package de.taz.app.android.ui.navigation

import android.app.Activity
import android.content.Intent
import androidx.annotation.IdRes
import com.google.android.material.bottomnavigation.BottomNavigationView
import de.taz.app.android.R
import de.taz.app.android.ui.ExperimentalSearchActivity
import de.taz.app.android.ui.bookmarks.BookmarkListActivity
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.settings.SettingsActivity
import kotlin.reflect.KClass

sealed class BottomNavigationItem(@IdRes val itemId: Int) {
    object Home : BottomNavigationItem(R.id.bottom_navigation_action_home)
    object Bookmark : BottomNavigationItem(R.id.bottom_navigation_action_bookmark)
    object Search : BottomNavigationItem(R.id.bottom_navigation_action_search)
    object Settings : BottomNavigationItem(R.id.bottom_navigation_action_settings)
    class ChildOf(val parent: BottomNavigationItem) : BottomNavigationItem(0)
}

fun Activity.setupBottomNavigation(
    navigationBottom: BottomNavigationView,
    currentItem: BottomNavigationItem
) {
    val menuId = when (currentItem) {
        is BottomNavigationItem.ChildOf -> currentItem.parent.itemId
        else -> currentItem.itemId
    }
    navigationBottom.menu.findItem(menuId)?.isChecked = true

    navigationBottom.setOnItemSelectedListener { menuItem ->
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home -> {
                if (currentItem is BottomNavigationItem.Home) {
                    // FIXME: This is bad style. this helper should not have to know about the MainActivities behavior.
                    //        But for the first iteration it seems okay to me
                    (this as? MainActivity)?.showHome()
                } else {
                    navigateToMain()
                }
                true
            }

            R.id.bottom_navigation_action_bookmark -> {
                if (currentItem !is BottomNavigationItem.Bookmark) {
                    navigateToBookmarks()
                }
                true
            }

            R.id.bottom_navigation_action_search -> {
                if (currentItem !is BottomNavigationItem.Search) {
                    navigateToSearch()
                }
                true
            }

            R.id.bottom_navigation_action_settings -> {
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
fun setBottomNavigationBackActivity(activity: Activity?) {
    backActivityClass = activity?.let { it::class }
}

// Navigate back to Home (MainActivity)
// Or if an explicit activity is set via setBottomNavigationBackActivity navigate back to that one.
// To be used from the root bottom navigation activities.
fun Activity.bottomNavigationBack() {
    val currentBackActivityClass = backActivityClass
    if (currentBackActivityClass != null) {
        backActivityClass = null
        startActivity(this, currentBackActivityClass)
    } else {
        navigateToMain()
    }
}

private fun Activity.navigateToMain() = startActivity(this, MainActivity::class)
private fun Activity.navigateToBookmarks() = startActivity(this, BookmarkListActivity::class)
private fun Activity.navigateToSearch() = startActivity(this, ExperimentalSearchActivity::class)
private fun Activity.navigateToSettings() = startActivity(this, SettingsActivity::class)

private fun startActivity(parentActivity: Activity, activityClass: KClass<out Activity>) {
    Intent(parentActivity, activityClass.java)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .apply(parentActivity::startActivity)
}