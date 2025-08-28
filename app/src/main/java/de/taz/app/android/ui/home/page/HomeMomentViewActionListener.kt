package de.taz.app.android.ui.home.page

import androidx.lifecycle.lifecycleScope
import de.taz.app.android.persistence.repository.AbstractCoverPublication
import de.taz.app.android.persistence.repository.FrontpagePublication
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.persistence.repository.IssuePublicationWithPages
import de.taz.app.android.persistence.repository.MomentPublication
import de.taz.app.android.ui.bottomSheet.issue.IssueBottomSheetFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

open class HomeMomentViewActionListener(
    private val issueFeedFragment: IssueFeedFragment<*>
) : CoverViewActionListener {
    override fun onImageClicked(coverPublication: AbstractCoverPublication) {
        val issuePublication = when (coverPublication) {
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
        issueFeedFragment.lifecycleScope.launch(Dispatchers.Main) {
            IssueBottomSheetFragment
                .newInstance(IssuePublication(coverPublication))
                .show(issueFeedFragment.childFragmentManager, IssueBottomSheetFragment.TAG)
        }
    }

}