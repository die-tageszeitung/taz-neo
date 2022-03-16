package de.taz.app.android.ui.settings

import android.content.Intent
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivitySettingsBinding
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setupBottomNavigation

class SettingsActivity : ViewBindingActivity<ActivitySettingsBinding>() {

    override fun onResume() {
        super.onResume()
        setupBottomNavigation(
            viewBinding.navigationBottom,
            BottomNavigationItem.Settings
        )
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