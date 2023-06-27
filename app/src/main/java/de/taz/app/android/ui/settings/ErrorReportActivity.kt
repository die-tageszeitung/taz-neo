package de.taz.app.android.ui.settings

import android.os.Bundle
import de.taz.app.android.audioPlayer.AudioPlayerService
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivityErrorReportBinding
import de.taz.app.android.getTazApplication
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setBottomNavigationBackActivity
import de.taz.app.android.ui.navigation.setupBottomNavigation

class ErrorReportActivity : ViewBindingActivity<ActivityErrorReportBinding>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The AudioPlayer shall stop when the users wants to report an error
        AudioPlayerService.getInstance(applicationContext).apply {
            dismissPlayer()
        }
    }

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