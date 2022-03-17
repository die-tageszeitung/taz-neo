package de.taz.app.android.ui.settings

import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivityErrorReportBinding
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setupBottomNavigation

class ErrorReportActivity : ViewBindingActivity<ActivityErrorReportBinding>() {

    override fun onResume() {
        super.onResume()
        setupBottomNavigation(
            viewBinding.navigationBottom,
            BottomNavigationItem.ChildOf(BottomNavigationItem.Settings)
        )
    }
}