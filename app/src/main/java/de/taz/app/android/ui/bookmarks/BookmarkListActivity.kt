package de.taz.app.android.ui.bookmarks

import android.content.Intent
import de.taz.app.android.R
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivityBookmarksBinding
import de.taz.app.android.ui.ExperimentalSearchActivity
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.settings.SettingsActivity
import kotlinx.android.synthetic.main.activity_main.*

class BookmarkListActivity: ViewBindingActivity<ActivityBookmarksBinding>() {

    override fun onResume() {
        super.onResume()
        navigation_bottom.menu.findItem(R.id.bottom_navigation_action_bookmark)?.isChecked = true
        navigation_bottom.setOnItemSelectedListener { menuItem ->
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