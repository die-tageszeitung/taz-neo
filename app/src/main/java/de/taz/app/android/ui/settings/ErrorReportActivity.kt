package de.taz.app.android.ui.settings

import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivityErrorReportBinding
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setBottomNavigationBackActivity
import de.taz.app.android.ui.navigation.setupBottomNavigation

class ErrorReportActivity : ViewBindingActivity<ActivityErrorReportBinding>() {

    override fun onResume() {
        super.onResume()
        setBottomNavigationBackActivity(this, BottomNavigationItem.Settings)
        setupBottomNavigation(
            viewBinding.navigationBottom,
            BottomNavigationItem.ChildOf(BottomNavigationItem.Settings)
        )
    }

    override fun onDestroy() {
        setBottomNavigationBackActivity(null, BottomNavigationItem.Settings)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        setBottomNavigationBackActivity(null, BottomNavigationItem.Settings)
        super.onBackPressed()
    }
}