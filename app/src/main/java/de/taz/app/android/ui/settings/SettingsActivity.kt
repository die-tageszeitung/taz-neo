package de.taz.app.android.ui.settings

import android.content.Intent
import de.taz.app.android.R
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivitySettingsBinding
import de.taz.app.android.ui.ExperimentalSearchActivity
import de.taz.app.android.ui.bookmarks.BookmarkListActivity
import de.taz.app.android.ui.main.MainActivity
import kotlinx.android.synthetic.main.activity_settings.*

class SettingsActivity: ViewBindingActivity<ActivitySettingsBinding>() {

    override fun onResume() {
        super.onResume()
        navigation_bottom.menu.findItem(R.id.bottom_navigation_action_settings)?.isChecked = true
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
                    true
                }
                else -> false
            }
        }
    }
}