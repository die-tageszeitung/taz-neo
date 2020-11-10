package de.taz.app.android.ui.home.page

import androidx.lifecycle.lifecycleScope
import de.taz.app.android.data.DataService
import de.taz.app.android.ui.bottomSheet.issue.IssueBottomSheetFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class HomeMomentViewActionListener(
    private val homePageFragment: HomePageFragment,
    private val dataService: DataService,
) : MomentViewActionListener {

    override fun onImageClicked(momentViewData: MomentViewData) {
        homePageFragment.onItemSelected(momentViewData.issueStub)
    }

    override fun onLongClicked(momentViewData: MomentViewData) {
        homePageFragment.lifecycleScope.launch(Dispatchers.IO) {
            val issueStubRefresh =
                dataService.getIssueStub(issueKey = momentViewData.issueStub.issueKey)
            issueStubRefresh?.let {
                withContext(Dispatchers.Main) {
                    homePageFragment.showBottomSheet(
                        IssueBottomSheetFragment.create(
                            homePageFragment.getMainView()!!,
                            it
                        )
                    )
                }
            }
        }
    }
}