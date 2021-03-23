package de.taz.app.android.ui.home.page

import androidx.lifecycle.lifecycleScope
import de.taz.app.android.data.DataService
import de.taz.app.android.ui.bottomSheet.issue.IssueBottomSheetFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class HomeMomentViewActionListener(
    private val issueFeedFragment: IssueFeedFragment,
    private val dataService: DataService,
) : CoverViewActionListener {

    override fun onImageClicked(momentViewData: CoverViewData) {
        issueFeedFragment.onItemSelected(momentViewData.issueKey)
    }

    override fun onLongClicked(momentViewData: CoverViewData) {
        issueFeedFragment.lifecycleScope.launch(Dispatchers.IO) {
            val isDownloaded = dataService.isIssueDownloaded(momentViewData.issueKey)
            withContext(Dispatchers.Main) {
                issueFeedFragment.showBottomSheet(
                    IssueBottomSheetFragment.create(
                        momentViewData.issueKey,
                        isDownloaded
                    )
                )
            }
        }
    }
}