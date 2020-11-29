package de.taz.app.android.ui.home.page.coverflow

import de.taz.app.android.data.DataService
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.ui.home.page.HomeMomentViewActionListener
import de.taz.app.android.ui.home.page.MomentViewData

open class CoverflowMomentActionListener(
    private val coverflowFragment: CoverflowFragment,
    dataService: DataService
) : HomeMomentViewActionListener(coverflowFragment, dataService) {
    override fun onDateClicked(momentViewData: MomentViewData) {
        coverflowFragment.openDatePicker(simpleDateFormat.parse(momentViewData.issueKey.date)!!)
    }
}