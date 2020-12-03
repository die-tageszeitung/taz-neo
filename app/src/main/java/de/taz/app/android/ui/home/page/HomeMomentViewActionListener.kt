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
        homePageFragment.onItemSelected(momentViewData.issueKey)
    }

    override fun onLongClicked(momentViewData: MomentViewData) {
        homePageFragment.lifecycleScope.launch(Dispatchers.IO) {
            val isDownloaded = dataService.isIssueDownloaded(momentViewData.issueKey)
            withContext(Dispatchers.Main) {
                homePageFragment.showBottomSheet(
                    IssueBottomSheetFragment.create(
                        momentViewData.issueKey,
                        isDownloaded
                    )
                )
            }
        }
    }
}