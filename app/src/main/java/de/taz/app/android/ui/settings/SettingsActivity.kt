package de.taz.app.android.ui.settings

import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivitySettingsBinding
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.bottomNavigationBack
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
        bottomNavigationBack()
    }
}