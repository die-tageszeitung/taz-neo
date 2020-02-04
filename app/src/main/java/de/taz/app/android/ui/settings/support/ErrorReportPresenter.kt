package de.taz.app.android.ui.settings.support

import android.view.MenuItem
import de.taz.app.android.R
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.util.Log

class ErrorReportPresenter:
    BasePresenter<ErrorReportFragment, ErrorReportDataController>(ErrorReportDataController::class.java),
    ErrorReportContract.Presenter {

    private val log by Log

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        if (menuItem.itemId == R.id.bottom_navigation_action_home) {
            log.debug("Show home clicked")
            getView()?.getMainView()?.showHome()
        }

    }

}