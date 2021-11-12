package de.taz.app.android.ui.home.page

import androidx.lifecycle.lifecycleScope
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheState
import de.taz.app.android.ui.bottomSheet.issue.IssueBottomSheetFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class HomeMomentViewActionListener(
    private val issueFeedFragment: IssueFeedFragment,
    private val contentService: ContentService,
) : CoverViewActionListener {

    override fun onImageClicked(momentViewData: CoverViewData) {
        issueFeedFragment.onItemSelected(momentViewData.issueKey)
    }

    override fun onLongClicked(momentViewData: CoverViewData) {
        issueFeedFragment.lifecycleScope.launch(Dispatchers.IO) {
            val cacheState = contentService.getCacheState(momentViewData.issueKey).cacheState
            withContext(Dispatchers.Main) {
                issueFeedFragment.showBottomSheet(
                    IssueBottomSheetFragment.create(
                        momentViewData.issueKey,
                        cacheState == CacheState.PRESENT
                    )
                )
            }
        }
    }
}