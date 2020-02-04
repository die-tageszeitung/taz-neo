package de.taz.app.android.ui.settings.support

import android.view.MenuItem
import de.taz.app.android.base.BaseContract

interface ErrorReportContract {

    interface View : BaseContract.View {
    }

    interface DataController : BaseContract.DataController {
    }

    interface Presenter : BaseContract.Presenter {

        fun onBottomNavigationItemClicked(menuItem: MenuItem)

    }
}
