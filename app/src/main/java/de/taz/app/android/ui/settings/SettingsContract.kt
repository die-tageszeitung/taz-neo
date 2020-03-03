package de.taz.app.android.ui.settings

import android.content.Context
import android.view.MenuItem
import androidx.lifecycle.LifecycleOwner
import de.taz.app.android.base.BaseContract

interface SettingsContract {

    interface View : BaseContract.View {
        fun showStoredIssueNumber(number: String)

        fun showNightMode(nightMode: Boolean)

        fun showTextSize(textSize: Int)

        fun showLogoutButton()

        fun showManageAccountButton()
    }

    interface DataController : BaseContract.DataController {

        fun observeNightMode(lifecycleOwner: LifecycleOwner, observationCallback: (Boolean) -> Unit)

        fun observeStoredIssueNumber(lifecycleOwner: LifecycleOwner, observationCallback: (String) -> Unit)

        fun observeTextSize(lifecycleOwner: LifecycleOwner, observationCallback: (String) -> Unit)

        fun setStoredIssueNumber(number: Int)

        fun setTextSizePercent(percent: String)

        fun getTextSizePercent(): String

        fun setNightMode(activated: Boolean)

        fun initializeSettings(applicationContext: Context)
    }

    interface Presenter : BaseContract.Presenter {

        fun decreaseTextSize()
        fun increaseTextSize()
        fun resetTextSize()

        fun enableNightMode()
        fun disableNightMode()

        fun setStoredIssueNumber(number: Int)

        fun onBottomNavigationItemClicked(menuItem: MenuItem)

        fun reportBug()

        fun logout()
    }
}
