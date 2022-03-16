package de.taz.app.android.ui.settings

import android.content.Intent
import de.taz.app.android.R
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivitySettingsBinding
import de.taz.app.android.ui.ExperimentalSearchActivity
import de.taz.app.android.ui.bookmarks.BookmarkListActivity
import de.taz.app.android.ui.main.MainActivity

class SettingsActivity: ViewBindingActivity<ActivitySettingsBinding>() {

    override fun onResume() {
        super.onResume()
        viewBinding.navigationBottom.menu.findItem(R.id.bottom_navigation_action_settings)?.isChecked = true
        viewBinding.navigationBottom.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.bottom_navigation_action_home -> {
                    Intent(
                        this,
                        MainActivity::class.java
                    ).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        .apply { startActivity(this) }
                    true
                }
                R.id.bottom_navigation_action_bookmark -> {
                    Intent(
                        this,
                        BookmarkListActivity::class.java
                    ).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        .apply { startActivity(this) }
                    true
                }
                R.id.bottom_navigation_action_search -> {
                    Intent(
                        this,
                        ExperimentalSearchActivity::class.java
                    ).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
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

    override fun onBackPressed() {
        Intent(
            this,
            MainActivity::class.java
        ).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .apply { startActivity(this) }
        finish()
    }
}