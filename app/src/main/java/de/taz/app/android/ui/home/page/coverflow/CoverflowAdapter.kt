package de.taz.app.android.ui.home.page.coverflow

import androidx.annotation.LayoutRes
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.ui.home.page.HomePageAdapter

class CoverflowAdapter(
    private val fragment: CoverflowContract.View,
    @LayoutRes private val itemLayoutRes: Int,
    private val presenter: CoverflowContract.Presenter
) : HomePageAdapter(fragment, itemLayoutRes, presenter) {

    override fun setIssueStubs(issues: List<IssueStub>) {
        val skipToLast = visibleIssueStubList.isEmpty()
        super.setIssueStubs(issues)
        if(skipToLast) {
            fragment.skipToEnd()
        }
    }

    override fun setInactiveFeedNames(inactiveFeedNames: Set<String>) {
        val skipToLast = visibleIssueStubList.isEmpty()
        super.setInactiveFeedNames(inactiveFeedNames)
        if(skipToLast) {
            fragment.skipToEnd()
        }
    }
}