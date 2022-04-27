package de.taz.app.android.ui.home.page

import androidx.lifecycle.lifecycleScope
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.ui.bottomSheet.issue.IssueBottomSheetFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class HomeMomentViewActionListener(
    private val issueFeedFragment: IssueFeedFragment<*>
) : CoverViewActionListener {
    override fun onImageClicked(coverPublication: AbstractCoverPublication) {
        val issuePublication = when(coverPublication) {
            is MomentPublication -> IssuePublication(
                coverPublication.feedName,
                coverPublication.date
            )
            is FrontpagePublication -> IssuePublicationWithPages(
                coverPublication.feedName,
                coverPublication.date
            )
            else -> throw IllegalArgumentException("Did not expect a ${coverPublication::class.simpleName}")
        }
        issueFeedFragment.onItemSelected(issuePublication)
    }

    override fun onLongClicked(coverPublication: AbstractCoverPublication) {
        issueFeedFragment.lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                issueFeedFragment.showBottomSheet(
                    IssueBottomSheetFragment.create(
                        IssuePublication(coverPublication)
                    )
                )
            }
        }
    }

}