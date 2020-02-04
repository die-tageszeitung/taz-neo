package de.taz.app.android.ui.settings.support

import android.view.MenuItem
import de.taz.app.android.base.BaseContract

interface ErrorReportContract {

    interface View : BaseContract.View {
        fun sendErrorReport(email: String?, message: String?, lastAction: String?, conditions: String?)
    }

    interface DataController : BaseContract.DataController {
    }

    interface Presenter : BaseContract.Presenter {

        fun onBottomNavigationItemClicked(menuItem: MenuItem)

    }
}
